-- 최초 어드민 세 명 (#343).
--
-- ## 왜 마이그레이션인가
--
-- 아무도 어드민이 아니면 아무도 어드민을 추가할 수 없다. 백오피스 CRUD 는 전부 ROLE_ADMIN 뒤에 있어
-- 첫 한 명은 사람이 아니라 배포가 넣어야 한다. V20260829184901 이 표를 만들며 "그 값을 확인한 뒤 별도
-- 마이그레이션으로 넣는다" 고 남긴 그 자리다.
--
-- 두 번째부터는 이런 파일이 필요 없다 — 어드민이 생기면 그 뒤로는 화면에서 명단을 관리한다.
--
-- ## 왜 provider 식별자를 직접 안 박나
--
-- 화면이 알려주는 값은 우리 users.id 이고, 이 표의 키는 provider + sub 다. 사람이 카카오 회원번호를
-- 따로 찾아 옮겨 적게 하면 틀릴 여지가 생기고, 그 실수는 "로그인은 되는데 계속 403" 으로만 보여
-- 원인을 짚기 어렵다. 그래서 user_id 로 user_identity 를 찾아 provider 와 sub 를 SQL 이 직접 꺼낸다.
--
-- 부수 효과로 **이 파일이 다른 환경에서 안전해진다.** 로컬·테스트 DB 에는 이 사용자들이 없으므로
-- 조인이 0행을 내고 아무 일도 일어나지 않는다. 실패하지 않고 그냥 비어 있다.
--
-- ## INSERT IGNORE 인 이유
--
-- uk_admin_account (provider, provider_user_id) 와 부딪히면 건너뛴다. 지금 데이터로는 충돌이 생길 수
-- 없지만(사용자당 신원이 하나뿐이다), 손으로 먼저 넣어 둔 DB 에 이 마이그레이션이 닿는 경우를 막는다.
--
-- ## 적용 뒤 확인할 것
--
-- 0행이 들어가도 마이그레이션은 성공으로 끝난다 — user_id 를 잘못 적으면 조용히 아무 일도 안 일어난다.
-- 배포 후 admin_account 가 3행인지 직접 세어 확인한다.
--
-- ## 이미 발급된 토큰에는 반영되지 않는다
--
-- 역할은 토큰에 실려 나간다. 이 마이그레이션이 돈 뒤에도 **각자 다시 로그인해야** 새 토큰에 ADMIN 이
-- 실린다. 반대로 명단에서 빼도 그 사람의 토큰은 만료될 때까지 어드민으로 남는다(재발급 시점에 다시
-- 대조하므로 최대 access 토큰 수명만큼이다).

INSERT IGNORE INTO admin_account (provider, provider_user_id, label)
SELECT ui.provider, ui.provider_user_id, seed.label
  FROM (
        SELECT UNHEX(REPLACE('ac120003-a036-1787-81a0-386423ff0002', '-', '')) AS user_id, '박세빈' AS label
        UNION ALL
        SELECT UNHEX(REPLACE('ac120003-a034-18f9-81a0-34b928e60000', '-', '')), '조영찬'
        UNION ALL
        SELECT UNHEX(REPLACE('ac120003-a04c-1692-81a0-50d8e7880003', '-', '')), '이예빈'
       ) AS seed
  JOIN user_identity ui ON ui.user_id = seed.user_id;
