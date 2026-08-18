-- 같은 코스에 같은 종류의 알림이 두 번 생기지 않게 한다(#269).
--
-- **왜 DB 에 두나.** 여행 전날 알림은 배치가 만든다. 배치는 재실행되고, 재배포로 주기가 처음부터 다시
-- 세지기도 한다. 애플리케이션이 "있는지 조회 → 없으면 INSERT" 로 판정하면 두 실행이 동시에 "없다" 를
-- 읽고 둘 다 넣는다. 판정을 DB 에 두면 경합을 DB 가 흡수한다 — 푸시 토큰(#264)이 유니크 제약 + upsert 로
-- 푼 것과 같은 결이다.
--
-- **course_id 가 NULL 인 알림은 이 제약에 걸리지 않는다.** MySQL 유니크 인덱스는 NULL 을 서로 다른 값으로
-- 보기 때문이다. 그것이 여기서는 맞는 동작이다 — 코스와 무관한 알림(공지 등)은 같은 사람에게 여러 번
-- 갈 수 있어야 한다. 코스에 묶인 알림만 코스 단위로 한 번이면 된다.
--
-- 기존 조회 인덱스(idx_notification_owner)는 그대로 둔다. 목록은 여전히 (guest_id, created_at) 으로 읽고,
-- 이 유니크 키는 삽입 판정에만 쓰인다.
ALTER TABLE notification
    ADD UNIQUE KEY uk_notification_owner_type_course (guest_id, type, course_id);
