-- 지역 콘텐츠(볼거리 수·대표 이미지·카테고리) 적재 — #193 2단계.
--
-- 왜 DB 인가: 인메모리 캐시(ExternalDataCache)는 프로세스와 함께 죽어, 배포할 때마다 부팅 워밍이 89개
-- 지역을 처음부터 다시 긁었다. 지역 콘텐츠는 #193 이 꼽은 넷 중 <b>호출량이 가장 크다</b>(지역당 자기 +
-- 인접 최대 3곳). 배포 20회면 그것만으로 일일 한도(1,000건)를 넘긴다.
--
-- 원본은 월 단위로도 잘 안 변하는 값이라 하루 한 번 갱신이면 충분하다.
--
-- <b>인접 병합까지 끝낸 결과를 저장한다.</b> 예전에는 요청 경로에서 89곳 팬아웃 + 인접 50km 병합을 했다.
-- 인접 관계는 89곳 좌표에서 나오는 고정값이라 미리 계산해도 틀어지지 않고, 그만큼 요청 경로가 가벼워진다.
--
-- MySQL / H2(MODE=MySQL) 호환. FK 없음(영속성 규약).

CREATE TABLE region_content (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    region_id         BIGINT       NOT NULL,
    -- 볼거리 수(TourAPI totalCount). 인접이 병합됐으면 합산된 값이다.
    content_count     INT          NOT NULL,
    -- 대표 이미지. #196 이후로는 갤러리 사진이 1순위라 이 값은 폴백으로 쓰인다.
    image_url         VARCHAR(500),
    -- 카테고리 enum 이름을 쉼표로 이어 둔다. 무드칩 분류라 개수가 적고(한 자릿수) 순서가 의미를 갖는다
    -- — 별도 테이블로 나누면 조인만 늘고 얻는 것이 없다.
    categories        VARCHAR(200) NOT NULL,
    -- 인접 50km 지역 콘텐츠가 병합됐는지. 화면의 "인접 포함" 안내에 쓴다.
    neighbor_included BOOLEAN      NOT NULL,
    updated_at        DATETIME     NOT NULL,
    PRIMARY KEY (id),
    -- 지역당 한 행. 갱신은 지역 단위 교체라 이 제약이 중복 적재를 막는다.
    CONSTRAINT uk_region_content_region UNIQUE (region_id)
);
