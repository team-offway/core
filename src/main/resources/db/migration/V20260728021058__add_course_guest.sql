-- 코스 저장(#33) — 게스트 소유자 식별. 로그인 전이라 클라이언트가 만든 게스트 ID(헤더)로 "내 코스"를 묶는다.
-- additive(ADD COLUMN·CREATE INDEX)라 out-of-order 안전. FK 없음(persistence-convention).

ALTER TABLE course ADD COLUMN guest_id VARCHAR(64);
CREATE INDEX idx_course_guest ON course (guest_id);
