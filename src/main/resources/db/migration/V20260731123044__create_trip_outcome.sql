-- 지난 여행을 실제로 다녀왔는지(#116).
-- MySQL / H2(MODE=MySQL) 호환. FK 없음.
--
-- 왜 필요한가: 여행이 끝나면 홈에서 "다녀오셨나요?" 를 묻는다. 답을 기억하지 않으면 매번 다시 물어 사용자를
-- 괴롭히고, 반대로 안 물어보면 연차 잔액이 계속 틀린 채로 남는다. 이 테이블이 "이미 물어봤다" 는 기억이다.
--
-- 안 간 여행도 기록한다. 안 기록하면 '아직 안 물어본 것' 과 구분되지 않아 영원히 다시 뜬다.

CREATE TABLE trip_outcome (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    guest_id    VARCHAR(64)  NOT NULL,
    course_id   BIGINT       NOT NULL,
    -- VISITED(다녀옴 — 연차 차감) / NOT_VISITED(안 감 — 차감 없음)
    outcome     VARCHAR(20)  NOT NULL,
    answered_on DATE         NOT NULL,
    PRIMARY KEY (id),
    -- 코스당 하나. 모달을 두 번 눌러도 두 번 차감되지 않게 DB 가 막는다.
    CONSTRAINT uk_trip_outcome_guest_course UNIQUE (guest_id, course_id)
);

-- 대기 목록은 "이 소유자가 이미 답한 코스" 를 통째로 묻는다.
CREATE INDEX idx_trip_outcome_guest ON trip_outcome (guest_id);
