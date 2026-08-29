-- 백오피스를 쓸 수 있는 계정 화이트리스트 (#342).
--
-- ## 왜 테이블인가
--
-- 환경변수에 이메일을 박으면 사람이 늘 때마다 배포해야 한다. "배포 없이 고친다" 는 이 에픽(#340)의
-- 취지와 정면으로 모순된다.
--
-- ## 왜 이메일이 아니라 provider 식별자인가
--
-- user_identity 와 같은 이유다. Apple Private Relay 는 익명 주소를 주고 Kakao 는 이메일 동의를 거부할 수
-- 있어, 이메일은 없거나 바뀔 수 있는 값이다. provider 가 준 sub 만이 안 바뀐다.
--
-- ## 최초 한 명
--
-- 아무도 어드민이 아니면 아무도 어드민을 추가할 수 없다(부트스트랩 문제). 그래서 첫 계정은 사람이 아니라
-- 마이그레이션이 넣어야 하는데, **그 값을 알려면 그 사람이 한 번 로그인해 user_identity 에 sub 가 남아야
-- 한다.** 이 파일은 표만 만들고, 첫 계정은 그 값을 확인한 뒤 별도 마이그레이션으로 넣는다.
--
-- 그전까지 /api/v1/admin/** 은 아무도 통과하지 못한다 — 잠긴 채로 뜨는 것이 열린 채로 뜨는 것보다 낫다.
--
-- FK 는 두지 않는다(영속성 규약). users 를 참조하지도 않는다 — 이 표가 가리키는 것은 우리 사용자가 아니라
-- **provider 계정**이라, 아직 가입하지 않은 사람도 미리 넣어 둘 수 있다.

CREATE TABLE admin_account (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    provider         VARCHAR(20)  NOT NULL COMMENT 'KAKAO · APPLE · GOOGLE',
    provider_user_id VARCHAR(100) NOT NULL COMMENT 'provider 가 준 sub. 이메일이 아니다',
    label            VARCHAR(50)  NOT NULL COMMENT '사람이 알아볼 이름 — curated_link.updated_by 에 그대로 남는다',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 로그인마다 이 조합으로 한 번 조회한다. UNIQUE 가 곧 그 인덱스다.
    UNIQUE KEY uk_admin_account (provider, provider_user_id)
);
