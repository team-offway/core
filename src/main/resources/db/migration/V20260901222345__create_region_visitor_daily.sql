-- 지역 일별 방문자 (#394) — 혼잡도의 재료.
--
-- ## 무엇을 버리고 있었나
--
-- locgoRegnVisitrDDList 는 **시군구별·일별·방문자유형별**로 준다. 그런데 저장할 때 한 달 한 줄로 뭉갠
-- 뒤(RegionVisitorAggregate) 지난달을 지웠다(replaceAll). 날짜·유형·이력이 전부 사라져서,
-- **요일 패턴도 계절성도 낼 수 없었다.**
--
-- 여행 앱에서 알고 싶은 것은 "지금 몇 명" 이 아니라 **"내가 가는 토요일에 붐비나"** 다. 그 답의 재료가
-- 이미 응답에 실려 오고 있었다.
--
-- ## 왜 집계 표를 안 고치고 새로 만드나
--
-- region_visitor_aggregate 의 observed_days 가 랭킹의 **베이지안 prior** 에 들어간다(RegionRanking).
-- 그 표의 관측 창을 넓히면 순위가 조용히 바뀐다 — 혼잡도를 고치려다 추천 순서를 흔드는 셈이다.
--
-- 두 관심사를 갈라 둔다. 랭킹은 지금 그대로(마지막 7일 표본), 혼잡도는 이 표를 본다.
--
-- ## 89곳만 넣는다
--
-- 원본은 전국 229곳을 준다. 우리가 쓰는 것은 89곳뿐이라 받아서 거르면 저장량이 2.6배 줄어든다.
--
-- ## 한 번 쌓으면 다시 안 쌓는다
--
-- 원본은 **완결된 달만 월 단위로 발행**되므로 지난달 값은 불변이다. PK 가 (시군구·날짜·유형)이라
-- 같은 행을 다시 넣으려 해도 걸린다 — 적재기는 없는 것만 넣고, 이미 있는 달은 외부를 부르지도 않는다.
--
-- ## 크기
--
-- 89곳 × 30일 × 3유형 × 12개월 = 약 96,000행. 행이 좁아(코드 10 + 날짜 3 + 유형 20 + 수치 8)
-- 약 8MB 로 추산한다 — 지금 DB 51MB 에 얹는 값이다. **추산이라 배포 전에 실측한다.**

CREATE TABLE region_visitor_daily (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    signgu_code   VARCHAR(10) NOT NULL COMMENT '법정 시군구코드 — region.signgu_code 와 같은 키',
    base_date     DATE        NOT NULL COMMENT '기준일자. 요일은 여기서 파생한다',
    visitor_type  VARCHAR(20) NOT NULL COMMENT '현지인·외지인·외국인. 관광 혼잡도는 외지인이 답이다',
    visitor_count DOUBLE      NOT NULL COMMENT '방문자수',
    PRIMARY KEY (id),
    -- **같은 날을 두 번 넣지 못하게 하는 것이 이 표의 핵심 불변식이다.** 원본이 완결된 달만 발행하므로
    -- 값은 불변인데, 백필과 월별 적재가 겹치면 같은 행이 두 번 들어가 방문자가 두 배로 보인다.
    --
    -- 복합 PK 대신 대리 키 + UNIQUE 를 쓴 것은 이 레포의 방식에 맞춘 것이다(curated_link·policy 도 그렇다).
    -- 보장은 같고 JPA 매핑이 단순해진다.
    UNIQUE KEY uk_region_visitor_daily (signgu_code, base_date, visitor_type),
    -- "이 지역의 이 기간" 이 유일한 조회 모양이라 위 UNIQUE 의 앞자리가 그대로 그 인덱스다.
    -- 날짜만으로 훑는 질의는 지금 없어 별도 인덱스를 두지 않는다 — 쓰지 않을 인덱스는 적재만 늦춘다.
    KEY idx_region_visitor_daily_date (base_date)
);
