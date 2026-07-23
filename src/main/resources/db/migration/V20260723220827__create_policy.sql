-- policy: 7대 여행 지원 혜택의 구체 인스턴스(정책명·기간·대상·신청링크). 수동 적재(전용 API 없음).
-- 지역 매칭은 PolicyType.targetTag ↔ region_tag.tag 로 이뤄지므로 policy 는 지역 컬럼을 갖지 않는다.
-- MySQL / H2(MODE=MySQL) 호환. FK 없음.
CREATE TABLE policy (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    type            VARCHAR(40)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    benefit_detail  VARCHAR(500),
    target_audience VARCHAR(200),
    period_start    DATE,
    period_end      DATE,
    apply_url       VARCHAR(500),
    verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id)
);

CREATE INDEX idx_policy_type ON policy (type);
