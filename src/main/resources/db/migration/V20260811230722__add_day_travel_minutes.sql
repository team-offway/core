-- Day 사이 이동시간(#188).
--
-- 거리는 좌표만 있으면 응답 시점에 계산되지만 이동시간은 실도로 경로라 외부 호출이 필요하다. 요청 경로에
-- 외부 I/O 를 넣지 않는다는 규약대로, 슬롯 이동시간과 똑같이 생성 시점에 받아 둔다.
--
-- 첫날과 이 컬럼이 생기기 전 코스는 null 이다.

ALTER TABLE day_schedule ADD COLUMN travel_minutes_from_prev_day INT;
