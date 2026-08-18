-- provider 가 준 refresh 토큰을 신원 행에 담는다 (#287).
--
-- 왜 user_identity 인가: 이 토큰은 "이 사용자의 이 provider 연결" 에 속한다. 사용자당도
-- 아니고 세션당도 아니다. 탈퇴가 신원을 지울 때 함께 사라지는 것도 맞는 생명주기다.
--
-- 왜 우리 refresh_token 테이블이 아닌가: 그쪽은 우리가 발급하고 우리가 회전시키는 토큰이다.
-- 이건 Apple 이 발급한 남의 토큰이고, 우리는 보관했다가 돌려줄 뿐이다. 한 테이블에 두면
-- 회전·폐기 규칙이 서로 다른 두 가지가 섞인다.
--
-- 이름에 apple 을 박지 않는다. 지금 채우는 것은 Apple 뿐이지만 컬럼이 표현하는 것은
-- "provider 가 준 갱신 토큰" 이다. 카카오 unlink 는 Admin 키 방식이라 이 자리를 안 쓴다.
--
-- NULL 이 정상이다 — 이 변경 이전에 로그인한 사용자와 Apple 이 아닌 provider 는 비어 있다.
-- 소급 적용은 불가능하다(authorizationCode 는 1회용·5분). 재로그인하면 채워진다.
ALTER TABLE user_identity
    ADD COLUMN provider_refresh_token VARCHAR(512) NULL
        COMMENT 'provider 가 준 갱신 토큰. 지금은 Apple 연결 해제에만 쓴다. 없으면 해제 불가',
    ADD COLUMN provider_client_id VARCHAR(255) NULL
        COMMENT '그 토큰을 발급받은 클라이언트(Bundle ID·Service ID). 해제할 때 같은 값으로 서명해야 한다';
