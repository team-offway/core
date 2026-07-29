-- 코스 슬롯을 트리플식 타임라인으로 인라인 렌더하기 위한 표시 필드.
-- 이미지·주소는 TourAPI 에서 오고, catchphrase 는 구석구석 감성 한 줄(추천 문구)이다.
-- 저장 코스도 TourAPI 재조회 없이 바로 그리도록 슬롯에 함께 영속한다. 전부 nullable(부가 정보), FK 없음·additive.
ALTER TABLE slot ADD COLUMN image_url VARCHAR(500);
ALTER TABLE slot ADD COLUMN address VARCHAR(300);
ALTER TABLE slot ADD COLUMN catchphrase VARCHAR(500);
