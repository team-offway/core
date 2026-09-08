-- 큐레이션 링크의 시드 식별자 (#498 리뷰 지적).
--
-- ## 무엇이 문제였나
--
-- 시드가 **id 를 명시**해 넣고 있었다. 처음 세 건(id 1~3)일 때는 문제가 드러나지 않았는데, #498 이
-- 아홉 건을 더하면서 id 4~12 를 쓰기 시작했다. 그런데 **이 표는 런타임 write 를 받는다**(#342 어드민 CRUD).
--
-- 운영 실측(2026-09-08)이 그 위험을 그대로 보여준다.
--
--   현재 행: id 1, 2, 3    ·    AUTO_INCREMENT = 4
--
-- 어드민이 링크를 **하나만** 만들어도 그것이 id 4 를 받는다. 그다음 배포에서 시드의
-- `INSERT IGNORE ... (4, '고속버스 예매', ...)` 는 PK 충돌로 **조용히 건너뛰어지고**, 그 공식 링크는
-- 영영 들어가지 않는다. 실패도 아니고 로그도 없다 — 그냥 없다.
--
-- 원래 파일의 주석이 그 경계를 적어 두었다("어드민이 새로 만든 항목(id 4 이상)도 손대지 않는다").
-- #498 이 그 경계를 넘은 것이다.
--
-- ## 왜 id 를 옮기지 않고 열쇠를 따로 두나
--
-- 시드를 1000번대로 밀어도 어드민이 그만큼 만들면 같은 일이 생긴다. 문제의 뿌리는 **시드 행과 어드민
-- 행이 같은 열쇠(PK)를 두고 경쟁하는 것**이라, 열쇠를 갈라야 끝난다.
--
-- `seed_key` 는 시드 행만 채우고 어드민 행은 NULL 로 둔다. MySQL 의 UNIQUE 는 NULL 을 여럿 허용하므로
-- 어드민이 몇 건을 만들든 걸리지 않는다. id 는 AUTO_INCREMENT 에 맡긴다 — 우리가 정할 이유가 없었다.
--
-- ## 기존 세 건을 채운다
--
-- 채우지 않으면 다음 시드가 `seed_key` 로 못 찾아 **같은 링크를 한 번 더 넣는다**. 이미 뜬 DB 에서는
-- "대한민국 구석구석" 이 둘이 된다.
--
-- 매칭은 **주소로** 하되 **한 행만** 고른다. id 로 하면 환경마다 다를 수 있는 값에 기대게 되고,
-- 주소만 보면 같은 주소가 여럿일 때 전부 갱신된다.
--
-- **`link_url` 에는 UNIQUE 가 없다.** 어드민이 같은 공식 주소로 링크를 하나 더 만들 수 있고, 그 행도
-- `seed_key IS NULL` 을 만족한다. 상한 없이 갱신하면 두 행이 같은 `seed_key` 를 갖게 되고, 그다음
-- `CREATE UNIQUE INDEX` 가 **1062 Duplicate entry 로 죽는다** — 마이그레이션 실패라 부팅이 안 된다.
-- 재현으로 확인했다(2026-09-08).
--
-- `ORDER BY id LIMIT 1` 이 시드 행을 고른다. 시드는 Flyway 가 부팅 때 넣고 어드민은 앱이 뜬 뒤 쓰므로,
-- **시드 행의 id 가 항상 더 작다.** 어드민이 만든 중복 행은 `seed_key` 가 NULL 로 남아 안 건드려진다.

ALTER TABLE curated_link
    ADD COLUMN seed_key VARCHAR(100) NULL COMMENT '시드 행의 식별자. 어드민이 만든 행은 NULL (#498)';

-- 이미 들어가 있는 세 건. 새 환경에서는 아무것도 안 바뀌고, 그다음 R__ 시드가 seed_key 와 함께 넣는다.
UPDATE curated_link SET seed_key = 'kto-visitkorea'
 WHERE link_url = 'https://korean.visitkorea.or.kr' AND seed_key IS NULL
 ORDER BY id LIMIT 1;
UPDATE curated_link SET seed_key = 'khs-heritage-portal'
 WHERE link_url = 'https://www.heritage.go.kr' AND seed_key IS NULL
 ORDER BY id LIMIT 1;
UPDATE curated_link SET seed_key = 'korail-ticket'
 WHERE link_url = 'https://www.letskorail.com' AND seed_key IS NULL
 ORDER BY id LIMIT 1;

-- 이 UNIQUE 가 곧 시드의 열쇠다. 같은 seed_key 를 두 번 넣으면 여기서 걸린다.
CREATE UNIQUE INDEX uk_curated_link_seed_key ON curated_link (seed_key);
