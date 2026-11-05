ALTER TABLE bookings
    ADD COLUMN payment_deadline TIMESTAMPTZ;

UPDATE bookings
SET payment_deadline = created_at + INTERVAL '15 minutes'
WHERE payment_deadline IS NULL;

ALTER TABLE bookings
    ALTER COLUMN payment_deadline SET NOT NULL,
    ADD COLUMN payment_reviewed_at TIMESTAMPTZ,
    ADD COLUMN payment_reviewed_by_staff_id BIGINT,
    ADD CONSTRAINT fk_bookings_payment_reviewer
        FOREIGN KEY (payment_reviewed_by_staff_id)
        REFERENCES staff (id)
        ON DELETE SET NULL;

CREATE INDEX idx_bookings_pending_payment_created_at
    ON bookings (created_at)
    WHERE status = 'PENDING_PAYMENT';

CREATE UNIQUE INDEX uq_manual_qr_payment_per_booking
    ON payments (booking_id)
    WHERE provider = 'MANUAL_QR';
