-- 앱에서 외부 페이지로 나가는 창구 (#341).
--
-- policy 와 나눈 이유는 담는 기준이 다르기 때문이다(#340). policy 는 "여행자가 직접 신청해 받는 혜택" 만
-- 담기로 한 자리이고(#217), 여기는 도 관광포털·국토교통부·지역 축제처럼 우리가 데이터를 다 갖지 못해
-- 웹뷰로 넘기는 것들이다.
--
-- ## always_on 을 따로 두는 이유 — policy 가 이미 덴 자리다
--
-- policy 는 기간을 NULL 로 두면 isActiveOn 이 "상시" 로 읽어, 사업이 끝나도 뱃지가 영영 남았다(#217).
-- NULL 이 "모른다" 인지 "상시" 인지 값만 보고 알 수 없는 것이 원인이었다. 그래서 상시를 명시적 플래그로
-- 받고, 끄면 종료일을 도메인이 요구한다 — 어드민이 깜빡한 것이 영구 노출로 굳지 않게.
--
-- ## surfaces 를 한 칸에 쉼표로 넣는 이유
--
-- 값이 넷(HOME·REGION·COURSE·POI)뿐이고 늘 일이 드물다. 별도 테이블로 쪼개면 조회마다 조인이 하나 느는데
-- 그 비용이 값어치를 넘는다.
--
-- ## FK 를 두지 않는다 (persistence-convention)
--
-- 이 테이블은 아무것도 참조하지 않는다 — 지역·코스에 묶이지 않고 면(surface) 단위로만 갈린다.

CREATE TABLE curated_link (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    title          VARCHAR(100)  NOT NULL COMMENT '카드 제목',
    chip_text      VARCHAR(30)   NOT NULL COMMENT '칩 문구 — 목록에서 처음 읽는 한 줄',
    description    VARCHAR(500)  NULL     COMMENT '카드 부제. 없으면 앱이 그 줄을 접는다',
    link_url       VARCHAR(1000) NOT NULL COMMENT '웹뷰로 열 주소. https 만 (도메인이 검증)',
    thumbnail_url  VARCHAR(1000) NULL     COMMENT '썸네일. 없으면 앱이 기본 이미지를 쓴다',
    starts_on      DATE          NULL     COMMENT '노출 시작일. 없으면 이미 시작한 것',
    ends_on        DATE          NULL     COMMENT '노출 종료일. always_on=FALSE 면 필수(도메인이 강제)',
    always_on      BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '상시 노출 — 날짜를 비운 것과 구분한다',
    surfaces       VARCHAR(100)  NOT NULL COMMENT '내릴 화면들. HOME,REGION,COURSE,POI 를 쉼표로',
    display_order  INT           NOT NULL DEFAULT 0 COMMENT '같은 면 안의 정렬. 작을수록 앞',
    published      BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '앱에 내릴지. 기본은 안 내린다',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by     VARCHAR(100)  NULL     COMMENT '마지막으로 고친 어드민(#342 부터 채운다)',
    PRIMARY KEY (id)
);

-- 앱 조회는 언제나 "게시된 것을 정렬 순으로" 다. 그 두 칸을 한 인덱스로 덮는다.
CREATE INDEX idx_curated_link_published ON curated_link (published, display_order);
