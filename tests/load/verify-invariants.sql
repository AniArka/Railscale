-- ============================================================================
-- RailScale Invariant Verification Suite (Architecture Section 54)
-- Must be executed after load tests to verify consistency and zero overselling.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- INVARIANT 1 & 2: No duplicate booking items on confirmed bookings (Zero Overselling)
-- EXPECTED: 0 rows returned
-- ----------------------------------------------------------------------------
SELECT 
    'INVARIANT_OVERSLEEPING_CHECK' AS test_name,
    bi.inventory_id,
    COUNT(*) AS confirmed_count,
    CASE WHEN COUNT(*) > 1 THEN 'VIOLATION: OVERSELLING DETECTED' ELSE 'OK' END AS status
FROM booking_items bi
JOIN bookings b ON bi.booking_id = b.id
WHERE b.status = 'CONFIRMED'
GROUP BY bi.inventory_id
HAVING COUNT(*) > 1;

-- ----------------------------------------------------------------------------
-- INVARIANT 3 & 4: Seat status and metadata consistency
-- - HELD seats MUST have held_by AND held_until
-- - AVAILABLE or BOOKED seats MUST NOT have held_by or held_until
-- EXPECTED: 0 rows returned
-- ----------------------------------------------------------------------------
SELECT 
    'INVARIANT_HELD_METADATA_MISSING' AS test_name,
    id, status, held_by, held_until
FROM seat_inventory
WHERE status = 'HELD' AND (held_by IS NULL OR held_until IS NULL);

SELECT 
    'INVARIANT_NON_HELD_METADATA_LEAK' AS test_name,
    id, status, held_by, held_until
FROM seat_inventory
WHERE status IN ('AVAILABLE', 'BOOKED') AND (held_by IS NOT NULL OR held_until IS NOT NULL);

-- ----------------------------------------------------------------------------
-- INVARIANT 6: Idempotency uniqueness
-- EXPECTED: 0 rows returned
-- ----------------------------------------------------------------------------
SELECT 
    'INVARIANT_IDEMPOTENCY_DUPLICATE' AS test_name,
    user_id, idempotency_key, COUNT(*) AS occurrences
FROM idempotency_keys
GROUP BY user_id, idempotency_key
HAVING COUNT(*) > 1;

-- ----------------------------------------------------------------------------
-- SUMMARY REPORT: Seat Inventory Breakdown
-- ----------------------------------------------------------------------------
SELECT 
    status,
    COUNT(*) AS count
FROM seat_inventory
GROUP BY status;

-- ----------------------------------------------------------------------------
-- SUMMARY REPORT: Bookings Status Breakdown
-- ----------------------------------------------------------------------------
SELECT 
    status,
    COUNT(*) AS count,
    COALESCE(SUM(total_amount), 0) AS total_revenue
FROM bookings
GROUP BY status;

-- ----------------------------------------------------------------------------
-- SUMMARY REPORT: Outbox Event Publishing State
-- ----------------------------------------------------------------------------
SELECT 
    event_type,
    COUNT(*) AS total_created,
    COUNT(published_at) AS total_published,
    COUNT(*) - COUNT(published_at) AS pending_publish
FROM outbox_events
GROUP BY event_type;
