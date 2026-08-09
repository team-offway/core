-- 관광빅데이터 방문자 집계를 DB 로 내린다(#193).
--
-- 인메모리 캐시라 배포할 때마다 날아갔고, 그때마다 관광빅데이터를 처음부터 다시 긁었다.
-- 배포가 하루 스무 번이면 그만큼 API 일일 한도(1,000건)를 태운다.
--
-- 원본은 **완결된 달만 월 단위로** 발행된다. 하루 네 번(6h TTL) 물어볼 이유가 없다.
CREATE TABLE region_visitor_aggregate (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    -- 법정 시군구코드. 지명으로 집계하면 전국 동구 6곳이 한 버킷에 합산된다.
    signgu_code   VARCHAR(10)  NOT NULL,
    -- 집계 기준 연월(YYYYMM). 어느 달 데이터인지 알아야 갱신 필요 여부를 판단한다.
    base_ym       CHAR(6)      NOT NULL,
    -- 관측 창 안의 관광객(외지인+외국인) 합.
    visitor_total DOUBLE       NOT NULL,
    -- 관측된 distinct 일자 수. 베이지안 보정의 표본 크기다.
    observed_days INT          NOT NULL,
    updated_at    DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_region_visitor_aggregate_signgu (signgu_code)
);
