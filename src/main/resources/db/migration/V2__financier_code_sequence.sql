-- =====================================================================
--  Atomic, collision-free financier_code allocation (FIN-000123).
--  V1's schema had no sequence for this — registration needs one that's
--  safe across multiple app instances.
-- =====================================================================

CREATE SEQUENCE financier_code_seq START WITH 1 INCREMENT BY 1;
