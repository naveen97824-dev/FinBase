# Technology Stack — Final

**FinBase — lending management for private financiers**
Version 4.0 · supersedes v3

**What changed from v3:** backend language and runtime only — Java 21 + Spring Boot instead of Python 3.12 + FastAPI. The functional spec, the database schema, the web app, and the mobile app are unchanged. Database is still PostgreSQL; see §0 for why Mongo was considered and set aside.

**Team assumption:** 2 developers, working with AI assistance.
**Scope assumption:** v2 functional spec — single shared login per company, no roles, no approval workflows, no notifications, PDF download instead of SMS, no offline mode, no thermal printing.

---

## 0. Why Postgres, not Mongo

You asked about MongoDB. Short answer: stick with Postgres. Longer answer, based on the actual schema in `v2-02-database-schema.sql`:

- **27 tables, 52 foreign keys, 51 CHECK constraints.** Loans reference customers, repayments reference loans, ledger entries reference repayments, documents reference almost everything. That's a deeply relational model. In Mongo, all of that referential integrity moves into application code — more surface area for bugs in a system that handles other people's money.
- **Row Level Security is the whole tenant-isolation strategy.** Company A can never see Company B's loan book, enforced by Postgres RLS policies on every table. Mongo has no equivalent — every query, everywhere, forever, would need a hand-written `financier_id` filter. One missed filter is a cross-company data leak.
- **Money and ledger consistency.** Updating a loan balance, a ledger entry, and an overdue flag together wants a real multi-table transaction. Postgres gives you this natively; Mongo's multi-document transactions exist but are heavier and less idiomatic.
- **The reporting screens** (Current Dues, Pending Dues, aging buckets, dashboards) are join- and aggregate-heavy — exactly SQL's home turf.

Nothing here is impossible in Mongo, but you'd be rebuilding guardrails Postgres gives you for free, in a product where getting them wrong has real financial consequences. **PostgreSQL 16 stays.** The schema file itself (`v2-02-database-schema.sql`) needs no changes for this switch — it's already pure Postgres DDL, independent of backend language.

---

## 1. The stack at a glance

| Layer | Choice |
|---|---|
| **Backend** | Java 21 (LTS) + Spring Boot 3.3+ |
| **Database** | PostgreSQL 16 (Row Level Security for company isolation) |
| **Cache** | Redis 7 — OTP storage + distributed rate limiting only (no broker role — see §2.3) |
| **Scheduled / background jobs** | Spring `@Scheduled` + **ShedLock** (no Celery, no separate worker/beat processes) |
| **File storage** | AWS S3, ap-south-1 (Mumbai) |
| **PDF generation** | openhtmltopdf, **server-side only**, Thymeleaf templates |
| **Web app** | React 19 + TypeScript + Vite |
| **Mobile app** | React Native via **Expo managed workflow** — Android only for v1 |
| **Shared code** | TypeScript package, types generated from the OpenAPI spec |
| **SMS** | MSG91 or Kaleyra — **login OTP only** |
| **Infra** | AWS ap-south-1 |
| **CI/CD** | GitHub Actions |

Two languages total: Java on the server, TypeScript on both clients.

---

## 2. Backend — Spring Boot

```
Java 21 (LTS)
Spring Boot 3.3+
Spring Web (MVC, servlet, thread-per-request — not WebFlux; see §2.5)
Spring Data JPA + Hibernate 6
PostgreSQL JDBC driver
Flyway                      migrations, plain versioned SQL
Spring Security             Argon2PasswordEncoder for OTP/MPIN hashing
jjwt (io.jsonwebtoken)       access + refresh token issuing/verification
Spring Data Redis           OTP cache, rate-limit counters
Bucket4j                    rate limiting
Spring Retry                @Retryable for idempotent batch-job retries
ShedLock                    single-runner guarantee for @Scheduled jobs
openhtmltopdf + Thymeleaf   PDF — pure Java, no Chromium
Apache POI                  Excel export
Apache Tika                 MIME detection by magic bytes, not extension
AWS SDK for Java v2          S3 + KMS
Logback + logstash-logback-encoder   structured JSON logging (redact PAN)
JUnit 5 + Testcontainers + AssertJ
ArchUnit                    architecture rules enforced as tests
Checkstyle + SpotBugs
Maven
```

Spring Initializr will scaffold most of this in one request; the specific choices above are the ones worth pinning deliberately.

### 2.1 Money is `BigDecimal` — and Java closes a hole Python had open

Java's type system already prevents the exact bug the v3 doc spent a paragraph warning about. Jackson (Spring Boot's JSON library) binds a JSON number directly into whatever type the field declares — if a DTO field is `BigDecimal principalAmount`, the number is parsed as `BigDecimal`, full stop. There's no intermediate `float` representation for Pydantic-style silent coercion to lose precision in.

The risk simply moves one step earlier: a developer, or an AI assistant, declaring the field as `double` in the first place.

```java
public record LoanCreateRequest(
    @NotNull BigDecimal principalAmount,   // never double
    @NotNull BigDecimal interestRate,      // never double
    LoanType loanType
) {}
```

```java
public static BigDecimal toRupees(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
}
```

Round rupee amounts at the boundary, keep full intermediate precision everywhere else — same discipline as before, just enforced by the compiler instead of a runtime config flag.

**Still add an ArchUnit test that fails the build if `float` or `double` appears on any entity, DTO, or service field.** It's belt-and-braces now rather than the primary defence, but it's a five-line test and it catches the case where someone copy-pastes from a calculator example:

```java
@ArchTest
static final ArchRule no_float_or_double_for_money =
    noFields().that().areDeclaredInClassesThat()
        .resideInAnyPackage("..entity..", "..dto..", "..service..")
        .should().haveRawType(double.class)
        .orShould().haveRawType(float.class)
        .because("money is BigDecimal, never a binary floating-point type");
```

### 2.2 Row Level Security with Spring and JDBC — the one genuinely tricky part

`SET LOCAL` (via `set_config(..., true)`) only applies inside a transaction, and connections are pooled by HikariCP. Get this wrong and Company A reads Company B's loan book — the exact failure RLS exists to prevent. This is the same hazard the Python version had with async SQLAlchemy; the mechanics differ, the risk doesn't.

**Don't reach for `@Transactional` plus a ThreadLocal plus an `@Around` AOP aspect here.** It can be made to work, but Spring's transactional advice and a custom aspect both wrap the method via proxies, and getting the *ordering* right — your `set_config` call must run **after** the transaction physically begins but **before** any other query on that connection — depends on `@Order` values that are easy to get subtly wrong. A plausible-looking wrong answer here is a cross-company data leak, so don't let an AI assistant improvise the ordering. Use an explicit, single, unambiguous block instead:

```java
@Component
@RequiredArgsConstructor
public class TenantSession {

    private final PlatformTransactionManager txManager;
    private final JdbcTemplate jdbcTemplate;

    public <T> T execute(UUID financierId, Function<Void, T> work) {
        TransactionTemplate tt = new TransactionTemplate(txManager);
        return tt.execute(status -> {
            jdbcTemplate.update(
                "SELECT set_config('app.current_financier_id', ?, true)",
                financierId.toString()
            );
            return work.apply(null);
        });
    }
}
```

Everything inside the lambda — the `set_config` call and the business logic — runs on the same bound connection, inside the one transaction `TransactionTemplate` opened. There's no ordering question to get right because there's only one block, read top to bottom. A controller resolves `financierId` from the verified JWT and passes it in; nothing downstream ever sets it another way.

**Write this test in week one and never delete it:**

```java
@Testcontainers
class TenantIsolationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16").withInitScript("schema.sql");

    @Test
    void companyBSeesNothingFromCompanyA() {
        tenantSession.execute(companyA, tx -> {
            jdbcTemplate.update(INSERT_LOAN, companyA, ...);
            return null;
        });

        List<Loan> rows = tenantSession.execute(companyB, tx ->
            jdbcTemplate.query("SELECT * FROM loans", loanRowMapper)
        );

        assertThat(rows).isEmpty();
    }
}
```

Run it against real Postgres via Testcontainers — H2 has no RLS and will pass meaninglessly, the same trap SQLite was for the Python version.

**If you add pgbouncer later:** session or transaction pooling mode only. Statement mode breaks `SET LOCAL` silently, regardless of what language is driving the connection.

**Keep Hibernate's second-level cache and query cache OFF.** This is a Java-specific trap with no real Python equivalent: second-level cache stores entity state across requests, keyed independent of the RLS session variable. A row cached while serving Company A can be served back to Company B on a cache hit, silently bypassing RLS entirely. It looks like free performance; it's a cross-tenant leak waiting to happen. Don't let an AI assistant "optimize" by turning it on.

### 2.3 Scheduling — Spring `@Scheduled` + ShedLock, no Celery

This is a genuine simplification over the Python stack, not just a swap. Celery needed a broker (Redis) plus dedicated worker and beat processes, and the Python doc's hard rule was "run exactly one Celery Beat instance — scaling it produces duplicate jobs." ShedLock removes that constraint structurally: it's safe to run as many application instances as you like, because the lock lives in Postgres and only one instance will ever win it for a given job at a given time.

```java
@Scheduled(cron = "30 0 * * * ?", zone = "Asia/Kolkata")
@SchedulerLock(name = "recomputeOverdueAll", lockAtLeastFor = "5m", lockAtMostFor = "25m")
public void recomputeOverdueAll() {
    List<UUID> financierIds = getActiveFinancierIds();   // via BYPASSRLS datasource
    financierIds.forEach(this::recomputeOverdueForTenant);
}

@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000))
public void recomputeOverdueForTenant(UUID financierId) {
    // idempotent — running it twice must produce the same result, because it will happen
}
```

No separate worker or beat container is needed at all — the scheduled methods run inside the same Spring Boot application that serves the API. Give the scheduler's datasource a **separate database role with `BYPASSRLS`** for the cross-tenant fan-out, exactly as the Python version did for its Celery role, and keep looping one tenant at a time so one company's bad data doesn't halt the whole run.

If a job later needs genuinely complex scheduling (dynamic schedules set by users, misfire-handling policies, job chaining), Quartz with a JDBC-backed clustered `JobStore` is the next step up — but don't start there. For eight cron-shaped jobs, ShedLock is the leaner choice for a two-person team.

### 2.4 Scheduled jobs

| Task | When (IST) | Purpose |
|---|---|---|
| `recomputeOverdueAll` | 00:30 | Days past due, schedule status, aging buckets |
| `accruePenalCharges` | 00:45 | Percentage or fixed amount, per loan config |
| `buildDailySnapshot` | 01:00 | Dashboard aggregates — never scan the ledger live |
| `generateRunningCosts` | 01:15 | Create upcoming recurring payables |
| `fetchGoldRate` | 09:00 | External rate feed |
| `expiryRecompute` | 06:00 | Refresh days-remaining on the Expiration Report |
| `stalePledgeSweep` | Weekly | Flag registry entries active past loan tenure |
| `registryReciprocityCheck` | Daily | Suspend query access for non-contributing companies |

Every job is `@SchedulerLock`-protected and every per-tenant unit of work is `@Retryable` and idempotent.

### 2.5 Blocking is fine — Spring MVC, not WebFlux

The Python doc spent a section on async discipline because FastAPI's whole advantage disappears the moment you block the event loop. Spring MVC doesn't have that problem in the first place: it's thread-per-request by design, so synchronous JDBC calls, PDF rendering, and image processing can run directly on the request thread without special handling.

The one thing to still get right: a PDF of 2,000 overdue rows (or any slow operation) shouldn't tie up a servlet thread for the length of a client's HTTP timeout under load. Put long-running PDF generation behind a job-id-and-poll pattern (§3), not a synchronous endpoint — not because blocking is wrong, but because you don't want thirty concurrent large-PDF requests exhausting the Tomcat thread pool while everyone else's dashboard requests queue up behind them.

Reactive (WebFlux + R2DBC) was deliberately not chosen here. It would buy back some of FastAPI's async model, but R2DBC's tooling and the Postgres-driver ecosystem around it are less mature than plain JDBC, and at this scale (50 to 5,000 companies) thread-per-request with a sensibly sized connection pool has no real ceiling problem. Don't add reactive programming to a two-person team's plate for a win you won't need.

---

## 3. PDF generation — server-side, always

Since users download PDFs rather than printing to a device, **generate every PDF on the server and return a download URL. Never generate PDFs on the client.** Same reasoning as the Python version: one implementation serves both web and mobile, identical output regardless of device, and nothing heavy runs on a ₹8,000 phone.

```java
String html = templateEngine.process("current-dues",
    new Context(Locale.forLanguageTag("en-IN"), Map.of(
        "rows", rows, "filters", appliedFilters,
        "totals", totals, "company", company, "generatedAt", Instant.now()
    )));

ByteArrayOutputStream pdf = new ByteArrayOutputStream();
PdfRendererBuilder builder = new PdfRendererBuilder();
builder.useFastMode();
builder.withHtmlContent(html, null);
builder.toStream(pdf);
builder.run();
```

openhtmltopdf is the closest Java analog to WeasyPrint: pure JVM, no 300 MB Chromium, and genuine `@page` CSS support. Its CSS coverage isn't quite as complete as WeasyPrint's — stick to the subset below and you won't hit the gaps. (If you later need pixel-perfect fidelity to a complex design and are willing to pay for a commercial licence, iText's `pdfHTML` add-on is the alternative — it's AGPL-licensed otherwise, which matters for a closed-source commercial product.)

### What the print stylesheet must handle

```css
@page {
  size: A4;
  margin: 15mm;
  @bottom-right { content: "Page " counter(page) " of " counter(pages); }
}
thead { display: table-header-group; }   /* repeat headers across pages */
tr    { page-break-inside: avoid; }
```

Every generated PDF carries a header block: company name, report title, **the filters that were applied**, generation date, and totals. The filter line matters — a printed list with no record of how it was filtered is untrustworthy a week later.

### PDF endpoints needed
Current Dues · Pending Dues · Expiration Report · Customer Statement · Collection Receipt · Loan Register · Expense Report · Seizure Report · Profit Summary

### Delivery

**Web** — a link to the endpoint.

**Mobile:**
```js
import * as FileSystem from 'expo-file-system';
import * as Sharing from 'expo-sharing';

const { uri } = await FileSystem.downloadAsync(
  pdfUrl,
  FileSystem.documentDirectory + 'current_dues.pdf'
);
await Sharing.shareAsync(uri);
```

The Android share sheet already includes Print, so users get local printing for free without you building anything for it. Unchanged from v3 — this is all client-side and doesn't touch the backend language.

### Large result sets — job id and poll, not a blocking request

For result sets over ~200 rows, write a `pdf_jobs` row, return its id, render on a small dedicated thread pool, and let the client poll `GET /pdf-jobs/{id}`. This replaces the Python version's "Celery task with a polled status" — same shape, no broker required since it's just a background thread pool and a status row in Postgres.

---

## 4. Web — React

```
React 19 + TypeScript + Vite
TanStack Query          server state, caching, invalidation
TanStack Table          the data tables — this is most of your app
Tailwind CSS + shadcn/ui
React Hook Form + Zod   loan forms are large and conditional
Recharts                dashboard
React Router 7
i18next                 Tamil / English
date-fns                with Asia/Kolkata handling
```

Unchanged from v3 — none of this depends on the backend language.

**Build the web app first.** The loan forms are where the data model gets stress-tested — roughly 25 fields for a vehicle loan, repeating item rows for gold, conditional fields when "commercial vehicle" is ticked. Get those right on a keyboard, then build mobile against a settled API.

An office tablet running the web app is a good middle ground: big enough for loan creation, portable enough to carry to a customer.

---

## 5. Mobile — React Native via Expo (managed)

```
Expo SDK 52+
React Native 0.76+ (New Architecture)
Expo Router                 file-based navigation
NativeWind                  Tailwind syntax — styling knowledge transfers from web
TanStack Query               same as web
expo-camera                 document and chassis photo capture
expo-image-manipulator      compress before upload
expo-local-authentication   fingerprint / face unlock for MPIN
expo-secure-store           refresh token storage
expo-file-system            PDF download
expo-sharing                share sheet — gives you print for free
react-native-mmkv           fast local key-value
i18next
EAS Build                   release builds only
```

Entirely unchanged from v3 — the mobile stack never depended on Python. **Not needed, deliberately:** `react-native-ble-plx` (no thermal printing), `op-sqlite` / `expo-sqlite` (no offline mode), `expo-notifications` (no notifications module). **Android only for v1.**

---

## 6. Shared TypeScript package

```
repo/
  apps/
    web/
    mobile/
  packages/
    shared/
      api-types.ts      generated from OpenAPI
      api-client.ts     openapi-fetch wrapper
      schemas.ts        Zod validation mirroring backend rules
      formatters.ts     currency, dates, PAN masking
      calculations.ts   interest display logic (schemes A, B, D)
```

pnpm workspaces or Turborepo. Both are fine; pnpm workspaces is simpler.

### Type generation — do this in week one

Spring Boot generates its OpenAPI document via **springdoc-openapi** instead of FastAPI's built-in schema — same idea, different endpoint:

```bash
# CI step
curl http://localhost:8080/v3/api-docs > openapi.json
npx openapi-typescript openapi.json -o packages/shared/src/api-types.ts
```

Then `openapi-fetch` on both clients, exactly as before. Change a Java DTO, regenerate, and both frontends fail to build until they're updated. This is what recovers most of the safety a single-language stack would have given you — the main failure mode of a split-language setup is hand-written TypeScript types drifting from the API, and this pipeline removes the hand-writing entirely.

**Share logic, never UI components.** Unchanged guidance — `react-native-web` is a trap here regardless of backend language.

---

## 7. Deployment

```
                CloudFront (CDN + WAF)
                        │
                   ALB (HTTPS)
                        │
                 api container(s)
          (Spring Boot fat JAR, embedded Tomcat,
           @Scheduled + ShedLock jobs run in-process —
           no separate worker or beat container)
        │               │
   ┌────┴───────────────┴────┬──────────────┐
RDS Postgres 16      ElastiCache Redis   S3 + KMS
Multi-AZ, ap-south-1  (OTP + rate limit)  ap-south-1
        │
   External: MSG91 (OTP SMS only)
```

One container type instead of three. ShedLock means you can run multiple API instances behind the load balancer for availability without reintroducing the "exactly one scheduler" constraint the Python version had — any instance can win the lock, none of them can win it twice at once.

**MVP shortcut:** a single EC2 instance running the Spring Boot app in Docker is plenty for the first 50 companies — there's no worker/beat container to add to the box in the first place. Move to ECS when you outgrow it.

**Data residency:** database, files, backups and logs all in India.

---

## 8. Cost

The JVM has a heavier baseline memory footprint than Python — budget **t4g.medium (4 GB)** rather than t4g.small (2 GB) for the app instance once a Spring Boot process is sharing a box with anything else. In ap-south-1 that's roughly $16.35/month vs. $8.18/month on-demand — about ₹700/month more, real but small next to the other line items below.

| Stage | Monthly |
|---|---|
| **MVP** (~50 companies) — t4g.medium EC2 (app only, no compose needed), RDS db.t4g.small, ElastiCache t4g.micro, S3 ~50 GB | **₹10,500–16,000** |
| **Growth** (~500 companies) — ECS 2–4 tasks at 1–2 GB each, RDS db.t4g.medium Multi-AZ, S3 ~500 GB | **₹42,000–62,000** |
| **Scale** (~5,000 companies) — autoscaled, RDS db.r6g.large + read replica | **₹2.2–3.5 lakh** |

**Messaging cost is unchanged from v3** — still negligible. With notifications removed you send login OTPs only: about 90 SMS/month per company, ₹11–22. The ₹ figures above are dominated by RDS, ElastiCache and S3, not compute, which is why the JVM's heavier memory footprint doesn't meaningfully move the total.

---

## 9. Build order — 10 weeks to MVP

```
Week 1     Schema as Flyway baseline + RLS wiring + tenant-isolation test
           ArchUnit money-type check · OpenAPI type generation · repo scaffolding
Week 2     Registration, OTP flow, sessions, MPIN
Weeks 3–4  Customers (PAN + mobile dedup), brokers, document vault + S3
Weeks 5–7  Loans: all 4 types, interest engine (A, B, D), schedule generation
Weeks 8–9  Repayment ledger, Current Dues, Pending Dues, receipt PDF
Week 10    Vehicle Inspection, Expiration Report, Business Expense,
           dashboard, PDF layer, hardening
```

Same ten weeks as the Python plan. Java's extra verbosity per line is real but doesn't change the schedule — most of the ten weeks is business-rule and UI work, not framework boilerplate, and that volume is identical regardless of language.

Seizing Details is small now that the approval chain is gone — fold it into week 10 if there's room, otherwise it's first in Phase 2.

**Mobile starts around week 6**, once the API has settled. Attempting both surfaces in parallel from week 1 with two people produces churn as the API changes underneath the app.

**Ship to 5–10 pilot companies in one district before building anything else.**

### Phase 2 (weeks 11–18)
Seizing Details (if deferred) · full report suite · Tamil localisation · chassis-number registry matching · UPI collection via Razorpay · offline mode *if pilots actually ask for it*

Don't build offline speculatively. It roughly doubles mobile complexity, and you'll know within a month of the pilot whether connectivity is genuinely a problem for your users.

---

## 10. Week-one checklist

These four are cheap now and expensive to retrofit:

1. **Tenant isolation test** — Company B tries to read Company A's loans, asserts empty. Against real Postgres, via Testcontainers.
2. **ArchUnit money-type check** — build fails if `double` or `float` appears on an entity, DTO, or service field.
3. **OpenAPI type generation** — wired into CI, sourced from springdoc's `/v3/api-docs`, before either frontend has real screens.
4. **Flyway baseline** — the full v2 schema as `V1__baseline.sql`, so nothing is ever hand-applied to a database.

---

## 11. Working with AI assistance — where it helps and where it doesn't

**AI is strong on:** Spring Boot controllers and JPA entities, React forms and tables, Tailwind styling, Flyway migrations, SQL queries, test scaffolding. These are high-volume, well-documented patterns — if anything, Spring Boot CRUD boilerplate is even more heavily represented in training data than FastAPI's.

**AI is weaker on, and you should verify by hand:**

- **React Native's New Architecture.** Unchanged from v3 — shipped as default in 0.76, much training data predates it. Check current docs rather than iterating with the model when something looks off.
- **The RLS + Spring/JDBC wiring (§2.2).** A plausible-looking wrong answer here is a cross-company data leak. Write it once by hand as the single `TenantSession` block above, test it, don't regenerate it — and watch for an AI assistant "helpfully" turning on Hibernate's second-level cache, which looks like a performance win and is actually a tenant-isolation hole.
- **Entities leaking straight out of controllers.** A common Spring Boot pattern AI assistants reach for is returning a JPA `@Entity` directly from a `@RestController` method. With lazy-loaded relations this throws `LazyInitializationException` outside the Hibernate session, or — worse — serializes further than intended (a `Loan` response that drags its `Customer`, which drags that customer's other loans). Always map to a DTO/record at the controller boundary; never return an entity.
- **Interest calculations.** Unchanged — AI will produce code that looks right and rounds wrong. Write the three schemes with worked examples in the tests — ₹1,00,000 at 2%/month for 12 months should produce exactly ₹24,000 interest under Scheme A — and check against a calculator, not against a model.
- **Performance on cheap hardware.** Unchanged — this is a mobile-client concern, independent of backend language. Buy a ₹8,000 Android device and test on it weekly.

---

## 12. Still-open questions

Unchanged from v3 — these are business questions, not technology ones:

1. **Which interest scheme do you actually use** — A, B, or D? And is the rate quoted per month or per annum? Still the biggest spec gap.
2. **Penal default** — percentage or fixed amount? Does a fixed penalty repeat each month the installment stays overdue, or is it charged once?
3. **The optional "Entered by" free-text field** — with one shared login it's the only way to know who handled a cash collection. One text box, no login system. Keep it or drop it?
4. **One district at launch, or wider?** The Vehicle Inspection registry is only useful at local density, so I'd argue hard for one district fully saturated.
5. **Business model** — if you ever charge per registry query, note that it discourages the exact checking behaviour the product exists to create. Bundle queries into the subscription.
