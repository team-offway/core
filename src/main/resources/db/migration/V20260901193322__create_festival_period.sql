-- 축제가 언제 열리는지 (#388).
--
-- ## 왜 필요한가
--
-- **우리는 이미 축제를 코스에 넣고 있다.** RegionPoiService 의 볼거리 풀이 contentTypeId 15(축제공연행사)를
-- 포함하는데, 그 행에는 기간이 없다. 그래서 **3월에 끝난 벚꽃축제가 9월 코스에 들어갈 수 있다** — 이름만
-- 보면 그럴듯해서 화면에서도 안 드러나고, 사용자가 현장에 가서야 안다.
--
-- 같은 데이터가 반대로 기능이 되기도 한다. 기간을 알면 "9월 12일에 가시면 장보고축제를 볼 수 있습니다"
-- 가 된다.
--
-- ## 왜 region_poi 에 컬럼을 더하지 않았나
--
-- region_poi 는 **매월 base_ym 단위로 다시 채워진다**(RegionPoiRefreshService). 거기에 두면 갱신 주기가
-- 장소 풀에 묶여, 축제 날짜가 바뀌어도 다음 달까지 못 고친다. 축제는 날짜가 바뀌고 취소된다.
--
-- poi_intro(운영시간·휴무일)가 같은 이유로 content_id 를 키로 따로 서 있다. 그 선례를 따른다 —
-- **contentId 별 외부 상세는 자기 주기로 갱신한다.**
--
-- ## 날짜를 모르는 축제
--
-- TourAPI 가 날짜를 안 주는 행이 있다. 그때는 **행을 만들지 않는다** — 없는 것과 "모른다" 를 구분하려는
-- 것이고, 모르는 것을 끝났다고 단정하지 않기 위해서다(#220 의 checked_on 과 같은 판단). 조회에서 행이
-- 없으면 지금처럼 평범한 볼거리로 남는다.
--
-- ## 인덱스
--
-- 조회는 언제나 "이 contentId 들이 이 날짜에 열리나" 라 PK 로 들어간다. 종료일 인덱스는 낡은 행을
-- 걷어내는 정리 작업이 생길 때 함께 본다 — 지금 만들면 쓰지 않는 인덱스가 된다.

CREATE TABLE festival_period (
    content_id  VARCHAR(64) NOT NULL COMMENT 'TourAPI contentId — region_poi 와 같은 키',
    event_start DATE        NOT NULL COMMENT '행사 시작일',
    event_end   DATE        NOT NULL COMMENT '행사 종료일',
    title       VARCHAR(200) NULL COMMENT '조회 당시 축제명. 사람이 로그를 읽을 때 쓴다',
    fetched_at  DATETIME(6) NOT NULL COMMENT '마지막으로 받아 온 시각',
    PRIMARY KEY (content_id)
);
