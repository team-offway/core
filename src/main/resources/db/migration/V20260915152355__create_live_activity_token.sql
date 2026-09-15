-- 잠금화면(Live Activity) 갱신 토큰(#575).
--
-- **device_push_token 과 왜 따로 두나.** 가리키는 것이 다르다. FCM 토큰은 *기기* 하나를 가리키고 앱을
-- 지울 때까지 살지만, 이 토큰은 *잠금화면에 띄운 카드* 하나를 가리키고 그 카드가 사라지면 함께 죽는다.
-- iOS 는 Live Activity 를 최대 8시간 뒤 스스로 끝내므로 이 표의 행은 짧게 산다.
--
-- 한 표에 섞으면 두 가지가 조용히 깨진다. 기기 단위 발송(FCM)이 Live Activity 토큰으로 나가고, 반대로
-- 잠금화면 갱신이 FCM 토큰으로 나간다. 둘 다 오류 없이 실패하는 종류라 아무 흔적을 안 남긴다.
--
-- **유니크는 (user_id, course_id).** 한 사람이 같은 코스를 두 번 띄울 이유가 없고, 앱이 재시도할 수
-- 있어야 하므로 등록은 멱등이어야 한다. 그 판정을 애플리케이션에서 조회 후 분기로 하면 동시 요청이 둘 다
-- "없다" 를 읽는다 — 제약을 쥔 DB 가 한 문장(INSERT ... ON DUPLICATE KEY UPDATE) 안에서 가르게 한다.
--
-- **token 에는 유니크를 걸지 않는다.** 토큰이 신원인 FCM 과 달리 여기서 신원은 (사람, 코스)다. 토큰은
-- 같은 카드가 다시 뜨면 바뀌는 값이라, 그쪽에 제약을 걸면 갱신이 제약 위반으로 떨어진다.
--
-- user_id·course_id 에 FK 를 걸지 않는다(영속성 규약). 참조 무결성은 서비스 계층이 책임진다 — 코스가
-- 지워지면 자정 배치가 코스를 못 찾아 종료를 보내고 행을 지운다.
CREATE TABLE live_activity_token (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    -- 주인. 코스·연차·알림과 같은 소유 키다(#280). 등록 본문이 아니라 access 토큰이 확인한 값이 들어간다.
    user_id    BINARY(16)   NOT NULL,
    -- 잠금화면에 띄운 코스.
    course_id  BIGINT       NOT NULL,
    -- Live Activity push token. hex 문자열이고 실제로는 160자 안팎이지만 규격이 길이를 못 박지 않아
    -- 여유를 둔다. 유니크 인덱스가 걸리지 않는 칸이라 인덱스 키 상한을 신경 쓸 필요가 없다.
    token      VARCHAR(512) NOT NULL,
    -- 처음 등록한 시각. 토큰이 갈려도 갱신하지 않는다.
    created_at DATETIME     NOT NULL,
    -- 마지막 등록 시각. 토큰이 언제 갈렸는지를 보는 자리다.
    updated_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_live_activity_token_user_course UNIQUE (user_id, course_id)
);
