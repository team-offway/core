-- 관광사진 갤러리(PhotoGalleryService1) 적재 — 지역 대표 사진의 주력 소스(#196).
--
-- 왜 DB 인가: 요청 경로에서 사진을 찾으려면 지역마다 외부를 불러야 하는데, 그러면 일일 한도가 마르고
-- 배포마다 다시 긁는다(#193 과 같은 원칙). 전량이 6,118건(실측 2026-08-09)이라 통째로 들고 있어도 작다.
--
-- 촬영 위치는 원본이 자유 텍스트다("전남광주통합특별시"·"강원도"/"강원특별자치도" 혼재, "신승반점" 같은
-- 값도 있다). 그래서 원문(photography_location)과 우리가 정규화해 붙인 region_id 를 <b>따로</b> 둔다 —
-- 정규화 규칙이 바뀌어도 원문이 남아 있어야 다시 매길 수 있다.
--
-- MySQL / H2(MODE=MySQL) 호환. FK 없음(영속성 규약).

CREATE TABLE gallery_photo (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    gal_content_id      VARCHAR(32)  NOT NULL,
    title               VARCHAR(300) NOT NULL,
    image_url           VARCHAR(500) NOT NULL,
    -- 촬영월(yyyyMM). 없는 항목이 있어 NULL 허용 — 여행월과 가까운 사진을 고를 때만 쓴다.
    photography_month   VARCHAR(6)   NULL,
    -- 원본 촬영 위치 문자열. 정규화 전 원문이라 길이를 넉넉히 잡는다.
    photography_location VARCHAR(300) NULL,
    photographer        VARCHAR(100) NULL,
    -- 장소명 매칭에 쓰는 키워드 묶음. 제목에 없는 장소명이 여기 들어 있는 경우가 많다
    -- (예: "금강철교" 사진의 키워드에 "공산성").
    search_keyword      VARCHAR(2000) NULL,
    -- 정규화로 붙인 우리 지역. 못 붙이면 NULL 이고 대표 사진 후보에서 빠진다.
    region_id           BIGINT       NULL,
    updated_at          DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_gallery_photo_content UNIQUE (gal_content_id)
);

-- 지역별 대표 사진 조회용.
CREATE INDEX idx_gallery_photo_region ON gallery_photo (region_id);
