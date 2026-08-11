-- 국가유산 풀(#160) — 볼거리 후보.
--
-- 인허가 데이터로 숙소·맛집은 채웠지만 볼거리는 얇고 야영장·골프장이 섞여 있었다. 국가유산은 그 자체가
-- 관광 자원이고, 인허가에는 없던 사진(96%)·설명(98%)이 함께 온다. 국가유산청 자체 API 라 관광 API
-- 일일 한도와도 무관하다.
--
-- 데이터는 부팅 시 resources/data/heritage-pool.csv.gz 에서 적재한다(마이그레이션은 스키마만).
-- 89곳 기준 6,392건을 수집해, 그중 대분류가 방문 가능한 것(유적건조물·자연유산·등록문화유산)만 싣는다.
-- 유물·기록유산·무형유산은 주소가 소장 기관·보유자 소재지라 목적지가 될 수 없다.
-- FK 는 두지 않는다(persistence-convention).

CREATE TABLE heritage_place (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    region_id   BIGINT       NOT NULL,
    kind        VARCHAR(40)  NOT NULL,
    group_code  VARCHAR(40)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    address     VARCHAR(300) NOT NULL,
    lat         DOUBLE       NOT NULL,
    lng         DOUBLE       NOT NULL,
    image_url   VARCHAR(500),
    description TEXT,
    PRIMARY KEY (id)
);

-- 조회는 언제나 "이 지역의 볼거리 후보" 다. 코스 생성이 지역 단위로 뽑는다.
CREATE INDEX idx_heritage_place_region ON heritage_place (region_id, group_code);

-- 재적재가 같은 유산을 다시 넣지 않도록 자연키에 유니크를 건다. 같은 지역·같은 이름·같은 소재지가
-- 둘일 수 없고, 좌표는 지오코딩으로 미세하게 바뀔 수 있어 자연키에 넣지 않는다.
CREATE UNIQUE INDEX ux_heritage_place_natural ON heritage_place (region_id, name, address);

-- 적재된 파일이 무엇인지 기록한다. 파일이 그대로면 부팅마다 6천 건을 다시 넣지 않는다.
-- 건수 비교로는 부족하다 — 갱신된 파일의 건수가 우연히 같으면 낡은 데이터가 그대로 남는다.
CREATE TABLE heritage_pool_source (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    checksum    CHAR(64)     NOT NULL,
    loaded_count INT         NOT NULL,
    loaded_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);
