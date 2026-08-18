-- 살아 있는 refresh 만 골라내는 조회의 인덱스(#34).
--
-- 기존 인덱스는 (user_id) 뿐이라 revoked_at 조건을 인덱스로 못 거른다. access 1시간 / refresh 60일에
-- 삭제 경로가 없어 사용자당 행이 계속 쌓이는데(1년이면 1,400행 남짓), 그중 살아 있는 것은 1~2개다.
-- 로그아웃과 재사용 감지가 그때마다 그 사용자의 전 행을 읽는다.
--
-- 선두를 user_id 로 둔다 — 기존 인덱스가 하던 "이 사용자의 토큰" 조회를 그대로 덮고, 뒤에 revoked_at 을
-- 붙여 살아 있는 것만 바로 짚는다. 기존 인덱스는 이 인덱스의 접두어라 남겨 둘 이유가 없지만, DROP 은
-- 순서 의존이라 이 마이그레이션에서 함께 하지 않는다(persistence-convention: add → backfill → drop).
CREATE INDEX idx_refresh_token_user_active ON refresh_token (user_id, revoked_at);
