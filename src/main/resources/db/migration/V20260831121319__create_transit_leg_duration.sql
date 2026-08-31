-- 버스·여객선 구간 소요시간 저장(#107 · #97).
--
-- 왜 저장하나 — 조회창이 좁다. 실측(2026-08-31) 결과 고속·시외버스는 오늘~+2일, 여객선은 오늘~+7일만
-- 배차를 답한다. 우리는 연차를 기준으로 다음 달 코스를 짜므로 요청 시점에 시간표를 물을 수 없다.
--
-- 그런데 소요시간에는 편차가 없다. 동서울→정선 시외버스 7편이 전부 150분·28,600원·우등이었고,
-- 포항→울릉_도동 여객선도 조회한 날마다 140분으로 같았다. 시간표는 못 써도 소요시간은
-- **한 번 재서 미래 날짜에 그대로 쓸 수 있다**.
--
-- 왜 미리 다 재지 않나 — 짝이 너무 많다. 터미널 789곳 × 항구 500곳이라 모든 조합은 수십만이다.
-- 그래서 unroutable_probe 와 같은 방식을 쓴다: **쓰다가 필요해진 짝만 기억한다.** 코스가 어떤 구간을
-- 물었는데 값이 없으면 이 표에 자리만 만들고(measured_at NULL), 배치가 나중에 채운다. 초기 적재가
-- 필요 없고 실제로 쓰이는 짝만 쌓이므로 자연히 필요한 만큼만 찬다.
--
-- minutes 가 NULL 인 채 measured_at 이 채워진 행은 "재봤더니 그 구간은 운행이 없다" 는 뜻이다.
-- 이것도 결과이므로 지우지 않는다 — 지우면 배치가 같은 구간을 영원히 다시 잰다.
CREATE TABLE transit_leg_duration (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mode VARCHAR(16) NOT NULL,          -- EXPRESS_BUS · INTERCITY_BUS · FERRY
    dep_code VARCHAR(16) NOT NULL,      -- 출발 터미널·항구 코드
    arr_code VARCHAR(16) NOT NULL,      -- 도착 터미널·항구 코드
    minutes INT,                        -- 소요시간(분). NULL = 미측정이거나 운행 없음
    charge INT,                         -- 요금(원)
    vehicle_name VARCHAR(64),           -- 등급·선명(우등 · 엘도라도익스프레스호)
    measured_at DATETIME,               -- 측정 시각. NULL 이면 아직 안 쟀다(배치 대상)
    requested_at DATETIME NOT NULL,     -- 코스가 처음 물어본 시각
    CONSTRAINT uk_transit_leg UNIQUE (mode, dep_code, arr_code)
);

-- 배치가 "아직 안 잰 것" 을 훑는 경로. 오래 기다린 것부터 채운다.
CREATE INDEX idx_transit_leg_unmeasured ON transit_leg_duration (measured_at, requested_at);
