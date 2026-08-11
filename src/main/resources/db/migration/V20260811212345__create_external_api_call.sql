-- 외부 API 오늘자 호출량(#123).
--
-- 인메모리로 두면 재시작마다 0 이 되어 실제보다 여유 있게 보인다. 틀릴 거면 위험한 방향으로 틀리면 안 된다.
-- 배포가 잦은 날일수록 실제 소진에 가까운데 화면은 깨끗해지는, 정확히 반대 방향의 오차가 난다.
--
-- 리셋 경계는 KST 00:00 이다. call_date 가 키라 자정을 넘기면 자연히 새 행이 된다.

CREATE TABLE external_api_call (
    call_date  DATE        NOT NULL,
    api        VARCHAR(40) NOT NULL,
    call_count BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (call_date, api)
);
