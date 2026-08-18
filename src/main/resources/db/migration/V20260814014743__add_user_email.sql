-- 소셜 로그인이 주는 이메일을 보관한다(#34).
--
-- NULL 을 허용한다. Kakao 는 이메일 동의를 거부할 수 있고, Apple 은 Private Relay 익명 주소를 주거나
-- 최초 로그인 응답에만 준다 — 값이 없는 것이 정상 경로다.
--
-- UNIQUE 를 걸지 않는다. 한 사람이 provider 를 바꿔 다시 가입하면 같은 이메일이 두 행에 생길 수 있고,
-- Apple 익명 주소는 서비스마다 달라 동일성 판단에도 못 쓴다. 계정 매칭 키는 user_identity 의
-- (provider, provider_user_id) 뿐이다.
--
-- ADD COLUMN 이라 순서 무관하다(out-of-order 안전).

ALTER TABLE users ADD COLUMN email VARCHAR(255) NULL;
