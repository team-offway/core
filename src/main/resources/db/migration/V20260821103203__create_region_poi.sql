-- 지역별 장소 풀 (#304) — 사진·소개를 갖춘 장소를 지역 단위로 꺼낼 수 있게 한다.
--
-- 왜 저장하나: 같은 데이터가 코스 생성 응답에는 이미 실리는데, 지역 단위로 받을 입구가 없었다.
-- 요청 경로에서 TourAPI 를 부르면 지역당 3콜이라 사용자 몇 명으로 일일 한도(1,000건, 관광빅데이터와
-- 공유)가 마른다. 월 1회 배치가 채우고 조회는 DB 만 읽는다.
--
-- 카테고리는 lclsSystm1(대분류)로 가른다 — 목록 응답에 이미 실려 오므로 추가 호출이 없다.
-- 판정 규칙은 Category enum 이 소유한다(필터칩과 같은 규칙이어야 개수와 목록이 안 어긋난다).
CREATE TABLE region_poi (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    region_id       BIGINT       NOT NULL,
    -- TourAPI contentId. 장소 상세(GET /pois/{contentId}) 로 그대로 이어진다.
    content_id      VARCHAR(64)  NOT NULL,
    content_type_id INT          NOT NULL,
    -- 필터칩 분류(SIGHT·STAY·EXPERIENCE·FOOD). lclsSystm1 에서 도출해 저장한다 —
    -- 조회할 때마다 다시 판정하면 규칙이 바뀌었을 때 저장분과 어긋난다.
    category        VARCHAR(20)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    -- 대표 사진. NULL 이 정상이다 — TourAPI 에 사진이 없는 장소가 있다.
    -- "매력 포인트 장소" 는 사진 있는 것만 담으므로 이 컬럼이 곧 노출 조건이다.
    image_url       VARCHAR(500),
    address         VARCHAR(300),
    lat             DOUBLE,
    lng             DOUBLE,
    tel             VARCHAR(100),
    -- 갱신 기준월(YYYYMM). 그 달치가 이미 있으면 외부를 아예 안 부른다 — hub_attraction 과 같은 방식.
    base_ym         CHAR(6)      NOT NULL,
    fetched_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);

-- 조회는 언제나 "이 지역의, 이 분류의, 사진 있는 장소" 다. 지역 상세와 홈 카드가 같은 모양으로 묻는다.
CREATE INDEX idx_region_poi_region_category ON region_poi (region_id, category);

-- 같은 지역에 같은 장소가 두 번 들어가지 않는다. 재적재가 멱등하려면 자연키에 유니크가 있어야 한다 —
-- 배치가 반쯤 돌다 죽어도 다시 돌리면 안 만들어진 것만 채운다.
CREATE UNIQUE INDEX ux_region_poi_natural ON region_poi (region_id, content_id);
