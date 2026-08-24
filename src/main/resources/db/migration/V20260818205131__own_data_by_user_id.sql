-- 코스·연차·후기·알림의 소유를 guest_id 에서 user_id 로 옮긴다 (#280).
--
-- ## 왜
--
-- 소유 키가 요청 헤더였다. 클라이언트가 X-Guest-Id 로 들고 오는 값을 서버가 검증할 방법이
-- 없어, 그 문자열을 아는 것만으로 남의 데이터를 지울 수 있었다 — 일회용 소셜 계정으로
-- 가입하며 피해자의 게스트 키를 로그인 콜백에 실으면 탈퇴 한 번으로 전부 삭제됐다.
--
-- 임시 대책을 두 번 얹었지만(탈퇴가 헤더를 안 받게 · user_guest_link 로 서버가 기록)
-- 둘 다 자리를 옮겼을 뿐이다. 파괴를 인증된 주체에 묶어야 닫힌다.
--
-- ## backfill 이 없는 이유
--
-- 공개 배포 전이라 보존할 데이터가 없다. 이 전환이 원래 비싼 이유는 기존 데이터를 지키며
-- 옮겨야 해서인데(컬럼 추가 → backfill → 조회 전환 → 옛 컬럼 제거, 여러 배포) 그게 통째로
-- 빠진다. **DB 를 비운 뒤 이 마이그레이션을 적용한다.**
--
-- 장소 풀·지역 마스터는 부팅 시 재적재되므로 함께 지워도 복구된다.
--
-- ## user_id 에 FK 를 걸지 않는다
--
-- 이 레포는 FK 를 두지 않는다(persistence-convention). 참조 무결성은 서비스가 책임진다 —
-- 탈퇴가 user_id 로 직접 지운다.

-- ── 코스 ─────────────────────────────────────────────────────
DROP INDEX idx_course_guest ON course;
ALTER TABLE course
    DROP COLUMN guest_id,
    ADD COLUMN user_id BINARY(16) NULL COMMENT '소유자. NULL 은 주인 없는 공유 전용 코스(#261)';
CREATE INDEX idx_course_user ON course (user_id);

-- ── 연차 설정 ─────────────────────────────────────────────────
ALTER TABLE leave_balance
    DROP INDEX uk_leave_balance_guest,
    DROP COLUMN guest_id,
    ADD COLUMN user_id BINARY(16) NOT NULL COMMENT '소유자',
    ADD CONSTRAINT uk_leave_balance_user UNIQUE (user_id);

-- ── 연차 사용 내역 ─────────────────────────────────────────────
DROP INDEX uk_leave_usage_guest_course ON leave_usage;
DROP INDEX idx_leave_usage_guest ON leave_usage;
ALTER TABLE leave_usage
    DROP COLUMN guest_id,
    ADD COLUMN user_id BINARY(16) NOT NULL COMMENT '소유자';
CREATE INDEX idx_leave_usage_user ON leave_usage (user_id);
-- 코스당 한 행 — 중복 차감을 막는 그 제약이다(#91).
CREATE UNIQUE INDEX uk_leave_usage_user_course ON leave_usage (user_id, course_id);

-- ── 여행 결과 ─────────────────────────────────────────────────
DROP INDEX idx_trip_outcome_guest ON trip_outcome;
ALTER TABLE trip_outcome
    DROP INDEX uk_trip_outcome_guest_course,
    DROP COLUMN guest_id,
    ADD COLUMN user_id BINARY(16) NOT NULL COMMENT '소유자',
    ADD CONSTRAINT uk_trip_outcome_user_course UNIQUE (user_id, course_id);
CREATE INDEX idx_trip_outcome_user ON trip_outcome (user_id);

-- ── 알림 ─────────────────────────────────────────────────────
-- #273·#279 로 뒤에 생겨 원래 목록에 없었다. 같은 성질이라 함께 옮긴다.
ALTER TABLE notification
    DROP INDEX idx_notification_owner,
    DROP INDEX uk_notification_owner_type_course,
    DROP COLUMN guest_id,
    ADD COLUMN user_id BINARY(16) NOT NULL COMMENT '소유자',
    ADD KEY idx_notification_owner (user_id, created_at),
    ADD UNIQUE KEY uk_notification_owner_type_course (user_id, type, course_id);

-- ── 전환용 임시 테이블 제거 ────────────────────────────────────
-- 이 전환의 backfill 키로 만든 것이다(#34). 전환이 끝나면 존재 이유가 없다.
DROP TABLE user_guest_link;
