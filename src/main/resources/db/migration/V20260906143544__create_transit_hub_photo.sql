-- 역·터미널·항구 칸의 사진(#450).
--
-- 대중교통 코스는 교통 거점으로 시작해 교통 거점으로 끝나는데(#415), 그 칸이 사진·주소 없이 나갔다.
-- 장소 상세 키(poiContentId)가 없어서다 — TourAPI 장소가 아니라 TAGO 터미널이라 물을 키가 없었다.
--
-- **갤러리는 키가 아니라 말로 찾는다.** 관광사진갤러리의 gallerySearchList1 은 제목·촬영지·검색어를
-- 함께 보므로 "강릉역" 같은 지점명이 그대로 통한다.
--
-- **미리 받아 둔다.** 코스는 도착 지역부터 보여주므로 필요한 지점은 인구감소지역 89곳이 쓰는 것뿐이고,
-- 그 집합은 시드가 정한 유한 집합이다(버스만 110종, 열차역·항구까지 200종 남짓). 요청 경로에서 외부를
-- 부르지 않는다는 규칙 그대로다.
--
-- 이름을 키로 쓴다. 같은 지점이 고속·시외 목록에 다른 코드로 올라 있어(NAEK/NAI) 코드로 잡으면 같은
-- 사진을 두 번 받는다.
CREATE TABLE transit_hub_photo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hub_name VARCHAR(64) NOT NULL,       -- 지점명(역·터미널·항구). 검색어이자 키다
    image_url VARCHAR(500),              -- 못 찾았으면 NULL — "안 물어봄" 과 구분하려고 행은 남긴다
    photographer VARCHAR(100),           -- 갤러리가 주는 촬영자. 출처 표기에 쓴다
    title VARCHAR(300),                  -- 사진 제목
    fetched_at DATETIME NOT NULL,        -- 마지막으로 물어본 시각. 배치가 오래된 것부터 다시 잰다
    CONSTRAINT uk_transit_hub_photo_name UNIQUE (hub_name)
);
