## 1. Money is always BigDecimal. Never float or double.
- Every monetary field in entities, DTOs, and business logic is
  `java.math.BigDecimal`. Every money column is `NUMERIC(15,2)`.
- Interest rates and weights are BigDecimal too.
- Round to rupees only at the boundary:
      value.setScale(2, RoundingMode.HALF_UP)
  Keep full precision for intermediate steps.
- If you write `double` or `float` for anything touching money, you have
  introduced a bug. Java's type system makes this harder to do by accident
  than Python's did, but it is not impossible — an AI assistant copying a
  generic calculator example can still reach for `double`. A ₹0.01 error
  compounds across thousands of transactions and destroys the customer's
  trust in the ledger.

## 2. Tenant isolation is enforced by the database, not by application code.
- Every tenant-scoped query runs inside the `TenantSession` helper, which
  opens a transaction and immediately executes:
      SELECT set_config('app.current_financier_id', ?, true)
  The third argument `true` makes it transaction-local. Without it the
  setting leaks onto the pooled HikariCP connection and the next request —
  possibly a different company — inherits it.
- Do not implement this with `@Transactional` plus a separate `@Around` AOP
  aspect unless you can prove the advice ordering is correct. Use the single
  explicit `TenantSession.execute(...)` block instead — one method, read top
  to bottom, nothing to get subtly wrong.
- Never filter by financier_id in application code as the primary isolation
  mechanism. RLS policies do that. Application filters are belt and braces.
- **Hibernate's second-level cache and query cache must stay OFF.** Cached
  entity state is not re-checked against the current session's RLS variable
  on a cache hit — a row cached while serving Company A can be served back
  to Company B. This looks like a performance optimisation. It is a
  cross-tenant data leak.
- Scheduled jobs (§4) run cross-tenant and use a separate BYPASSRLS
  database role. They must loop tenants explicitly, one unit of work per
  company.
- A failure here means one lending company reads another's entire loan book.

## 3. The ledger is append-only.
- `loan_transactions` is never updated or deleted. Corrections are reversal
  entries (contra postings) that reference the original.
- Same for `audit_log`.
- Recompute balances from the ledger; the denormalised columns on `loans`
  are a cache, not the source of truth.

## 4. Never store these, under any framing:
- Aadhaar numbers (photocopy only)
- Full debit/credit card numbers, CVV, or PIN — only the last 4 digits
- Plaintext OTPs — hash with Argon2 (Spring Security's
  `Argon2PasswordEncoder`), store in Redis with a TTL
- Passwords in any form (there are none; auth is OTP + MPIN)
PAN and bank account numbers ARE stored, but encrypted at rest (AES-256-GCM
via AWS KMS) with a last-4 column for display.

## 5. Vehicle Inspection query responses are strictly limited.
Return ONLY: match found (yes/no), financing company name, company district,
company contact number, status, pledged-on date.
NEVER return: borrower name, mobile, address, PAN, photo, loan amount,
interest rate, outstanding balance, any document, or anything at all when
the pledge status is 'closed'.
Every query is logged and rate-limited (Bucket4j).

## 6. Never show a bare "clear" result on a vehicle check.
Always display coverage context alongside it, e.g. "Checked against 1,247
companies (about 18% of financiers in this district). This is not a
guarantee." A reassuring green tick at low coverage creates false
confidence and makes the fraud worse than the paper process did.

## 7. Timestamps are UTC in the database, Asia/Kolkata in the UI.
All columns are TIMESTAMPTZ, mapped to `java.time.OffsetDateTime` or
`Instant` in entities. Never store a naive `LocalDateTime` for anything
that crosses a day boundary. Due-date drift from timezone bugs is the
classic failure in lending systems.

## 8. Entities never leave the service layer.
Controllers return DTOs/records, never JPA `@Entity` objects. An entity
returned straight from a `@RestController` either throws
`LazyInitializationException` outside the Hibernate session, or serializes
further than intended — a `Loan` response dragging in its `Customer`, which
drags in that customer's other loans. Map explicitly at the boundary.

## 9. Do not add features that are not in the spec.
Specifically absent by design, and not to be helpfully reintroduced:
roles and permissions, approval workflows, notifications and reminders,
offline mode, thermal printing, reducing-balance interest, fuzzy customer
matching, promise-to-pay, visit logs, borrower-facing portal.