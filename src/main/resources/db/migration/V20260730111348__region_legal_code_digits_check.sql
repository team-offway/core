-- region.legal_code 를 '숫자' 5자리로 굳힌다.
--
-- 선행 V20260730104206 의 CHAR_LENGTH(legal_code) = 5 는 'ABCDE' 같은 값을 통과시킨다.
-- 코드가 숫자가 아니면 관광빅데이터 signguCode 와 절대 매칭되지 않아, 그 지역은 관측일수 0이 되고
-- 베이지안 prior 로 전국 평균 점수를 받는다 — 예외도 경고도 없이 순위만 틀어진다.
--
-- 선행 제약을 DROP 하지 않는다: Flyway out-of-order 가 켜져 있어(persistence-convention) 이 파일이
-- 선행보다 먼저 적용되면 DROP 이 없는 제약을 지우려다 실패한다. 아래 제약이 길이까지 포함해 더 강하므로
-- 선행은 중복이 될 뿐 틀리지 않는다.
--
-- REGEXP_LIKE 는 MySQL 8.0.16+ CHECK 와 H2 양쪽에서 동작한다(결정적 내장 함수).
-- MySQL / H2(MODE=MySQL) 호환. FK 없음.

ALTER TABLE region
    ADD CONSTRAINT chk_region_legal_code_digits CHECK (REGEXP_LIKE(legal_code, '^[0-9]{5}$'));
