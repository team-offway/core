-- 하루가 여행 시작일로부터 며칠 뒤인지 기록한다(#159).
--
-- 일정이 하나도 없는 날은 코스에서 빠진다(늦게 도착해 아무것도 못 하는 날). 그러면 둘째 날이
-- day 1 이 되는데, 날짜를 표시 번호로 계산하면 하루가 앞당겨진다 — 날씨도 함께 어긋난다.
-- 화면의 탭은 1·2·3 으로 이어지되 날짜는 이 컬럼을 따른다.
--
-- 기존 행은 표시 번호에서 도출한다(day_number - 1). 그때까지 저장된 코스는 빈 날을 담지 않았으므로
-- 두 값이 같다.

ALTER TABLE day_schedule ADD COLUMN day_offset INT NOT NULL DEFAULT 0;

UPDATE day_schedule SET day_offset = day_number - 1;
