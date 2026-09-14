-- 관광지 집중률 예측 적재(#565).
--
-- 한 행이 "그 관광지의 그 날짜" 다. 89곳 × 관광지 평균 40개 × 30일이라 10만 행 안쪽을 예상한다
-- (실측 최대 태안 2,910행 = 관광지 97개 × 30일).
--
-- 자연키가 지역 + 관광지명 + 날짜인 이유 — 이 API 는 콘텐츠 ID 를 안 주고 관광지명만 준다. 이름은
-- 지역 안에서만 유일하면 되고, 전국으로 보면 겹치는 이름이 많아 지역을 반드시 낀다.
--
-- FK 는 두지 않는다(persistence-convention). 조회 인덱스만 둔다.
CREATE TABLE attraction_crowd_forecast (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    region_id       BIGINT       NOT NULL,
    attraction_name VARCHAR(200) NOT NULL,
    base_date       DATE         NOT NULL,
    rate            DOUBLE       NOT NULL,
    fetched_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_attraction_crowd_natural UNIQUE (region_id, attraction_name, base_date),
    -- 코스 한 건이 "이 지역의 이 날짜들" 을 한 번에 읽는다.
    KEY idx_attraction_crowd_lookup (region_id, base_date),
    -- 이번 회차에 안 온 예보를 지우는 기준.
    KEY idx_attraction_crowd_fetched (fetched_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
