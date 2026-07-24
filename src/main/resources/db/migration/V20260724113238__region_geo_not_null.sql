-- 좌표·TourAPI 코드 backfill(V20260724112101) 완료 후 NOT NULL 보정.
-- region 은 마이그레이션으로만 쓰는 레퍼런스 데이터라, 이후 추가되는 행(고시 개정 등)도
-- 네 값의 완결을 DB 가 강제한다. 적용된 backfill 마이그레이션은 수정하지 않는다(checksum).

ALTER TABLE region MODIFY lat DOUBLE NOT NULL;
ALTER TABLE region MODIFY lng DOUBLE NOT NULL;
ALTER TABLE region MODIFY area_code INT NOT NULL;
ALTER TABLE region MODIFY sigungu_code INT NOT NULL;
