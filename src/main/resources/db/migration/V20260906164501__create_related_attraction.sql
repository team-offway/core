-- 중심 관광지와 함께 가는 곳 (#186).
--
-- ## 왜 필요한가
--
-- 지금 코스는 좌표 군집(GeoCluster.selectCompact)으로 짠다 — 가까운 것끼리 묶는다. 동선은 짧아지지만
-- **"왜 이 조합인가" 에는 답이 없다.**
--
-- TarRlteTarService1(관광지별 연관 관광지)이 그 답을 갖고 있다. 실제 방문 데이터라 "갑사에 간 사람들이
-- 실제로 들르는 곳" 이고, 관광지뿐 아니라 **음식**까지 순위로 준다.
--
-- 실측(공주시 44/44150 · baseYm=202606): 갑사 → 신원사(1위) · 동학사(2위) · 동해원(음식 8위) ·
-- 베이커리밤마을(11위) · 카페마암(12위).
--
-- ## 좌표는 원본이 안 준다
--
-- 응답 필드에 mapX·mapY 가 **없다**(중심관광지 API 는 준다). 그래서 인허가 장소(#144)와 이름으로 이어
-- 좌표를 얻고, **못 얻으면 담지 않는다.** 좌표 없는 후보가 표에 있으면 조회하는 쪽이 매번 걸러야 하고,
-- 한 군데만 빠뜨려도 동선이 깨진다. 그래서 lat·lng 가 NOT NULL 이다.
--
-- 실측(공주시): 연관 음식 75곳 중 54곳(72%)이 매칭됐다. 2박3일 식사 슬롯이 여섯이라 넉넉하다.
--
-- ## 지역 밖은 애초에 안 들어온다
--
-- 원본은 인접 시군 것을 섞어 준다 — 공주시 300건 중 45건(15%)이 천안·아산·부여였다. 그 필터는 어댑터가
-- 강제하므로 이 표에는 우리 지역 것만 들어온다.
--
-- ## 크기
--
-- 지역당 중심관광지 몇 곳 × 유형별 상위 50위인데, 좌표를 못 얻은 것이 빠지고 지역 밖도 빠진다. 공주시
-- 실측(300건 중 우리 지역 255건, 그중 좌표 매칭 72%)으로 어림하면 지역당 100~200행, 89곳 합계 1~2만
-- 행이다. **어림이라 첫 적재 후 실측한다.**

CREATE TABLE related_attraction (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    region_id       BIGINT       NOT NULL COMMENT '인구감소지역 89곳 중 하나 (raw 참조)',
    base_ym         CHAR(6)      NOT NULL COMMENT '원본 기준월 (yyyyMM)',
    hub_code        VARCHAR(64)  NOT NULL COMMENT '중심 관광지 코드 — "무엇과 함께 가는가" 의 그 무엇',
    hub_name        VARCHAR(200) NOT NULL,
    related_code    VARCHAR(64)  NOT NULL COMMENT '연관 관광지 코드',
    related_name    VARCHAR(200) NOT NULL,
    related_rank    INT          NOT NULL COMMENT '중심 관광지 안에서의 순위 (1부터, 낮을수록 강한 연결)',
    category_large  VARCHAR(50)  NOT NULL COMMENT '관광지·음식·숙박 — 어느 슬롯에 넣을지를 이 값이 정한다',
    category_medium VARCHAR(50),
    -- 이름으로 이어 붙인 인허가 장소. 좌표만 베끼지 않고 id 를 드는 이유는, 코스가 이 장소를 후보로
    -- 올릴 때 쓰는 식별자가 'LIC-{id}' 이기 때문이다. 좌표만 있으면 같은 곳을 다시 찾아야 한다.
    licensed_place_id BIGINT     NOT NULL,
    -- 인허가 조인으로 얻은 좌표. 못 얻은 행은 애초에 저장하지 않으므로 NOT NULL 이다.
    lat             DOUBLE       NOT NULL,
    lng             DOUBLE       NOT NULL,
    PRIMARY KEY (id),
    -- 같은 달 같은 중심의 같은 연관 장소가 두 번 들어오지 않게. 월 단위 재적재가 이 키로 맞춘다.
    UNIQUE KEY uk_related_attraction (region_id, base_ym, hub_code, related_code),
    -- 조회 모양은 "이 지역 이 중심의, 이 분류에서 순위 낮은 것부터" 다.
    KEY idx_related_attraction_hub (region_id, hub_code, category_large, related_rank)
);
