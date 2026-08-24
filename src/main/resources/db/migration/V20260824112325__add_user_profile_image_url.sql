-- 사용자 프로필 사진 주소(#308).
--
-- provider 가 주는 값이라 NULL 을 허용한다 — Apple 은 사진을 아예 안 주고, Kakao 는 동의를 거부할 수 있다.
-- 이메일이 같은 이유로 NULL 인 것과 같다.
--
-- 500 자로 둔 이유: Kakao CDN 주소가 쿼리스트링을 달고 오고 Google 사진 주소도 크기 파라미터가 붙는다.
-- 넘치면 저장 단계에서 터지는 대신 엔티티가 잘라 담는다(닉네임·이메일과 같은 규칙).

ALTER TABLE users ADD COLUMN profile_image_url VARCHAR(500) NULL;
