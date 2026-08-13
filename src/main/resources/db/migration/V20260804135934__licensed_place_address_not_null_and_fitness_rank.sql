-- 장소 풀 보정(#144 리뷰 대응) — 주소 NOT NULL · 적합도 순위 컬럼 · 조회 인덱스 교체.
--
-- 앞선 마이그레이션(V20260804130352)을 고치지 않고 보정으로 나눈다(forward-only).
--
-- ① address NOT NULL
--    자연키 유니크 ux_licensed_place_natural (region_id, kind, name, address) 의 구성 요소인데
--    NULL 을 허용하면 MySQL·H2 가 NULL 끼리 서로 다른 값으로 취급해, 주소 없는 행이 몇 번이고 다시
--    들어간다(유니크가 절반만 걸린다). 생성 스크립트가 주소로 지역을 판정하므로 주소 없는 행은
--    애초에 실리지 않는다 — 기존 데이터에 NULL 이 있어도 빈 문자열로 눕히고 제약을 세운다.
--
-- ② fitness_rank
--    코스 적합도 순위(0=우선). 분류는 문자열로 저장돼 사전순이 적합도 순서와 다르다 — 그대로 정렬하면
--    목록 첫 페이지에 모텔(LODGING)이 관광호텔(TOURIST_HOTEL)보다 앞선다. 페이징이 걸려 정렬을 DB 가
--    해야 하므로(애플리케이션에서 다시 세우면 페이지 경계가 어긋난다) 컬럼으로 눕힌다.
--
-- ③ 인덱스 교체
--    기존 (region_id, kind) 는 자연키 유니크의 좌측 프리픽스와 겹쳐 적재 시 갱신 비용만 더했다.
--    목록 조회가 분류로도 좁히므로 (region_id, kind, category) 로 바꾼다.

UPDATE licensed_place SET address = '' WHERE address IS NULL;

-- MODIFY 로 쓴다 — H2 는 ALTER COLUMN ... SET NOT NULL 도 받지만 MySQL 은 MODIFY 만 받는다.
ALTER TABLE licensed_place MODIFY COLUMN address VARCHAR(300) NOT NULL;

ALTER TABLE licensed_place ADD COLUMN fitness_rank INT NOT NULL DEFAULT 1;

-- 기존 행의 순위를 분류에서 도출해 채운다(PlaceCategory.Fitness 와 같은 서열).
UPDATE licensed_place SET fitness_rank = 0
 WHERE category IN ('HANOK', 'TOURIST_HOTEL', 'TOURIST_PENSION', 'TOURIST_RESTAURANT',
                    'KOREAN', 'SEAFOOD', 'COFFEE', 'TRADITIONAL_TEA',
                    'TEMPLE', 'MUSEUM', 'THEME_PARK', 'CABLE_CAR');

UPDATE licensed_place SET fitness_rank = 2
 WHERE category IN ('LODGING', 'FASTFOOD', 'TEAROOM', 'GOLF');

DROP INDEX idx_licensed_place_region_kind ON licensed_place;

CREATE INDEX idx_licensed_place_lookup ON licensed_place (region_id, kind, category);
