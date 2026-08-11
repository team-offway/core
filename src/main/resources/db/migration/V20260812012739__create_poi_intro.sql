-- 장소 운영시간·휴무일 캐시(#157).
--
-- **왜 테이블인가.** 슬롯마다 detailIntro2 를 부르면 코스 하나에 20건이 넘는다. 관광정보 한도가 하루
-- 1,000이라 코스 40개면 마른다. 운영시간은 콘텐츠에 붙는 값이고 거의 안 변하므로, 콘텐츠당 한 번 받아
-- 두면 같은 장소가 여러 코스에 나와도 다시 부르지 않는다.
--
-- 채우는 것은 배치다. 코스에 실제로 쓰인 콘텐츠(slot.poi_content_id)를 일감 목록으로 삼아 하루 예산만큼
-- 메운다. 화면은 있으면 보여주고 없으면 그 줄을 지운다 — 처음엔 비다가 며칠에 걸쳐 찬다.
--
-- fetched_at 은 나중에 오래된 것을 다시 받을 때 쓴다. 지금은 한 번 받으면 그대로 둔다.

CREATE TABLE poi_intro (
    content_id      VARCHAR(64) NOT NULL,
    content_type_id INT         NOT NULL,
    use_time        VARCHAR(500),
    rest_date       VARCHAR(500),
    fetched_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (content_id)
);

-- 슬롯이 콘텐츠 타입을 들고 있어야 배치가 detailIntro2 를 부를 수 있다. 타입마다 필드명이 달라
-- (usetime·usetimeculture·opentimefood…) 타입 없이는 무엇을 읽을지 정할 수 없다.
--
-- 이 컬럼이 생기기 전 슬롯은 null 이라 배치가 건너뛴다. 새로 만드는 코스부터 채워진다.
ALTER TABLE slot ADD COLUMN poi_content_type_id INT;
