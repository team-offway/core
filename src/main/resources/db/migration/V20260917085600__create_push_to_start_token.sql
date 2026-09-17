-- 잠금화면 카드를 **띄울 수 있는** 주소(#583) — push-to-start.
--
-- **live_activity_token 과 무엇이 다른가.** 가리키는 대상이 다르다.
--
--   live_activity_token   (사용자, 코스)  이미 떠 있는 *카드 하나*   카드가 사라지면 죽는다
--   push_to_start_token   (사용자, 기기)  그 *기기*                  앱을 지울 때까지 산다
--
-- 그래서 이 토큰은 카드를 한 번도 띄운 적이 없어도 발급된다. 서버가 **처음으로** 카드를 만드는 데
-- 쓰는 것이 이것이다.
--
-- **왜 필요했나.** iOS 는 Live Activity 를 최대 8시간 뒤 스스로 끝내고, 카드를 처음 띄우는 일은
-- 지금까지 앱만 할 수 있었다. 그래서 (1) 출발 5일 전부터 띄우기로 해 놓고도 그 5일 동안 앱을 안 연
-- 사람은 끝내 못 봤고, (2) 자정 갱신(#577)이 실제로 먹는 것은 그날 16시 이후에 앱을 연 경우뿐이었다.
--
-- **유니크는 (user_id, token).** 한 사람이 폰과 태블릿을 쓰면 행이 둘이고 둘 다에 띄운다. 앱은 시작할
-- 때마다 같은 토큰을 다시 보내므로 등록이 멱등이어야 하고, 그 판정은 제약을 쥔 DB 가 한 문장
-- (INSERT ... ON DUPLICATE KEY UPDATE) 안에서 한다.
--
-- **토큰 단독 유니크가 아니다.** 그렇게 걸면 같은 토큰이 다른 소유자로 왔을 때 주인을 갈아끼우게 되고,
-- 남의 토큰을 아는 쪽이 그것을 자기 것으로 등록해 상대의 카드를 가로챌 수 있다
-- (device_push_token 이 #264 에서 같은 이유로 복합 키다).
--
-- user_id 에 FK 를 걸지 않는다(영속성 규약). 탈퇴 정리는 UserWithdrawn 리스너가 한다 — 남으면
-- 배치가 매일 그 기기에 카드를 만든다.
CREATE TABLE push_to_start_token (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    -- 주인. 코스·연차·알림과 같은 소유 키다(#280). 등록 본문이 아니라 access 토큰이 확인한 값이 들어간다.
    user_id    BINARY(16)   NOT NULL,
    -- APNs push-to-start 토큰. hex 문자열이고 실제로는 160자 안팎이지만 규격이 길이를 못 박지 않아
    -- 여유를 둔다. 유니크 인덱스가 걸리는 칸이라 무한정 늘릴 수 없다 — utf8mb4 기준 2048바이트이고
    -- 소유 키(16바이트)와 묶여 InnoDB 인덱스 키 상한(3072바이트) 안이다.
    token      VARCHAR(512) NOT NULL,
    -- 처음 등록한 시각. 앱이 다시 보내도 갱신하지 않는다.
    created_at DATETIME     NOT NULL,
    -- 마지막 등록 시각. 앱이 언제까지 살아 있었는지를 보는 자리다.
    updated_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_push_to_start_token_user_token UNIQUE (user_id, token)
);
