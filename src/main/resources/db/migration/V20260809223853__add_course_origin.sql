-- 저장 코스의 출발지 — 대중교통 열차 접근을 다시 계산하려면 필요하다(#187).
--
-- 생성 때는 "서울역 → 공주역 KTX 1시간 20분" 이 실리는데, 저장하고 다시 열면 비어 있었다.
-- 다시 계산하려 해도 <b>어디서 출발하는지를 저장하지 않아</b> 근거 자체가 없었다.
--
-- <b>계산 결과가 아니라 입력을 저장한다.</b> 열차 시간표는 바뀌므로, 한 달 뒤 여행에 생성 시점의
-- 시간을 그대로 보여주면 사용자가 열차를 놓친다 — 없는 것보다 나쁘다. 출발지는 코스 날짜 수정(#170)
-- 에도 필요하다.
--
-- NULL 을 허용한다 — 이 필드가 생기기 전에 저장된 코스와 자차 코스는 출발지가 없다.
--
-- MySQL / H2(MODE=MySQL) 호환. additive 라 순서 무관.

ALTER TABLE course ADD COLUMN origin_lat DOUBLE NULL;
ALTER TABLE course ADD COLUMN origin_lng DOUBLE NULL;
