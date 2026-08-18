-- 외부 API 호출을 누가 태웠는지(#285).
--
-- 총량(external_api_call)과 따로 둔다. 그쪽 PK 를 (call_date, api, caller) 로 넓히면 #257 이 얹은
-- notified_step 이 주체별로 쪼개져 같은 10% 단계를 주체 수만큼 알린다. 알림을 늘리지 않는 것이
-- 이 작업의 전제라 총량 테이블은 손대지 않는다.
--
-- caller 키 공간은 유한하다 — 배치 5 + 엔드포인트 패턴 약 20. 경로가 아니라 패턴을 쓰기 때문이다.
-- 경로를 쓰면 /courses/123 처럼 id 마다 행이 생겨 상한이 없어진다.

CREATE TABLE external_api_call_caller (
    call_date  DATE        NOT NULL,
    api        VARCHAR(40) NOT NULL,
    caller     VARCHAR(80) NOT NULL,
    call_count BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (call_date, api, caller)
);
