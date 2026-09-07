-- 실제로 물어본 구간을 배치가 먼저 재게 한다(#491).
--
-- 왜 필요했나 — 이 표의 원래 설계는 수요 기반이었다("쓰다가 필요해진 짝만 기억한다", 생성 마이그레이션).
-- 배치도 그 전제로 requested_at 이 오래된 것부터 집었다. 그런데 #450 이 부팅 시 후보 33,448건을 미리
-- 넣으면서 전부 같은 시각을 달았고, 그 뒤로 **사용자가 새로 요청한 구간이 3만 건 뒤에 서게 됐다.**
-- 배치가 시간당 50구간이라 28일이 걸린다 — 실제로 쓰이는 구간이 언제 채워질지 알 수 없는 상태였다.
--
-- requested_at 을 덮어쓰지 않고 컬럼을 새로 두는 이유 — "처음 물어본 때" 와 "마지막으로 물어본 때" 는
-- 다른 질문이다. 전자를 덮으면 이 구간이 얼마나 오래 값 없이 나가고 있었는지를 잃는다.
--
-- 시드로 들어간 행은 NULL 이다. 아무도 안 물어본 후보라는 뜻이고, 정렬에서 뒤로 간다.
ALTER TABLE transit_leg_duration ADD COLUMN last_asked_at DATETIME NULL;

-- 배치가 훑는 경로를 새 정렬에 맞춘다. 기존 idx_transit_leg_unmeasured 는 재측정 대상(measured_at 이
-- 채워진 행)을 고를 때 그대로 쓰이므로 남긴다.
CREATE INDEX idx_transit_leg_demand ON transit_leg_duration (measured_at, last_asked_at);
