-- 연차 영속 — 총 연차(leave_balance)와 증감 내역(leave_usage).
-- 남은 연차는 저장하지 않는다: 내역이 정본이고 남은 값은 파생이다(따로 저장하면 둘이 어긋난다).
--
-- 소유 키는 코스 저장과 같은 guest_id 다. 인증이 붙는 날 user_id 로 옮기는 것은 코스와 함께 별도 PR.
-- MySQL / H2(MODE=MySQL) 호환. FK 없음.

CREATE TABLE leave_balance (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    guest_id   VARCHAR(64)  NOT NULL,
    total_days DOUBLE       NOT NULL,
    PRIMARY KEY (id),
    -- 소유자당 하나. 동시 요청이 두 행을 만드는 것을 DB 가 막는다.
    CONSTRAINT uk_leave_balance_guest UNIQUE (guest_id)
);

CREATE TABLE leave_usage (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    guest_id  VARCHAR(64)  NOT NULL,
    used_on   DATE         NOT NULL,
    -- 증감. 사용은 양수, 취소는 음수(행을 지우지 않고 되돌린다 — 언제 무엇이 취소됐는지 남긴다).
    days      DOUBLE       NOT NULL,
    reason    VARCHAR(100),
    -- 이 내역을 만든 코스(수동 입력이면 NULL). 코스 확정 차감의 중복 방지 키다.
    course_id BIGINT,
    PRIMARY KEY (id)
);

-- 소유자별 조회·합산이 기본 경로다.
CREATE INDEX idx_leave_usage_guest ON leave_usage (guest_id);
-- 같은 코스로 이미 차감했는지 확인(#91) — 소유자 안에서 코스로 찾는다.
CREATE INDEX idx_leave_usage_guest_course ON leave_usage (guest_id, course_id);
