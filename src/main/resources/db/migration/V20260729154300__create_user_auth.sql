-- OAuth 인증 기반 User(#34) — 게스트 식별 폐기(ADR 0002).
-- FK 제약은 두지 않는다(persistence-convention). 조회 인덱스와 UNIQUE 제약만 유지.
-- MySQL / H2(MODE=MySQL) 양쪽 호환. UUID 는 BINARY(16) 로 저장한다.
--
-- 테이블명이 users(복수)인 이유: USER 는 MySQL·H2 양쪽에서 예약어라 단수형을 쓸 수 없다.
-- 나머지 테이블(course·region·policy)의 단수 규칙에서 이것만 벗어난다.

CREATE TABLE users (
    id         BINARY(16)  NOT NULL,
    nickname   VARCHAR(50) NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP   NOT NULL,
    PRIMARY KEY (id)
);

-- provider 계정 ↔ 우리 유저 매핑. 매칭 키는 ID 토큰의 sub 뿐이다.
-- 이메일로 매칭하지 않는다 — Apple Private Relay 는 익명 주소를 주고, Kakao 는 이메일 동의를 거부할 수 있다.
CREATE TABLE user_identity (
    id               BINARY(16)   NOT NULL,
    user_id          BINARY(16)   NOT NULL,
    provider         VARCHAR(20)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at       TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_identity_provider UNIQUE (provider, provider_user_id)
);
CREATE INDEX idx_user_identity_user ON user_identity (user_id);

-- refresh 토큰. 원문이 아니라 SHA-256 해시만 저장한다(DB 유출 시 그대로 쓰이는 걸 막는다).
-- 회전 시 행을 지우지 않고 revoked_at 을 채운다 — 지우면 "폐기된 토큰 재사용"과 "없는 토큰"을 구분할 수 없어
-- 탈취 감지가 불가능해진다.
CREATE TABLE refresh_token (
    id         BINARY(16)  NOT NULL,
    user_id    BINARY(16)  NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP   NOT NULL,
    revoked_at TIMESTAMP   NULL,
    created_at TIMESTAMP   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);
