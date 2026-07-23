-- region_tag: 지역에 붙는 라벨(정책 매칭·필터용). 정책이 늘어도 컬럼을 추가하지 않고 tag 행만 INSERT 한다.
-- MySQL / H2(MODE=MySQL) 호환. FK 없음(참조 무결성은 서비스 계층 책임).
CREATE TABLE region_tag (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    region_id BIGINT      NOT NULL,
    tag       VARCHAR(40) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_region_tag UNIQUE (region_id, tag)
);

-- 조회 인덱스: tag → 지역(정책 상세의 "이 혜택 되는 여행지"), region → tag(지역 카드 뱃지)
CREATE INDEX idx_region_tag_tag ON region_tag (tag);
CREATE INDEX idx_region_tag_region_id ON region_tag (region_id);

-- POPULATION_DECLINE: 현재 89 전부가 인구감소지역. 나중에 다른 유형 지역이 추가되면 이 태그가 구분자가 된다.
-- 프로그램별 태그(디지털관광주민증·반값여행 등)는 정책 시딩 때 각 정책의 실제 대상 지역과 함께 additive 로 추가한다.
INSERT INTO region_tag (region_id, tag)
SELECT id, 'POPULATION_DECLINE' FROM region;
