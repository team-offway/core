-- 지자체별 중심 관광지(한국관광 데이터랩 LocgoHubTarService1) — #185.
--
-- 관광공사가 실제 이동 데이터로 계산한 "타 관광지와 가장 많이 연결되는" 순위다.
-- 우리가 점수식을 만들 필요가 없고, TourAPI 조회순보다 대표성이 높다.
--   공주시 예: 조회순 1위 "연미산 자연미술공원" vs 중심 1위 "공산성"
--
-- DB 로 내린다 — 요청 경로에서 외부를 부르지 않고, 배포로 캐시가 날아가지도 않는다(#193 과 같은 이유).
CREATE TABLE hub_attraction (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    region_id       BIGINT       NOT NULL,
    -- 집계 기준 연월(YYYYMM). 원본이 월 단위로 갱신되므로 갱신 필요 여부 판단에 쓴다.
    base_ym         CHAR(6)      NOT NULL,
    -- 지자체 안 순위(1부터). 낮을수록 중심에 가깝다.
    hub_rank        INT          NOT NULL,
    -- 데이터랩의 관광지 식별자. 우리 POI 체계와 다르므로 그대로 보관한다.
    hub_code        VARCHAR(64)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    -- 대분류(관광지·음식·숙박). 대표 사진은 관광지만 쓴다 — 정선 1위가 콘도라 필터가 필요하다.
    category_large  VARCHAR(50),
    -- 중분류(역사관광·문화관광·자연관광·쇼핑 등). 화면 칩으로 쓴다.
    category_medium VARCHAR(50),
    lat             DOUBLE,
    lng             DOUBLE,
    PRIMARY KEY (id)
);

CREATE INDEX idx_hub_attraction_region_rank ON hub_attraction (region_id, hub_rank);
