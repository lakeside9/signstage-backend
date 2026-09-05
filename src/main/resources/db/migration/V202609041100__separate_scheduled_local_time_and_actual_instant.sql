-- 예약 일시는 행사 시간대의 벽시계 값이므로 DATETIME으로, 실제 발생 시각은 UTC instant이므로
-- TIMESTAMP로 구분한다. 행사의 IANA time_zone_id와 scheduled_*를 결합해야 실제 시점을 얻는다.
ALTER TABLE ceremony_events
    MODIFY COLUMN scheduled_start_at DATETIME(6) NULL,
    MODIFY COLUMN scheduled_end_at DATETIME(6) NULL,
    MODIFY COLUMN actual_start_at TIMESTAMP(6) NULL,
    MODIFY COLUMN actual_end_at TIMESTAMP(6) NULL;
