-- 지방행정인허가 데이터에서 온 장소 풀(#144) — 숙박·맛집·볼거리 후보.
--
-- TourAPI 는 관광사업체 위주라 지방 숙소·식당이 빠진다. 의성군 숙박이 TourAPI 로는 1건인데 실제로는 52건이고,
-- 89곳 중 31곳이 2박3일 숙박 2곳을 못 채웠다. 분기 단위로만 바뀌는 레퍼런스 데이터를 매 요청 외부에서 끌어오지 않고
-- 여기 담아, 외부 한도가 소진되거나 포털이 점검에 들어가도 코스가 나가게 한다.
--
-- 데이터는 부팅 시 resources/data/place-pool.csv.gz 에서 적재한다(마이그레이션은 스키마만).
-- 89곳·영업중 기준 약 16만 건. FK 는 두지 않는다(persistence-convention).

CREATE TABLE licensed_place (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    region_id  BIGINT       NOT NULL,
    kind       VARCHAR(20)  NOT NULL,
    category   VARCHAR(40)  NOT NULL,
    name       VARCHAR(200) NOT NULL,
    address    VARCHAR(300),
    tel        VARCHAR(40),
    lat        DOUBLE       NOT NULL,
    lng        DOUBLE       NOT NULL,
    PRIMARY KEY (id)
);

-- 조회는 언제나 "이 지역의 이 종류" 다. 코스 생성이 지역당 세 풀을 각각 뽑는다.
CREATE INDEX idx_licensed_place_region_kind ON licensed_place (region_id, kind);

-- 재적재가 같은 장소를 다시 넣지 않도록 자연키에 유니크를 건다. 같은 건물에 상호가 같은 업소가
-- 둘일 수 없고, 원본 갱신으로 좌표만 미세하게 바뀌는 경우까지 중복으로 세지 않으려면 위치가 아니라
-- 지역·상호·주소로 묶는 것이 맞다.
CREATE UNIQUE INDEX ux_licensed_place_natural ON licensed_place (region_id, kind, name, address);
