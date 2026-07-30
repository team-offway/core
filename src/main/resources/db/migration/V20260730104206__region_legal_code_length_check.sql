-- region.legal_code 를 정확히 5자리로 굳힌다.
--
-- 선행 V20260730085345 는 VARCHAR(5) NOT NULL 까지만 걸었는데, 그건 1~4자리도 허용한다.
-- 짧은 코드는 관광빅데이터 방문자 매칭에서 조용히 빗나가 — 예외도 경고도 없이 순위만 틀어진다.
-- 그 마이그레이션은 이미 적용됐으므로 수정하지 않고(checksum·forward-only) 여기서 보정한다.
--
-- 길이로 잡히지 않는 것: 값이 '틀린' 경우(행정구역 개편으로 코드가 낡는 등)는 5자리를 만족하므로
-- 이 제약을 통과한다. 그쪽은 TourDataLabClientE2ETest 의 89곳 전수 검증이 담당한다.
--
-- MySQL / H2(MODE=MySQL) 호환. FK 없음.

ALTER TABLE region
    ADD CONSTRAINT chk_region_legal_code_length CHECK (CHAR_LENGTH(legal_code) = 5);
