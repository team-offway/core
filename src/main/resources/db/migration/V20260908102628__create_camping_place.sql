-- 고캠핑 야영장 (#510) — 숙박 후보.
--
-- ## 왜 필요한가
--
-- **숙박 카드가 빈다. 후보 수가 모자라서가 아니라 사진이 없어서다.**
--
-- 인허가 숙박(licensed_place)은 지역당 평균 296곳이나 되지만 **사진 컬럼이 아예 없다** — 이름·주소·전화뿐이라
-- 코스에 올라가도 화면이 회색 판이 된다. 사진이 있는 숙박은 TourAPI 쪽 922건뿐이고, 89곳으로 나누면
-- **지역당 열두 곳 남짓**이다.
--
-- 고캠핑(15101933)을 우리 89곳으로 재면 운영 중인 것이 1,702건이고, 좌표가 99.8%(1,698건)
-- 사진이 75%(1,288건) 온다. 87곳이 덮인다.
--
-- ## 겹침을 먼저 쟀다
--
-- TourAPI 숙박에는 이미 야영장이 섞여 있다 — RegionPoiService 가 남긴 실측이 "AC(숙박) 977건 중 625건이
-- 타입 28(레포츠)이고 전부 야영장·캠핑장·펜션" 이었다. 그래서 새로 얻는 것만 따로 셌다(2026-09-08, 운영 DB 대조).
-- 이름이 같거나 좌표가 200m 이내면 같은 곳으로 봤다.
--
--   겹침 375건  →  **순증 1,323건 (그중 사진 964건)**
--
-- 사진 있는 숙박 후보가 922 → 1,886건으로 **2.05배**가 된다.
--
-- 두 출처의 contentId 체계가 달라(교집합 0) id 로는 못 가른다. 런타임 dedupe 는 RegionPois.identity()
-- (이름 + 100m 격자)가 이미 하므로 여기서 또 만들지 않는다.
--
-- ## 자연키는 고캠핑 contentId
--
-- 매월 재적재하는 표라 같은 야영장이 두 번 들어오면 안 된다. 고캠핑이 주는 contentId 가 그 야영장을
-- 가리키는 안정된 키다 — 이름·주소는 지자체가 고쳐 올리지만 이 값은 유지된다.
--
-- 우리 id 를 자연키로 쓸 수 없는 이유는 축제(#433)와 같다. 재적재마다 id 가 바뀌면 'CMP-{id}' 가
-- **다른 야영장을 가리킨다** — 코스에 실어 보낸 식별자가 엉뚱한 곳의 상세를 연다.
--
-- ## 휴장은 담지 않는다
--
-- manageSttus 가 '운영' 2,992건 · '휴장' 123건이다. 휴장한 야영장을 코스에 넣으면 헛걸음이라 적재
-- 단계에서 거른다. 상태 컬럼을 두지 않는 것도 그래서다 — 담기지 않은 것은 운영 중인 것뿐이다.
--
-- ## 크기
--
-- 89곳 1,698건이다. 소개글(TEXT)을 얹어도 1MB 안쪽으로, 지금 DB 51MB 에 영향이 없다.

CREATE TABLE camping_place (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    region_id     BIGINT       NOT NULL COMMENT '인구감소지역 89곳 중 하나 (raw 참조)',
    -- 고캠핑이 주는 야영장 식별자. 우리 id 와 달리 재적재해도 같은 곳을 가리킨다.
    external_id   VARCHAR(50)  NOT NULL COMMENT '고캠핑 contentId — 자연키',
    name          VARCHAR(200) NOT NULL COMMENT '야영장명 (facltNm)',
    address       VARCHAR(300) NOT NULL COMMENT '소재지 도로명주소 (addr1)',
    -- 좌표가 없으면 동선에 못 올려 코스에 쓸 수 없다. 적재 단계에서 거르고 여기서도 NOT NULL 로 막는다.
    lat           DOUBLE       NOT NULL,
    lng           DOUBLE       NOT NULL,
    -- 업종이 곧 뱃지다 — `일반야영장`·`자동차야영장`·`글램핑`·`카라반`. 둘 이상이면 쉼표로 이어 온다.
    induty        VARCHAR(100)          COMMENT '업종 (induty)',
    image_url     VARCHAR(500)          COMMENT '대표사진 — 74% 가 채워진다. 이 컬럼이 이 표의 존재 이유다',
    line_intro    VARCHAR(500)          COMMENT '한 줄 소개 (lineIntro) — 카드 캐치프레이즈 자리',
    intro         TEXT                  COMMENT '소개글 (intro)',
    tel           VARCHAR(50),
    homepage_url  VARCHAR(500),
    -- 상세의 "언제 여나" 에 답한다. 인허가·국가유산·축제는 이 값이 없어 지도로 넘겼다.
    oper_period   VARCHAR(200)          COMMENT '운영기간 (operPdCl)',
    oper_days     VARCHAR(200)          COMMENT '운영일 (operDeCl)',
    reservation   VARCHAR(200)          COMMENT '예약방법 (resveCl)',
    fetched_at    DATETIME     NOT NULL,
    PRIMARY KEY (id),
    -- 매월 재적재가 같은 야영장을 두 번 넣지 못하게 막는다.
    UNIQUE KEY uk_camping_place_external (external_id),
    -- "그 지역의 야영장" 이 실제 조회 모양이다. 사진 있는 것을 앞세우는 정렬은 지역당 최대 199건이라
    -- 인덱스 없이도 충분하다.
    KEY idx_camping_place_region (region_id)
);
