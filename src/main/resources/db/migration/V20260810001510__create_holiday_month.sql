-- 공휴일(특일정보) 월 단위 적재 — #193 3단계.
--
-- 왜 DB 인가: 인메모리 캐시(ExternalDataCache)는 프로세스와 함께 죽는다. 공휴일은 이 시스템에서 가장
-- 정적인 데이터인데(연 단위로 미리 공표되고 확정되면 안 바뀐다) 배포할 때마다 처음부터 다시 물었다.
--
-- 행 단위를 '달' 로 잡은 이유: 공휴일이 아예 없는 달은 정상이고 흔하다(4·6·11월). 날짜만 저장하면
-- "공휴일이 없는 달" 과 "아직 안 받아온 달" 이 똑같이 '행 없음' 이 되어 구분되지 않는다. 후자를 전자로
-- 오인하면 공휴일이 평일로 세어져 소모 연차가 과다 계산된다 — 조용히 틀리는 쪽이라 가장 나쁘다.
--
-- FK 없음(영속성 규약).

CREATE TABLE holiday_month (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    -- 기준 연월(YYYYMM). hub_attraction.base_ym 과 같은 표기를 쓴다.
    base_ym    CHAR(6)      NOT NULL,
    -- 그 달의 공휴일을 'YYYY-MM-DD' 로 쉼표에 이어 둔다. 공휴일이 없는 달은 빈 문자열이다.
    --
    -- 별도 테이블로 나누지 않는 이유: 한 달에 많아야 열 건 남짓이고, 읽기는 언제나 '달 단위 전부' 라
    -- 조인이 늘 뿐 얻는 것이 없다(region_content.categories 와 같은 판단).
    -- 길이 상한: 11자 × 30일치 여유.
    holidays   VARCHAR(400) NOT NULL,
    updated_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    -- 달당 한 행. 갱신은 달 단위 교체라 이 제약이 중복 적재를 막는다.
    CONSTRAINT uk_holiday_month_base_ym UNIQUE (base_ym)
);
