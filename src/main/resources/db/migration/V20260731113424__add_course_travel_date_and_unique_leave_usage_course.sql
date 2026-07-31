-- 코스 확정 시 연차 차감(#91) 을 위한 두 가지.
-- MySQL / H2(MODE=MySQL) 호환. FK 없음.

-- (1) 저장 코스의 여행 시작일.
--
-- 지금까지 저장 코스는 travel_days(3일) 만 갖고 "언제 가는지" 를 버렸다. 그래서 차감 일수(평일−공휴일)를 서버가
-- 다시 계산할 근거가 서버에 없었다. 클라이언트가 차감할 때 날짜를 같이 보내는 방법도 있지만, 그러면 날짜를 늘려
-- 보내는 만큼 차감량이 바뀌어 "클라 입력 불신" 이 반쪽이 된다.
--
-- NULL 을 허용하는 이유는 이 컬럼이 생기기 전에 저장된 코스가 있기 때문이다. 날짜 없는 코스는 차감 요청 시 400 이다.
ALTER TABLE course ADD COLUMN travel_date DATE;

-- (2) 코스당 차감 내역 하나 — 중복 차감을 DB 가 막는다.
--
-- 애플리케이션의 "이미 차감했나" 확인만으로는 동시 요청(더블클릭)이 둘 다 통과해 두 번 차감된다. 유니크 제약이
-- 유일하게 경합에 안전한 방법이다. 수동 입력은 course_id 가 NULL 이고 MySQL·H2 모두 NULL 은 중복을 허용하므로
-- 제약에 걸리지 않는다.
--
-- 이 제약은 "코스 차감의 취소는 음수 행을 덧붙이는 게 아니라 그 행을 되돌린다" 를 뜻한다. 음수 행 누적 방식은
-- 수동 내역에만 해당한다.
--
-- 기존 idx_leave_usage_guest_course 는 같은 컬럼·같은 순서라 새 유니크 인덱스가 그대로 대체한다(조회 경로 동일).
DROP INDEX idx_leave_usage_guest_course ON leave_usage;
CREATE UNIQUE INDEX uk_leave_usage_guest_course ON leave_usage (guest_id, course_id);
