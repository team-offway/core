-- 기기 푸시 토큰(#264) — 알림을 보낼 주소.
--
-- **유니크 제약을 소유 키가 아니라 token 에 건다.** 토큰이 기기의 신원이다. 앱을 지웠다 깔면 게스트 ID 는
-- 새로 발급되지만 FCM 토큰은 이어질 수 있고, 그때 같은 기기에 두 행이 생기면 같은 알림이 두 번 간다.
--
-- 이 제약이 곧 중복 등록의 방어선이다. 등록은 INSERT ... ON DUPLICATE KEY UPDATE 한 문장으로 나가,
-- "있나 보고 없으면 넣기" 가 동시 요청에서 깨지는 문제 자체가 생기지 않는다
-- (course_share 가 유니크 제약으로 발급 경합을 흡수한 것과 같은 결).
--
-- guest_id 에 FK 를 걸지 않는다(영속성 규약). 애초에 게스트를 담는 테이블이 없다.
CREATE TABLE device_push_token (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    -- 소유 키. 코스(course.guest_id)·연차(leave_balance.guest_id)와 같은 값·같은 길이를 쓴다.
    -- 인증이 붙는 날 함께 user_id 로 옮긴다.
    guest_id   VARCHAR(64)  NOT NULL,
    -- FCM 등록 토큰. 실제로는 160자 안팎이지만 규격이 길이를 못 박지 않아 여유를 둔다.
    -- 유니크 인덱스가 걸리는 칸이라 무한정 늘릴 수 없다 — utf8mb4 기준 2048바이트로
    -- InnoDB 인덱스 키 상한(3072바이트) 안이다.
    token      VARCHAR(512) NOT NULL,
    platform   VARCHAR(16)  NOT NULL,
    -- 처음 등록한 시각. 재등록해도 갱신하지 않는다.
    created_at DATETIME     NOT NULL,
    -- 마지막 등록 시각. 오래 조용한 토큰을 걷어낼 때 근거가 된다.
    updated_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_device_push_token_token UNIQUE (token),
    -- 해제(소유자 단위 삭제)와 발송 대상 조회가 쓰는 경로.
    KEY idx_device_push_token_owner (guest_id)
);
