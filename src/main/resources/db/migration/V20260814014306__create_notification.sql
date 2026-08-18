-- 사용자 알림(#263) — 알림 화면의 목록과 홈 배지가 읽는 정본.
--
-- **문구 컬럼을 두지 않는다.** 종류(type)만 저장하고 화면 문구는 앱이 만든다. 문구를 여기 굳혀 두면
-- 이미 쌓인 행은 영영 옛 문구로 남아, 앱이 표현을 고칠 때 화면에 두 세대가 섞인다.
--
-- **읽음을 boolean 이 아니라 시각으로 둔다.** 저장 비용이 같은데 "읽었다" 외에 "언제 읽었나" 까지 답한다.
--
-- course_id 에 FK 를 걸지 않는다(영속성 규약). 코스가 지워져도 알림은 남아야 하므로 참조 무결성이
-- 목적에 어긋난다 — 지워진 코스를 가리키는 알림은 눌러도 코스가 없을 뿐이다.
CREATE TABLE notification (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    -- 소유 키. 코스(course.guest_id)·연차(leave_balance.guest_id)와 같은 값·같은 길이를 쓴다.
    -- 인증이 붙는 날 세 테이블을 함께 user_id 로 옮긴다.
    guest_id   VARCHAR(64) NOT NULL,
    -- enum 이름. ordinal 로 저장하면 상수를 재배치하는 순간 이미 저장된 행의 뜻이 통째로 바뀐다.
    type       VARCHAR(40) NOT NULL,
    -- 누르면 이동할 코스. 코스와 무관한 알림도 생길 수 있어 NULL 을 허용한다.
    course_id  BIGINT      NULL,
    -- NULL 이면 안 읽음.
    read_at    DATETIME    NULL,
    created_at DATETIME    NOT NULL,
    PRIMARY KEY (id),
    -- 목록은 소유자 안에서 최신순으로만 읽는다. 안읽음 개수도 이 인덱스로 소유자 범위까지 좁힌 뒤
    -- read_at 을 훑는다 — 한 사람의 알림은 수백 건 규모라 별도 인덱스를 더 두지 않는다.
    KEY idx_notification_owner (guest_id, created_at)
);
