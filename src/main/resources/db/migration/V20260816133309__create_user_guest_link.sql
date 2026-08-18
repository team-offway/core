-- 로그인한 사용자와 그 기기의 게스트 키를 잇는다(#34).
--
-- 지금 코스·연차는 guest_id 로 묶여 있고 사용자 식별이 아직 그리로 옮겨가지 않았다. 그래서 서버는
-- "이 사용자의 데이터가 무엇인가" 를 스스로 알지 못하고, 헤더로 들고 오는 값에 의존한다. 그 상태에서는
--
--   * 탈퇴가 헤더 없이 오면 코스·연차가 주인 없이 영영 남고,
--   * 헤더를 바꿔 보내면 남의 연차·후기를 지울 수 있으며,
--   * 나중에 소유를 user_id 로 옮길 때 무엇을 누구에게 줄지 판단할 근거가 없다.
--
-- 로그인할 때 한 줄 적어 두면 셋 다 닫힌다. 이 표가 그때의 backfill 키가 된다.
--
-- guest_id 에 UNIQUE 를 건다 — 한 기기에서 두 사람이 로그인해도 그 기기의 옛 데이터는 먼저 로그인한
-- 사용자의 것으로 고정한다. 뒤에 온 사람에게 상속시키면 남의 데이터를 넘기는 셈이라, 안 넘기는 쪽을 택했다.
--
-- FK 는 두지 않는다(persistence-convention). 조회 인덱스와 UNIQUE 제약만 유지한다.
CREATE TABLE user_guest_link (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    user_id   BINARY(16)  NOT NULL,
    guest_id  VARCHAR(64) NOT NULL,
    linked_at DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_guest_link_guest UNIQUE (guest_id),
    KEY idx_user_guest_link_user (user_id)
);
