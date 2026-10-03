# FinBase — Functional Specification

**Version:** 2.0 (revised per change request)
**Scope:** Lending management for individual financiers (private lenders)
**Jurisdiction assumed:** India

---

## What changed from v1

| # | Change | Applied |
|---|---|---|
| 1 | Business PAN and Owner PAN now optional | Part 1 |
| 2 | **Roles & permissions module removed** — one shared login per company | Throughout |
| 3 | Aadhaar number field removed; Aadhaar kept as uploaded photocopy only | Part 2 |
| 4 | Broker commission removed | Part 2 |
| 5 | Customer dedup now on **PAN + mobile only**; probabilistic matching removed | Part 2 |
| 6 | Customer mobile OTP verification removed | Part 2 |
| 7 | No customer delete, ever | Part 2 |
| 8 | Loan goes **straight to Active** — no approval workflow | Part 3 |
| 9 | Penal charge now **percentage OR fixed amount**, user's choice | Part 3 |
| 10 | Personal loan: employee ID, department, office address, DOJ, retirement date removed | Part 3 |
| 11 | Reference 2 and verification status now optional | Part 3 |
| 12 | Vehicle: duplicate key surrendered removed | Part 3 |
| 13 | Gold: wastage deduction, purity test method, net weight, max LTV, appraiser, seal number, storage location all removed — single gross weight | Part 3 |
| 14 | Interest Scheme C (reducing balance) removed — A, B, D only | Part 3 |
| 15 | Current Dues & Pending Dues: photo, loan ID, agent, row actions, bulk actions, colour coding removed; branch/agent filters removed; **Print added** | Part 3 |
| 16 | Collection entry: reference number and auto-capture removed; no borrower SMS, receipt only | Part 3 |
| 17 | Promise-to-pay removed | Part 3 |
| 18 | Seizure: owner approval removed, plus 9 fields removed, rules simplified | Part 4 |
| 19 | **Notifications / reminders module removed entirely** | Throughout |

---

# Part 0 — Assumptions & Flags

## 0.1 Single shared login — consequences you should know about

You've specified one login account per company, used by owner, manager and staff alike. That's applied throughout: no roles, no permissions, no approval workflows, no maker-checker.

Two practical consequences:

**Nothing can be attributed to a person.** When ₹5,000 is recorded as collected, the system knows the company recorded it, not who. In a cash-collection business that's normally the main internal control. **Minimal suggestion:** one optional free-text "Collected by / Entered by" field on collection entries and petty expenses. No login, no account, no complexity — just a name typed in, so the receipt book still tells you who handled the cash. I've included it as optional; delete it if you don't want it.

**Everyone sees everything**, including PAN numbers, customer documents, and full portfolio figures. That's inherent to a shared login and I've stopped pretending otherwise — the masking-and-unmask-with-approval design from v1 is gone.

## 0.2 Interest calculation — *still unconfirmed*

Three schemes supported, **Scheme A is the default**:

| Code | Scheme | Typical use |
|---|---|---|
| **A** | Monthly interest only, principal as bullet at maturity | Gold, document, most vehicle loans — the classic "vatti" model. **Default.** |
| **B** | Flat-rate EMI | Personal loans |
| **D** | Daily / weekly fixed collection | Small business borrowers |

*(Scheme C, reducing balance, removed as requested.)*

**Please still confirm:** rate quoted per month or per annum, and whether you charge penal interest at all.

**Defaults assumed:** rate **% per month** · simple interest, no compounding · grace period **5 days** · penal charge configurable per loan.

## 0.3 Aadhaar — photocopy only

No Aadhaar number field anywhere in the system. The Aadhaar photocopy is uploaded as a document like any other. This is the safer design — Aadhaar Act §29 restricts who may store the number, and you avoid the question entirely by never holding it.

**One recommendation:** upload the **masked Aadhaar** (first 8 digits blacked out) where the customer can provide it. It proves identity equally well and reduces what you're liable for if a phone or laptop is lost.

## 0.4 "Salary ATM card details" — unchanged flag from v1

Still storing bank name, IFSC, account number (encrypted), card last 4 digits, and salary credit date. **Not** the full card number, CVV, or PIN — storing those is prohibited under RBI card rules regardless of company size, and holding a borrower's card and PIN to draw their salary is unauthorised account access. The system records whether the card is physically held, as a custody note.

## 0.5 Other assumptions

| Area | Assumption |
|---|---|
| Currency | INR only |
| Language | English + Tamil |
| Loan approval | **None** — creation makes it active |
| Tenure | Months (A/B), days (D) |
| Part payments | Allowed. Order: penal → charges → interest → principal |
| Foreclosure | Allowed, optional charge |
| Gold purity | Mandatory — weight alone cannot produce a valuation |
| Vehicle re-registration | Chassis number captured as secondary registry key |
| Notifications | **None.** All reminders are on-screen and printable only |
| Data residency | India |

---

# Part 1 — Authentication & Account Setup

## 1.1 Purpose
Register a company, and authenticate its single shared account via mobile OTP on every login.

## 1.2 Registration flow

```
Step 1  Mobile number entry → OTP sent → verified
Step 2  Business details form
Step 3  Owner details form
Step 4  Document upload
Step 5  Submit → PENDING_VERIFICATION
Step 6  Platform admin verifies → ACTIVE → login enabled
```

Mobile is verified first, before any data entry — prevents junk registrations.

### Business details
| Field | Type | Mandatory | Validation |
|---|---|---|---|
| Company / firm name | text | ✅ | 3–150 chars |
| Entity type | enum | ✅ | Proprietorship / Partnership / Pvt Ltd / Individual |
| **Business PAN** | text | ❌ *(now optional)* | If given: `[A-Z]{5}[0-9]{4}[A-Z]` |
| GSTIN | text | ❌ | 15-char format if given |
| Money lending licence no | text | ❌ | Free text |
| Licence valid until | date | ❌ | Future date |
| Year established | year | ❌ | |

### Business address
| Field | Mandatory |
|---|---|
| Address line 1 | ✅ |
| Address line 2 | ❌ |
| Area / locality | ✅ |
| City / town | ✅ |
| Taluk | ❌ |
| District | ✅ |
| State | ✅ |
| PIN code | ✅ (6 digits) |

### Owner / primary contact
| Field | Mandatory |
|---|---|
| Full name | ✅ |
| **Mobile** (login identity, OTP-verified) | ✅ |
| Alternate mobile | ❌ |
| Email | ❌ |
| **Owner PAN** | ❌ *(now optional)* |

### Documents uploaded
Business registration proof · Money lending licence copy (if any) · Owner ID proof · Business address proof

### Validation
- Mobile must be unique platform-wide. Already registered → "Log in instead."
- Business PAN, if provided, must be unique platform-wide.
- Registration cannot submit until mobile OTP is verified.

## 1.3 OTP flow

### Generation
| Rule | Value |
|---|---|
| Length | 6 digits |
| Generation | Cryptographically secure RNG — never `Math.random()` |
| Storage | **Hashed** (argon2), never plaintext, never in logs |
| Store | Redis with native TTL |
| Delivery | SMS via DLT-registered template |

### Expiry & retry limits
| Rule | Value | On breach |
|---|---|---|
| OTP validity | **5 minutes** | "OTP expired, request a new one" |
| Verify attempts per OTP | **3** | OTP invalidated, must resend |
| Resend cooldown | **30 seconds** | Button disabled with countdown |
| Max resends per session | **3** | "Too many attempts. Try again in 30 minutes." |
| Max OTP requests per mobile | **10/hour, 20/day** | Soft block 1 hour |
| Failed verifies before lockout | **5 consecutive** | Locked **30 minutes** |
| Per-IP limit | 20/hour | IP throttled |

### Safeguards
- One active OTP per mobile — a new request invalidates the previous one.
- OTP bound to a session token issued at request time, so it can't be used from a different browser.
- Voice OTP fallback after 2 failed SMS deliveries — matters in areas with poor SMS delivery.
- Android SMS Retriever auto-read to remove typing friction.
- Failed attempts logged with IP and timestamp.

### Flow
```
Enter mobile
  ├─ Not registered → Registration
  ├─ Locked → show unlock time, stop
  └─ Generate OTP → hash → Redis (TTL 300s) → send SMS
       └─ Enter OTP
            ├─ Correct    → invalidate → issue tokens → Home
            ├─ Wrong (<3) → "Incorrect. N attempts left."
            ├─ Wrong (3)  → invalidate, require resend
            └─ Expired    → resend available
```

## 1.4 Session handling

| Token | Lifetime | Storage |
|---|---|---|
| Access token (JWT) | **15 minutes** | Memory (web) / secure storage (mobile) |
| Refresh token | **30 days** | httpOnly Secure cookie (web) / encrypted keystore (mobile) |

Refresh tokens **rotate** on each use, with reuse detection: if an already-used token is presented, the whole family is revoked and re-login is forced.

### OTP on every login — honoured, with a recommendation

As specified, every login requires OTP. Since this is one shared account used by several people daily, that's a lot of OTPs — and every SMS delivery failure blocks real work.

**Recommended addition — MPIN + biometric:**
1. First login on a device → full OTP
2. Set a 4- or 6-digit MPIN, optionally enable fingerprint
3. Subsequent opens on that device → MPIN or fingerprint only
4. OTP re-required on: new device, 30 days elapsed, app reinstall, explicit logout

Make it a company-level setting so you can force OTP every time if you prefer.

### Other controls
- Idle timeout: 15 min mobile, 30 min web
- Max 5 concurrent devices (shared account, several staff) — viewable and revocable
- Login alert SMS on a new device
- Device binding on refresh tokens

---

# Part 2 — Customers

## 2.1 Purpose
One record per borrower, reusable across loans.

## 2.2 User flow
```
Customers → [+ New Customer]
  → Basic details → Address → PAN
  → Photo capture
  → Upload Aadhaar photocopy
  → Upload address proof (gas / electricity bill)
  → Broker details
  → Save → duplicate check on PAN + mobile → Created
```

## 2.3 Data fields

### Identity
| Field | Type | Mandatory | Validation |
|---|---|---|---|
| Full name | text | ✅ | 3–100 chars |
| Father's / spouse's name | text | ❌ | |
| Date of birth | date | ❌ | Age ≥ 18 |
| Gender | enum | ❌ | |
| **Mobile number** | text | ✅ | 10 digits, starts 6–9. **Duplicate key.** No OTP verification. |
| Alternate mobile | text | ❌ | |
| Email | text | ❌ | |
| **PAN number** | text | ✅ | `[A-Z]{5}[0-9]{4}[A-Z]`, 4th char a valid holder-type code. **Primary duplicate key.** Encrypted at rest. |
| **Passport-size photo** | image | ✅ | Max 5 MB, JPEG/PNG, auto-compressed. Live capture preferred. |
| **Aadhaar photocopy** | file | ✅ | Upload only — **no Aadhaar number field**. Masked copy preferred. |

### Address
| Field | Mandatory |
|---|---|
| Address line 1 | ✅ |
| Address line 2 | ❌ |
| Area / locality | ✅ |
| City / village | ✅ |
| Taluk | ❌ |
| District | ✅ |
| State | ✅ |
| PIN code | ✅ (6 digits) |

### Address proof
| Field | Mandatory | Validation |
|---|---|---|
| **Gas bill / current electricity bill** | ✅ | Upload |
| Proof type | ✅ | Gas bill / Electricity bill |
| Consumer number | ❌ | |
| Bill date | ❌ | Warn if older than 90 days |

### Profile
| Field | Mandatory |
|---|---|
| Occupation | ❌ |
| Monthly income | ❌ |

### Broker
| Field | Mandatory |
|---|---|
| **Broker name** | ✅ |
| **Broker phone** | ✅ (10 digits) |
| **Broker address** | ✅ |

*(Broker commission removed.)*

Brokers live in a **separate table**, not as free text — the same broker refers many customers and you'll want to see business volume per broker. The customer record holds a broker link.

## 2.4 Screens & actions

| Screen | Actions |
|---|---|
| **Customer list** | Search (name, mobile, PAN, customer code) · Filter (has active loan / closed only / by broker / by district) · Sort · Paginate · **Print / Export** |
| **Customer detail** | Tabs: Profile · Loans · Documents. Actions: Edit, New loan, Call |
| **Add / Edit customer** | Single form with inline validation and draft autosave |

## 2.5 Business rules & validations

### Duplicate detection — PAN and mobile only
Deterministic checks at save. No fuzzy or probabilistic matching.

| Check | Behaviour |
|---|---|
| **PAN already exists** | 🔴 Hard block → "This PAN is already registered as [Customer Name]. View existing customer." |
| **Mobile already exists** | 🔴 Hard block → same treatment |

Both checks are scoped to your own company's records. There is no cross-financier customer lookup — that would require sharing borrower data, which the system deliberately does not do. (The only cross-company lookup is Vehicle Inspection, Part 7.)

### Other rules
- PAN format validated on entry, including the holder-type character.
- Mobile must be 10 digits starting 6–9.
- **No mobile OTP verification** for customers.
- **No delete.** A customer record is permanent, whether or not they have loans, and whether or not those loans are closed. A customer can be marked **Inactive** to hide them from default lists, but the record and its history remain. This is intentional — it's the loan history you'll want in three years when the same person comes back.
- Blacklisting available with a reason; shows a warning banner when creating a new loan for that customer, but does not block.
- Aadhaar photocopy and passport photo are both mandatory before a loan can be created.

## 2.6 Dependencies
Feeds → Loans (borrower record) · Documents · Reports

---

# Part 3 — Loans

Three sub-modules: **New Loan**, **Current Dues**, **Pending Dues**.

## 3.1 Loan lifecycle — simplified

```
NEW LOAN  →  ACTIVE  ─┬→ CLOSED        (fully repaid)
                      ├→ FORECLOSED    (closed early)
                      ├→ SEIZED        (→ Part 4)
                      └→ WRITTEN_OFF
```

**No draft, no pending approval, no approved, no rejected.** Creating a loan makes it active immediately, as requested. `OVERDUE` is derived nightly from the schedule, not stored as a status — a loan is simultaneously Active and overdue.

## 3.2 New Loan

### User flow
```
Loans → [+ New Loan]
  Step 1  Select customer (search existing / create new inline)
  Step 2  Select loan type → form changes
  Step 3  Collateral details + documents
  Step 4  Loan terms (amount, rate, scheme, tenure, penal)
  Step 5  ⚡ Vehicle Inspection auto-check (vehicle loans only) — blocking
  Step 6  Preview: schedule, total interest, total repayable, net disbursal
  Step 7  Create → ACTIVE immediately, schedule generated, registry updated
```

### General loan inputs (all types)
| Field | Type | Validation |
|---|---|---|
| Loan type | enum | Personal / Vehicle / Gold / Document |
| Loan amount | decimal | > 0; warn if above collateral valuation |
| Interest rate | decimal | > 0; warn if above configured ceiling |
| Rate type | enum | Per month / Per annum |
| Interest scheme | enum | A / B / D (default A) |
| Tenure | integer | Months (A/B) or days (D); 1–120 |
| Disbursal date | date | Not future |
| First due date | date | Auto-computed, editable |
| Processing fee | decimal | ≥ 0 |
| Other deductions | decimal | ≥ 0 |
| **Net disbursal** | computed | `amount − processing fee − other deductions` |
| Disbursal mode | enum | Cash / Bank transfer / UPI / Cheque |
| Repayment frequency | enum | Daily / Weekly / Fortnightly / Monthly |
| Grace days | integer | Default 5 |
| **Penal type** | enum | **Percentage** or **Fixed amount** — see below |
| **Penal value** | decimal | % per month, or ₹ per occurrence |
| Guarantor | link | Optional, links to another customer |
| Remarks | text | Optional |

### Penal charge — two options
As requested, the user chooses per loan:

**Option 1 — Percentage**
```
penal = overdue_amount × (penal_percent / 100) × (days_overdue − grace_days) / 30
```
Accrues daily on the overdue amount only. Default 2% per month.

**Option 2 — Fixed amount**
```
penal = penal_amount   (charged once per overdue installment)
```
A flat rupee figure the user defines, e.g. ₹500 per missed installment. Simpler to explain to borrowers and easier to collect. Optionally repeatable per month overdue — a config toggle on the loan.

Either option can be set to zero if you don't charge penalties.

### 3.2.1 Personal loan — additional fields

| Field | Mandatory |
|---|---|
| Employer name | ✅ |
| Designation | ❌ |
| Net monthly salary | ✅ |
| Salary credit day (1–31) | ✅ |
| Bank name | ✅ |
| Bank branch | ❌ |
| IFSC code | ❌ |
| Salary account number | ✅ (encrypted, shown masked) |
| Card last 4 digits | ❌ |
| Physical card held? | ❌ (+ where kept) |
| **Reference 1 — name** | ✅ |
| **Reference 1 — phone** | ✅ |
| Reference 1 — relationship | ❌ |
| **Reference 2 — name** | ❌ *(now optional)* |
| Reference 2 — phone | ❌ |
| Reference 2 — relationship | ❌ |
| **Reference verification status** | ❌ *(now optional)* — Verified / Not verified, per reference |

*(Employee ID, department, office address, date of joining and retirement date removed.)*

**Rules:**
- Only Reference 1 name and phone are mandatory.
- Salary credit day is used to set due dates — putting the due date the day after salary credit materially improves collection, and it's a one-click option on the schedule preview.
- No collateral, so this is the highest-risk product in the book. Worth watching on the dashboard.

### 3.2.2 Vehicle loan — additional fields

| Field | Mandatory | Validation |
|---|---|---|
| **Vehicle registration number** | ✅ | Normalised + format-validated (Part 7) |
| **Chassis number** | ⚠️ strongly recommended | 17 alphanumeric, no I/O/Q |
| Engine number | ❌ | |
| Vehicle type | ✅ | 2W / 3W / Car / LCV / HCV / Tractor / Construction |
| Commercial vehicle? | ✅ | Drives permit fields |
| Make / model | ✅ | |
| Variant | ❌ | |
| Manufacturing year | ✅ | |
| Fuel type / colour | ❌ | |
| Odometer reading | ❌ | |
| **RC book** | ✅ | Upload front + back; flag original held / copy only |
| RC owner name | ✅ | Warn if it doesn't match customer name |
| **Insurance copy** | ✅ | Upload |
| Insurance company / policy no | ✅ | |
| **Insurance expiry date** | ✅ | Future-dated → feeds Part 5 |
| **Licence number** | ✅ | Format-validated |
| Licence expiry | ❌ | → feeds Part 5 |
| **Permit expiry date** | ✅ *if commercial* | → feeds Part 5 |
| Fitness certificate expiry | ❌ | → feeds Part 5 |
| Vehicle valuation | ✅ | |
| Photos | ✅ | Front, rear, sides, odometer, **chassis close-up**, RC both sides |

*(Duplicate key surrendered removed.)*

**The chassis close-up photo is the highest-value field here.** It's evidence someone physically saw the vehicle rather than lending against a photocopied RC — the most common vehicle-loan fraud in this sector.

**Rules:**
- Vehicle Inspection runs automatically at Step 5 and blocks on a hit. Proceeding requires typing a reason.
- Registration number must not already be on an active loan in your own book.
- Insurance expiring before loan maturity → warning shown at creation.
- Permit expiry becomes mandatory when commercial is ticked.

### 3.2.3 Gold loan — additional fields *(simplified)*

| Field | Mandatory | Notes |
|---|---|---|
| **Item rows** (repeating) | ✅ | One row per ornament |
| → Item type | ✅ | Chain / Bangle / Ring / Necklace / Coin / Other |
| → Quantity | ✅ | |
| → **Weight (g)** | ✅ | Single gross weight, 3 decimals |
| → **Purity (karat)** | ✅ | 18K / 20K / 22K / 24K |
| → Hallmark / HUID | ❌ | |
| → Description | ❌ | |
| **Total weight (g)** | computed | Sum of item weights |
| Weighted average purity | computed | |
| Item count | computed | |
| **Current gold rate (₹/g, 24K)** | ✅ | Auto-filled from daily rate, editable |
| Rate date | ✅ | |
| **Total valuation** | computed | `Σ (weight × purity/24 × rate_24k)` |
| Photos | ✅ | Each item against a scale |

*(Stone/wastage deduction, net weight, purity test method, max LTV, appraiser name, packet seal number and storage location all removed.)*

**Rules:**
- Purity is mandatory. Weight alone cannot produce a valuation — 50g of 18K and 50g of 22K differ by about 22% in value.
- System computes the valuation; the user may override it, and the variance is shown.
- Loan amount above valuation → warning, not a block.
- **Gold cannot be double-pledged** — you physically hold it — so no shared registry check applies to gold loans.

### 3.2.4 Document loan — additional fields

| Field | Mandatory |
|---|---|
| **Unique document number** | ✅ |
| Sub-registrar office (SRO) | ✅ |
| Registration year | ✅ |
| Document type | ❌ (Sale deed / Settlement / Partition / Gift) |
| Survey number | ✅ |
| Sub-division number | ❌ |
| Patta number | ❌ |
| Village / Taluk / District | ✅ |
| Extent + unit (acres / cents / sq ft) | ✅ |
| Land classification | ❌ |
| **Current land price (per unit)** | ✅ |
| Guideline value | ❌ |
| **Total valuation** | computed — `extent × price per unit` |
| Number of title-holders | ❌ |
| EC number / date obtained / findings | ❌ (recommended) |
| EC valid until | ❌ → feeds Part 5 |
| Originals held — itemised list | ✅ |
| Documents uploaded | ✅ (deed, EC, patta/chitta) |

**Rules:**
- Uniqueness key is **SRO + year + document number**, not the document number alone.
- Survey number + village + taluk + district is a second uniqueness key — the same land can be pledged using different deeds.
- EC showing a prior encumbrance → warning at creation.
- **Legal note:** holding original title deeds without a registered mortgage gives weak enforceable security. The system records what you actually hold; it doesn't imply the paper alone is enforceable.

## 3.3 Interest & due calculation

### Scheme A — Monthly interest only, bullet principal *(default)*
```
monthly_interest = principal × (monthly_rate / 100)

Installments 1 to n−1 : due = monthly_interest
Installment n         : due = monthly_interest + principal
total_interest        = monthly_interest × tenure_months
total_repayable       = principal + total_interest
```
*Example:* ₹1,00,000 at 2%/month for 12 months → ₹2,000/month for 11 months, then ₹1,02,000. Total interest ₹24,000.

### Scheme B — Flat-rate EMI
```
total_interest = principal × (monthly_rate/100) × tenure_months
emi            = (principal + total_interest) / tenure_months
principal_component = principal / tenure_months        (same every month)
interest_component  = total_interest / tenure_months   (same every month)
```
*Example:* ₹1,00,000 at 2%/month for 12 months → interest ₹24,000, EMI ₹10,333.

### Scheme D — Daily / weekly collection
```
Either: daily_interest = principal × (annual_rate/100) / 365
Or:     fixed daily collection amount for N days
        total_collectable = daily_amount × days
```

*(Scheme C, reducing balance, removed.)*

### Due date computation
```
first_due_date = disbursal_date + 1 period   (editable)
due_date(k)    = add_period(first_due_date, k−1)
```
- **Month-end clamping:** disbursed on the 31st → due dates fall on the 30th/28th/29th in shorter months, never rolling into the next month.
- Optional **salary-aligned** due date for personal loans: `salary_credit_day + 1`.

### Overdue
```
days_past_due = today − due_date
overdue when  days_past_due > grace_days   (default 5)
```
Buckets: `1–30` · `31–60` · `61–90` · `90+`

### Payment appropriation
```
1. Penal charge
2. Other charges
3. Interest due (oldest first)
4. Principal
5. Excess → advance
```
Printed on the receipt. Disputes about "where did my payment go" are common, and showing the split prevents most of them.

### Foreclosure
```
foreclosure_amount = outstanding_principal
                   + interest accrued to date
                   + unpaid penal
                   + foreclosure charge (optional)
```

## 3.4 Current Dues

**Purpose:** what's collectable now.

| Element | Detail |
|---|---|
| **Default view** | Installments due today |
| **Tabs** | Today · This week · This month · Custom range |
| **Columns** | Customer name · Mobile · Loan type · Due date · Due amount · Paid so far · Balance · Days to due |
| **Filters** | Loan type · Amount range · Broker · Area / district |
| **Sort** | Due date · Amount (desc) · Customer name |
| **Actions** | 🖨️ **Print** · Export to Excel/PDF |
| **Summary strip** | Total due · Count · Collected today |

*(Customer photo, Loan ID, Agent assigned, row actions and bulk actions all removed. Branch and Agent filters removed.)*

### 🖨️ Print
The **Print button applies to the currently filtered and sorted table**, not the whole list. Filter to "This week, gold loans, Gandhipuram area", press Print, and you get exactly that list as a clean printable sheet — no screen chrome, no buttons — with the filter criteria, date, company name and totals printed in the header. A PDF export of the same view is available alongside.

This is the field worksheet. Someone can carry it, tick off collections by hand, and enter them at the end of the day.

### Collection entry
Opened by selecting a row from the table.

| Field | Validation |
|---|---|
| Amount received | > 0; ≤ outstanding + charges |
| Payment mode | Cash / UPI / Bank / Cheque |
| Payment date | Default today |
| Collected by / Entered by | Optional free text (see §0.1) |
| Remarks | Optional |

*(Reference number and auto-captured GPS/timestamp/device removed.)*

On save: generate receipt number → post ledger entry → recompute schedule → **print or download the receipt**.

**No SMS is sent to the borrower.** The receipt is generated for printing or saving only, as requested.

**Receipt numbering:** sequential per company, gapless. Because there's one shared login and no per-user attribution, the receipt sequence is your main check that nothing went unrecorded — a missing receipt number is the signal worth watching.

## 3.5 Pending Dues

**Purpose:** everything past grace.

| Element | Detail |
|---|---|
| **Default view** | All overdue installments, grouped by loan |
| **Tabs** | 1–30 · 31–60 · 61–90 · 90+ · All |
| **Columns** | Customer name · Mobile · Loan type · Original due date · **Days overdue** · Overdue principal · Overdue interest · **Penal accrued** · **Total overdue** · Last payment date · Collateral value |
| **Filters** | Loan type · Amount range · Broker · Area / district |
| **Sort** | Days overdue · Amount (desc) · Customer name |
| **Actions** | 🖨️ **Print** · Export to Excel/PDF |
| **Summary strip** | Total overdue ₹ · Count · Bucket-wise split · Collateral value at risk |

*(Row actions removed. Colour coding removed — the aging bucket tabs and the Days Overdue column carry that information. Filters match Current Dues. Promise-to-pay removed entirely.)*

Print behaves the same way: the filtered table, printed clean, with criteria and totals in the header.

Collection entry is reachable the same way as in Current Dues — select a row, record the payment.

Seizure is initiated from the loan detail screen (Part 4).

## 3.6 Dependencies
| Depends on | For |
|---|---|
| Customers | Borrower record, broker |
| Vehicle Inspection | Blocking check on vehicle loans |
| Documents | Uploads and checklists |
| **Feeds →** Seizing Details | Loan link, collateral, outstanding |
| **Feeds →** Expiration Report | Insurance / permit / licence / EC expiry dates |
| **Feeds →** Reports | All portfolio figures |

---

# Part 4 — Seizing Details

## 4.1 Purpose
A record of collateral seized on default — what was taken, when, why, and what happened to it.

## 4.2 User flow — simplified
```
Loan detail (or Pending Dues) → [Record Seizure]
  → Seizure form (reason, date, collateral reference)
  → Notice details, if served
  → Photos
  → Save
  → Later: record release (borrower paid) OR record sale
```

*(Owner approval step removed. Request → approve → authorise chain removed. A seizure is simply recorded when it happens.)*

## 4.3 Data fields

| Field | Mandatory | Notes |
|---|---|---|
| Seizure number | auto | `SZ-2026-0012` |
| Customer name | auto | From the loan |
| **Collateral type** | ✅ | Vehicle / Gold / Document |
| **Collateral reference** | ✅ | Vehicle number / document number / item description |
| **Seizure date** | ✅ | Not future |
| **Reason** | ✅ | Non-payment / Willful default / Collateral misuse / Asset being sold / Other |
| Reason detail | ✅ if Other | |
| Outstanding at seizure | auto | Snapshotted from the loan |
| Days overdue at seizure | auto | |
| Notice served? | ❌ | Yes / No |
| Notice date | ❌ | |
| Notice mode | ❌ | Registered post / Hand delivery / Courier |
| Notice copy | ❌ | Upload |
| Agency name | ❌ | If a recovery agency was used |
| **Photos** | ✅ | Minimum 2 |
| Current valuation | ❌ | Post-seizure assessment |
| Valuation date | ❌ | |
| Sale date | ❌ | |
| Sale mode | ❌ | Private sale / Auction |
| Sale price | ❌ | |
| Buyer name | ❌ | |
| Recovery charges | ❌ | Costs incurred |
| **Surplus / shortfall** | computed | `sale price − outstanding − recovery charges` |
| Surplus refunded? | ❌ | Date + reference |
| Release date | ❌ | If returned to borrower |
| Released to | ❌ | |
| Remarks | ❌ | |

*(Loan ID, authorised by, seized by, agency contact, seizure location, inventory list, storage location, storage cost per day, and status field all removed.)*

**One note on Loan ID:** it's gone as a *field you fill in*, since a seizure is always started from a loan and the link is set automatically. The underlying link is still there in the database — without it the seizure couldn't pull the outstanding amount, and the loan couldn't show its seizure history.

## 4.4 Instead of a status field

The formal status state machine is removed. The current state is simply derived from which dates are filled in:

| Condition | Shown as |
|---|---|
| Release date filled | **Released** |
| Sale date filled | **Sold** |
| Neither filled | **In custody** |

That's enough for a small operation and there's nothing to keep in sync.

## 4.5 Screens & actions
| Screen | Actions |
|---|---|
| **Seizure list** | Filter (collateral type, date range, reason, in custody / released / sold) · Search (seizure number, customer, vehicle number) · 🖨️ **Print** · Export |
| **Seizure detail** | All fields, photo gallery, linked loan, edit |
| **Record seizure** | Single form |
| **Record sale** | Sale details → proceeds → auto-computed surplus / shortfall |
| **Record release** | Release date + released to |

## 4.6 Business rules — simplified

- Photos mandatory, minimum 2.
- Loan status changes to **Seized** when a seizure is recorded.
- **On release or sale of a vehicle, the shared Vehicle Inspection entry is automatically closed.** Otherwise the vehicle stays falsely marked as pledged for every other financier on the platform. This one is automatic and shouldn't be optional.
- Surplus is computed and displayed when a sale is recorded. If the sale fetched more than was owed, **the difference belongs to the borrower** — the system shows the figure and lets you record the refund. It won't block you, but it will keep showing the unrefunded surplus on the seizure record, because that's a real liability, not a bookkeeping detail.
- Shortfall stays as a receivable on the loan; it doesn't disappear when the asset is sold.
- Notice fields are optional in the system, but serving notice before seizing is what makes a seizure defensible if the borrower disputes it later. Worth filling in even though nothing forces you to.

## 4.7 Dependencies
Loans (source and link) · Vehicle Inspection (auto-close on disposal) · Reports · Business Expense (recovery costs can be logged as petty expenses)

---

# Part 5 — Expiration Report

## 5.1 Purpose
One screen showing every time-bound document across active loans — expired, expiring soon, upcoming.

With notifications removed, **this report is the only thing standing between you and a lapsed insurance policy on a financed vehicle.** It's worth checking weekly. Consider pinning it to the home screen.

## 5.2 What it tracks

| Document | Source | Applies to |
|---|---|---|
| Vehicle insurance | Insurance expiry | Vehicle loans |
| Permit | Permit expiry | Commercial vehicles |
| Fitness certificate | Fitness expiry | Commercial vehicles |
| Driving licence | Licence expiry | Vehicle loans |
| Encumbrance certificate | EC valid until | Document loans |
| Loan maturity | Maturity date | All loans |
| Company licence | Licence valid until | Your own account |

Implemented as a **database view** that unions these sources. Expiry dates aren't copied into a separate table — they'd drift out of sync with the loan records.

## 5.3 Severity
| Severity | Condition |
|---|---|
| **Expired** | Expiry date has passed |
| **Critical** | 0–7 days remaining |
| **Warning** | 8–30 days |
| **Upcoming** | 31–60 days |

## 5.4 Screen
| Element | Detail |
|---|---|
| **Default view** | Expired + Critical + Warning, soonest first |
| **Tabs** | Expired · Next 7 days · Next 30 days · Next 60 days · All |
| **Columns** | Severity · Document type · Customer name · Mobile · Loan type · Reference (vehicle number / policy number) · Expiry date · **Days remaining** · Outstanding |
| **Filters** | Document type · Loan type · Severity · Area / district |
| **Actions** | 🖨️ **Print** (filtered view) · Export · Upload renewed document · Update expiry date |
| **Summary cards** | Expired count · Expiring in 7 days · Expiring in 30 days · **Value at risk** |

**"Value at risk" is the number that matters.** A vehicle loan with ₹4,00,000 outstanding and lapsed insurance means that if the vehicle burns tomorrow, your security is gone. The rupee figure is what prompts action; a count alone doesn't.

## 5.5 Business rules
- Insurance expiry mandatory and future-dated when creating a vehicle loan.
- Permit expiry mandatory when the vehicle is marked commercial.
- Uploading a renewed document updates the expiry date and the row drops off the report automatically.
- Nightly job recomputes days remaining.
- Your own company licence expiring shows a persistent banner.

*(All automatic reminders removed — this is a screen you check, not something that contacts you.)*

## 5.6 Dependencies
Loans (all source dates) · Documents (renewal uploads) · Dashboard (summary tile)

---

# Part 6 — Business Expense

## 6.1 Purpose
Track operating costs so you can see actual profit — interest income minus real costs.

Two sub-modules: **Running Cost** (recurring) and **Petty Expense** (ad-hoc).

## 6.2 Running Cost

**Recurring and predictable.**

### Categories
Office rent · Employee salary · Electricity · Water · Internet / phone · Software subscription · Vehicle EMI · Insurance premium · Accounting fees · Licence renewal · Borrowed capital interest · Other recurring

### Data fields
| Field | Mandatory |
|---|---|
| Category | ✅ |
| Description | ✅ |
| Amount | ✅ |
| **Frequency** | ✅ (Monthly / Quarterly / Half-yearly / Yearly) |
| Start date | ✅ |
| End date | ❌ (blank = ongoing) |
| **Due day of period** | ✅ (e.g. 5th of every month) |
| Payee name | ✅ |
| Payee contact | ❌ |
| Payment mode | ✅ |
| Employee name | ❌ (free text, for salary entries) |
| Auto-generate each period | ✅ (Yes / No) |
| Active | ✅ |
| Notes | ❌ |

### Generated payment records
Each period the system creates a payable: period, due date, amount, status (Pending / Paid / Partly paid / Skipped), paid date, paid amount, receipt upload.

### Screens
| Screen | Purpose |
|---|---|
| **Running cost list** | All templates · Filter by category, frequency, active · Monthly total · 🖨️ Print |
| **Add / Edit** | Form with frequency preview: "Next 3 due dates: …" |
| **Pending payments** | Unpaid generated entries with a Mark Paid action · 🖨️ Print |

### Rules
- Auto-generation runs nightly, creating payables in advance.
- Marking paid requires amount and date.
- Deactivating a template stops future generation but keeps history.
- Editing an active template applies from the next period only — past payables are never altered retroactively.

## 6.3 Petty Expense

**Ad-hoc, one-off, usually cash.**

### Categories
Transportation / fuel · Food & refreshments · Stationery & printing · Courier & postage · Mobile recharge · Repairs & maintenance · Legal & documentation · Seizure costs · Broker payment · Bank charges · Miscellaneous

### Data fields
| Field | Mandatory |
|---|---|
| Date | ✅ (default today) |
| Category | ✅ |
| Description | ✅ (min 5 chars — blocks useless "misc" entries) |
| Amount | ✅ |
| Payment mode | ✅ |
| Paid to | ❌ |
| **Bill / receipt photo** | ❌ (prompted above ₹500) |
| Linked loan | ❌ (optional cost attribution) |
| Linked seizure | ❌ (recovery costs) |
| Entered by | ❌ (optional free text) |
| Notes | ❌ |

*(Approval workflow removed — there's no second user to approve.)*

### Screens
| Screen | Purpose |
|---|---|
| **Petty expense list** | Filter (date range, category) · Search · Running total · 🖨️ **Print** · Export |
| **Quick add** (mobile) | Three fields: amount, category, photo. If this takes more than 15 seconds nobody will use it. |
| **Category summary** | Spend by category, month-on-month |

### Rules
- Entries can be edited or reversed; a reversal creates a contra entry rather than deleting the original, so the totals stay auditable.
- Duplicate warning: same amount + same category + same day.
- Seizure-linked expenses show on the seizure record as recovery costs.
- Loan-linked expenses enable per-loan profitability.

## 6.4 Expense reporting
- Monthly summary by category
- Running vs petty split
- **Profit view: interest income − total expenses** — the number most financiers want and almost never have
- Expense as a % of interest income
- Month-on-month and year-on-year comparison
- Export for your accountant

## 6.5 Dependencies
Seizing (recovery costs) · Loans (per-loan attribution) · Reports (profit summary)

---

# Part 7 — Vehicle Inspection *(cross-financier shared lookup)*

**The only module whose data crosses company boundaries.**

## 7.1 Purpose
Check whether a vehicle is already pledged with another financier on the platform, before lending against it.

## 7.2 Shared database — the architecture

Everything else is **isolated per company**: Company A cannot see Company B's customers, loans, expenses or documents. Vehicle Inspection is the deliberate exception.

```
┌─────────────────────────────────────────────────────┐
│                 SHARED LAYER                         │
│  vehicle_pledge_registry                             │
│   • vehicle number (normalised) + chassis number     │
│   • financier → company name                         │
│   • status: active | closed                          │
│   • pledged on date                                  │
│   ⚠️ NO customer name, NO loan amount, NO documents   │
│                                                      │
│  vehicle_inspection_queries (audit)                  │
│  financiers · gold_rates                             │
└─────────────────────────────────────────────────────┘
        ▲ writes                    ▲ reads
        │                           │
┌───────┴────────┐         ┌────────┴───────┐
│  COMPANY A     │    ✗    │  COMPANY B     │
│  customers     │◄───────►│  customers     │
│  loans         │   no    │  loans         │
│  documents     │ access  │  documents     │
│  expenses      │         │  expenses      │
└────────────────┘         └────────────────┘
```

Implemented as one PostgreSQL database. Company-scoped tables carry a `financier_id` with **Row Level Security** enforcing isolation at the database layer — not just in application code, because app-only isolation fails the first time someone writes a query missing a `WHERE` clause.

## 7.3 What the query returns

**Returns:**
- Match found: yes / no
- **Financing company name**
- **Status** — a result surfaces **only when status is `active`**
- Pledged-on date ("pledged 3 years ago" vs "last week" is very different information)
- Company district and contact number, so you can actually follow up

**Never returns:** borrower name, mobile, address, PAN, photo, loan amount, interest rate, outstanding balance, any document, or anything at all when the status is `closed`.

Closed loans returning nothing means nobody can use the registry to reconstruct a competitor's past book.

## 7.4 Vehicle number normalisation

Two companies entering the same vehicle must produce the same key.

```
normalise(input):
  1. Uppercase
  2. Strip spaces, hyphens, dots
  3. Validate against known Indian formats
  4. Store both the raw input and the normalised key
```

| Format | Pattern | Example |
|---|---|---|
| Standard | `[A-Z]{2}[0-9]{1,2}[A-Z]{1,3}[0-9]{4}` | `TN37BZ1234` |
| Bharat series | `[0-9]{2}BH[0-9]{4}[A-Z]{1,2}` | `24BH1234AB` |

`TN 37 BZ 1234`, `TN-37-BZ-1234` and `tn37bz1234` all become `TN37BZ1234`.

### The failure mode to design around
**A vehicle's registration number changes on re-registration or state transfer. The chassis number does not.** A borrower can pledge `TN37BZ1234` to Company A, re-register it in Karnataka as `KA05MN5678`, and pledge it to Company B. A registration-only registry returns "clear" — confidently and wrongly.

Capture the chassis number and register **both** keys. Query matches on either. One extra field at data entry, and it closes the largest hole in the design.

## 7.5 User flow

```
A) Standalone check
   Vehicle Inspection → enter vehicle number → Search → Result

B) Automatic during vehicle loan creation  ⚡ (the important one)
   New Loan → Vehicle → enter vehicle number → auto-check
      🟢 Clear       → proceed
      🔴 Hit         → blocked; proceeding requires typing a reason
      ⚪ Unavailable → blocked; retry (never proceed on a failed check)
```

## 7.6 Result screen

**🟢 No active pledge found**
```
Vehicle: TN37BZ1234
✓ No active pledge found on this platform.

Checked against 1,247 companies on the platform
(about 18% of financiers in Coimbatore district).

⚠️ This is NOT a guarantee. Companies not on this platform,
   and unregistered pledges, will not appear here.
   Continue your normal checks.
```

**The coverage line is a product requirement, not a legal disclaimer.** A bare green tick at 18% coverage creates false confidence — the financier then lends with *less* caution than with their old paper process, and the fraud gets worse, not better. Show the real number and let it speak.

**🔴 Active pledge found**
```
Vehicle: TN37BZ1234
⚠️ ACTIVE PLEDGE FOUND

Company    : Sri Lakshmi Finance
District   : Coimbatore
Contact    : 98xxx xxxxx
Status     : Active
Pledged on : 12 March 2025

[ Report incorrect ]   [ Proceed anyway — reason required ]
```

**⚪ Check unavailable**
```
Unable to verify right now. Do not proceed without a completed check.
[ Retry ]
```

## 7.7 Registry data fields

| Field | Notes |
|---|---|
| Normalised vehicle number | Primary lookup key |
| Raw vehicle number | As entered |
| Chassis number (normalised) | Secondary key — survives re-registration |
| Engine number (normalised) | Tertiary key |
| Vehicle type, make, model, year | Safe to share; confirms it's the same vehicle |
| Company | Returns the company name |
| Loan link | Internal only, **never returned** |
| Status | `active` / `closed` |
| Pledged on / Released on | Dates |

## 7.8 Business rules

### Registration
- **Automatic on loan creation.** Since loans go straight to Active, the registry entry is created the moment the vehicle loan is saved. If registration were a manual step, compliance would collapse within weeks.
- **Automatic release** when the loan becomes Closed, Foreclosed or Written Off, or when a seized vehicle is sold or released.
- One active entry per vehicle per company.

### Query access
| Control | Rule |
|---|---|
| Who can query | Active companies only |
| Rate limit | 100 queries/day per company; 20/hour |
| Audit | Every query logged: company, time, vehicle, result |
| Enumeration detection | High-volume querying without matching loans → auto-flag and throttle |
| Suspension | Confirmed misuse → query access suspended, with an appeal route |

Without these, the registry becomes a tool for building a target list of financed vehicles. This isn't optional even for a small platform.

### Reciprocity
A company that queries but never registers its own pledges is free-riding, and enough of them will kill the registry. **If a company has created vehicle loans in the last 30 days but registered zero pledges, query access is suspended** until it's in sync. Each company sees its own contribution status.

### Disputes
"Report incorrect" opens a dispute; platform admin reviews and contacts the registering company. Pledges still active long past the loan tenure are auto-flagged for review — otherwise vehicles stay falsely encumbered forever.

## 7.9 Limitations to state plainly

1. **Coverage.** Below roughly 30–40% of financiers in a district, "clear" means very little. This is a go-to-market problem, not an engineering one — go district by district and saturate before expanding.
2. **Non-participants.** Banks, NBFCs and off-platform financiers are invisible. VAHAN or Digilocker integration would close much of this later.
3. **Deliberate non-registration.** Reduced by reciprocity gating, not eliminated.
4. **False pledges to block competitors.** Mitigated by requiring every registry entry to link to a real loan.
5. **Re-registration.** Solved only if you capture chassis numbers.

## 7.10 Dependencies
Vehicle loans (auto-register on creation) · Seizing (auto-close on disposal) · Loan closure (auto-close)

---

# Part 8 — Reporting & Dashboard

## 8.1 Home dashboard — one screen

**Top cards**
| Metric | Detail |
|---|---|
| Total disbursed | Lifetime + this month |
| Total outstanding | Principal + accrued interest |
| Collected today | |
| Total overdue | ₹ and count |
| Active loans | Count by type |
| Net profit (this month) | Interest income − expenses |

**Widgets**
- Collections due today
- Overdue aging (1–30 / 31–60 / 61–90 / 90+)
- Loan mix by type
- Disbursal vs collection trend, 6 months
- **Expiring documents — next 30 days**, count and value at risk
- Recent seizures
- Top 5 brokers by business volume

*(Pending approvals widget removed — there are no approvals.)*

## 8.2 Reports — all printable

| Report | Contents |
|---|---|
| **Active loans** | All active loans with outstanding, filterable |
| **Loan register** | Every loan: customer, amount, rate, tenure, status |
| **Collection report** | Collections by day/week/month and by mode |
| **Overdue report** | Bucket-wise overdue and trend |
| **Customer statement** | Per-customer ledger — disbursal, every repayment, appropriation split, balance. Printable and handed to the borrower. |
| **Expiration report** | Part 5 output |
| **Seizure report** | Seizures by outcome, realisation vs outstanding |
| **Expense report** | By category, running vs petty, month-on-month |
| **Profit summary** | Interest + fees + penal − expenses − write-offs |
| **Collateral value at risk** | Collateral value vs exposure by type |
| **Broker report** | Loans sourced and value per broker |
| **Vehicle inspection log** | Queries made, hits found, overrides — also feeds reciprocity |
| **Cash book** | Cash in / out, closing balance |

Every report and every list screen has the same **Print** behaviour: what you see after filtering is what prints, with criteria, date, company name and totals in the header.

## 8.3 Technical notes
- **Snapshot daily balances into a summary table.** Don't compute portfolio totals by scanning the ledger live — the dashboard will crawl past a few thousand loans.
- Date presets: Today · Yesterday · This week · This month · Last month · This FY · Custom.
- Excel and PDF export on every report.

---

# Part 9 — Data Privacy & Security

Simplified for a single shared login, but the parts that protect you against a lost phone or a stolen laptop still matter.

## 9.1 What's sensitive
| Level | Data | Treatment |
|---|---|---|
| **High** | PAN, bank account number, salary account | Encrypted at rest; PAN shown in full (single login, no role separation) |
| **High** | Aadhaar photocopy, customer photo, RC, land documents | Encrypted at rest, time-limited access links, never public URLs |
| **Medium** | Name, address, mobile, loan details | Company-isolated |
| **Low** | Loan type, status, totals | Standard |

**No Aadhaar number is stored anywhere** — only the uploaded photocopy. That's the single biggest privacy improvement in this revision.

## 9.2 Encryption
- **In transit:** TLS 1.3
- **At rest:** full-database encryption, plus field-level encryption for PAN and bank account numbers
- **Files:** server-side encryption, pre-signed URLs with short expiry, no public buckets ever
- **Backups:** encrypted, with **tested restores** — an untested backup isn't a backup

## 9.3 Audit trail
Even with one login, keep a record of what changed: entity, action, before and after values, timestamp. It won't tell you *who* (shared account), but it tells you *what* and *when*, which resolves most disputes about whether a figure was edited.

**Must log:** loan creation and closure · every financial transaction and reversal · customer edits · seizure records · expense reversals · vehicle inspection queries · data exports · login and failed login.

Ledger entries and audit records are **append-only** — corrections are reversal entries, never edits or deletes.

## 9.4 DPDP Act 2023 — the minimum
- A short **consent notice** at customer onboarding, in the borrower's language, covering what you collect, why, and that vehicle pledge data is shared on the platform.
- **Separate tick for shared registry participation** — this is the legal basis for Part 7, and bundling it into general terms isn't sufficient.
- Retention schedule: closed loans' document sets shouldn't sit on the server forever.
- Basic grievance contact shown in the app.

This is deliberately light. You're a small company, but the DPDP Act doesn't have a small-company exemption for personal data.

## 9.5 Application security
- Parameterised queries only
- Rate limiting on all endpoints, aggressive on login
- Server-side input validation
- File upload: type allowlist, size limits, virus scan
- Secrets never in code
- Dependency scanning in CI

## 9.6 Data residency
Database, files, backups and logs all stored in India.

---

# Part 10 — Offline & Mobile

## 10.1 What must work offline *(Phase 2)*
- View today's collection list (pre-synced)
- **Record a repayment and generate a receipt**
- Capture photos
- Create a customer as a draft
- Add a petty expense
- View a customer's loan summary (cached)

## 10.2 What must NOT work offline
| Blocked | Why |
|---|---|
| **Vehicle Inspection** | A cached "clear" answer is worse than no answer — it manufactures false confidence |
| Creating a vehicle loan | Depends on the inspection check |
| Any report | Needs live totals |

Offline is for *recording what already happened*. Online is required for *checks and decisions*.

## 10.3 Technical approach
- Encrypted local SQLite with a sync queue
- **Receipt numbers pre-allocated in blocks** so offline receipts don't collide
- Server-authoritative on balances; an offline payment against a loan closed meanwhile goes to an exception list for a human to sort out, never auto-resolved
- Photos compressed before queueing, uploaded on Wi-Fi
- **Sync status always visible** — "3 entries pending upload". Ambiguity here causes double-entry, which is worse than the original problem
- Device PIN or fingerprint lock; local data purged after 7 days

## 10.4 Mobile UX priorities
- **Speed decides adoption.** If a gold loan takes eight minutes on the app and ninety seconds in a notebook, the notebook wins. Target: gold loan under 3 minutes, collection entry under 15 seconds.
- Camera-first capture with auto edge-detection
- OCR assist for RC and PAN to prefill fields
- Large tap targets, high contrast — used outdoors
- Tamil + English toggle
- Must run on a ₹8,000 Android phone with 2 GB RAM — test on real low-end hardware, not emulators
- **Printing from mobile:** support Bluetooth thermal receipt printers for collection receipts, and standard PDF sharing for the filtered table prints. With no SMS going out, the printed receipt is the borrower's only record, so this needs to actually work well.
