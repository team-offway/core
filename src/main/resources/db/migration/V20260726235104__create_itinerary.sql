-- itinerary 애그리거트(코스 자동 생성) — course ← day_schedule ← slot.
-- FK 제약은 두지 않는다(persistence-convention: additive·out-of-order·forward-only). 조회 인덱스만 유지.
-- MySQL / H2(MODE=MySQL) 양쪽 호환.

CREATE TABLE course (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    region_id   BIGINT       NOT NULL,
    travel_days INT          NOT NULL,
    density     VARCHAR(20)  NOT NULL,
    transport   VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_course_region ON course (region_id);

CREATE TABLE day_schedule (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    course_id  BIGINT NOT NULL,
    day_number INT    NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_day_schedule_course ON day_schedule (course_id);

CREATE TABLE slot (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    day_schedule_id          BIGINT       NOT NULL,
    order_in_day             INT          NOT NULL,
    time_of_day              VARCHAR(20)  NOT NULL,
    kind                     VARCHAR(20)  NOT NULL,
    poi_content_id           VARCHAR(64)  NOT NULL,
    title                    VARCHAR(200) NOT NULL,
    lat                      DOUBLE       NOT NULL,
    lng                      DOUBLE       NOT NULL,
    travel_minutes_from_prev INT          NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_slot_day ON slot (day_schedule_id);
