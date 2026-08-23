-- 홈 장소 카드의 부제 재료를 저장한다(#305).
--
-- **받아서 쓰고 버리고 있었다.** TourApiClientImpl 이 detailIntro2 에서 카테고리별 필드를 뽑아 TourIntro 로
-- 들고 있고 도메인 PoiIntro 도 열 개를 다 갖고 있는데, 테이블은 use_time·rest_date 둘뿐이었다. 그래서
-- 홈이 부제를 쓰려면 장소마다 외부를 다시 불러야 했다 — 카드 10장이면 10콜이고, 사용자 100명이면
-- 하루 한도가 마른다.
--
-- **부제는 카테고리마다 다른 필드에서 온다.** 맛집은 대표메뉴, 숙박은 객실 수와 입실 시각, 체험은
-- 이용요금이다. 한 자리에 모아 두고 무엇을 쓸지는 Category 가 정한다.
--
-- 길이는 use_time·rest_date 와 같은 500 으로 맞춘다. 외부가 "※ 모든 체험은 사전 예약제로 운영되므로
-- 방문 전 예약 필수" 같은 안내문을 그 칸에 그대로 실어 보내는 것을 실측에서 봤다.
--
-- ADD COLUMN 만이라 순서에 무관하다(out-of-order 안전).

ALTER TABLE poi_intro
    -- 주차 안내(관광지·문화시설·레포츠). "가능" 처럼 짧게 오기도 하고 요금 안내가 붙기도 한다.
    ADD COLUMN parking        VARCHAR(500),
    -- 이용요금(문화시설·레포츠·체험). 관광지에는 이 필드가 아예 없다.
    ADD COLUMN fee            VARCHAR(500),
    -- 대표메뉴(음식점). 실측 표본 30건에서 100% 채워졌다 — 맛집 부제가 이것이다.
    ADD COLUMN signature_menu VARCHAR(500),
    -- 취급메뉴(음식점). 슬래시로 이어진 한 줄로 온다.
    ADD COLUMN menus          VARCHAR(500),
    -- 입실·퇴실 시각(숙박).
    ADD COLUMN check_in       VARCHAR(100),
    ADD COLUMN check_out      VARCHAR(100),
    -- 객실 수(숙박). `13실` 로 오기도 하고 `13` 으로 오기도 해 문자열로 둔다.
    ADD COLUMN room_count     VARCHAR(100),
    -- 예약 안내(숙박).
    ADD COLUMN reservation    VARCHAR(500);
