-- 반려동반 가능 장소 (#566) — 슬롯 모달의 "반려동물 동반" 칩과 그 상세.
--
-- ## 왜 필요한가
--
-- 반려동물과 여행하는 사용자가 **그 장소에 데려갈 수 있는지 알 방법이 없다.** 코스가 나와도 한 칸씩
-- 검색해 확인해야 하므로 그대로 쓰지 못한다.
--
-- 관광공사 반려동물 동반여행(`KorPetTourService2`)을 승인받아 실호출로 확인했다(#555).
--
-- ## 실측 (2026-09-13, 운영 DB 대조)
--
-- 전국을 한 요청에 받아 우리 `region.legal_code` 로 걸렀다.
--
--   전국 9,679건 (6.3MB · 2,790ms · 좌표 100%)
--     → 89곳 매칭 **442건**, 커버 **67 / 89곳**, 사진 92%
--
-- #555 본문은 170건 / 57곳이었는데 그것은 과소집계였다. 그쪽은 요청마다 TourAPI 지역코드를 넣어
-- 89번 돌았고(함정 3), 이쪽은 **지역 파라미터 없이 전국을 받아 응답의 법정동 코드로 맞춘다** —
-- `lDongRegnCd`+`lDongSignguCd` 가 9,675/9,679건에 채워져 온다. 변환을 거치지 않으니 누락이 없고
-- 호출도 89건에서 1건으로 준다.
--
-- ## 상세를 함께 담는 이유 — 칩 하나로는 정직하지 않다
--
-- `/detailPetTour2` 를 태안 15건 전수로 확인했다. **조건부가 섞여 있다.**
--
--   acmpyTypeCd    전구역 동반가능 8건 · **일부구역 동반가능 7건**
--   acmpyPsblCpam  전 견종 11건 · 5kg 이내 1건 · 9kg 이하 1건 · 10kg 이하 1건 · 빈값 1건
--
-- 절반이 "일부구역" 인데 "반려동물 동반" 칩만 띄우면 그 사실이 가려진다. 시안이 "누르면 동반 조건·
-- 유의사항이 열린다" 로 처리하므로, 상세를 미리 받아 **모달 응답에 실어** 왕복 없이 열리게 한다.
--
-- 이용 가능 시설(`relaPosesFclty`·`relaFrnshPrdlst`·`relaRntlPrdlst`)은 **대개 빈다** — 15건 중
-- 하나라도 채워진 곳이 1건이었다. 컬럼은 두되 화면은 없을 때를 전제로 그린다.
--
-- ## 자연키는 TourAPI contentId
--
-- 고캠핑(#510)과 같은 이유다. 재적재하는 표라 같은 장소가 두 번 들어오면 안 되고, 우리 id 는
-- 재적재마다 바뀌어 자연키로 쓸 수 없다.
--
-- 여기서는 이유가 하나 더 있다 — 이 값이 **장소 풀 매칭 키**다. 우리 관광 API 후보도 같은 contentId 를
-- 쓰므로 그 집합으로 맞춰 칩을 띄운다. 인허가·국가유산·고캠핑 출처는 이 값이 없어 판정할 수 없고,
-- 그때는 칩을 띄우지 않는다 — **모르는 것을 "불가" 로 내리지 않는다.**
--
-- ## 좌표를 담지 않는다
--
-- 지금 필요한 것은 "이 장소가 반려동반 가능한가" 뿐이라 매칭 키와 상세만 있으면 된다. 좌표는
-- 후보를 동선에 올릴 때 쓰는 값인데, "반려동물 우선 선별"(볼거리 슬롯을 반려동물 풀에서 먼저 뽑기)은
-- 별 작업으로 미뤘다(#566 · 팀 결정 2026-09-13). 그때 컬럼을 더하면 되고, ADD COLUMN 은 순서 무관하다.
--
-- ## 크기
--
-- 442건이다. 상세 텍스트를 얹어도 1MB 안쪽으로, 지금 DB 규모에 영향이 없다.

CREATE TABLE pet_friendly_place (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    -- 자연키이자 장소 풀 매칭 키. 우리 관광 API 후보의 contentId 와 같은 체계다.
    content_id      VARCHAR(50)  NOT NULL COMMENT 'TourAPI contentId — 자연키 · 매칭 키',
    region_id       BIGINT       NOT NULL COMMENT '인구감소지역 89곳 중 하나 (raw 참조)',
    name            VARCHAR(200) NOT NULL COMMENT '장소명 (title) — 운영·디버깅용. 화면은 코스 슬롯의 이름을 쓴다',

    -- 동반 조건 — 칩을 눌렀을 때 가장 먼저 보여줄 두 값이다.
    accompany_area  VARCHAR(100)          COMMENT 'acmpyTypeCd — 전구역/일부구역 동반가능',
    accompany_pet   VARCHAR(200)          COMMENT 'acmpyPsblCpam — 전 견종 / 5kg 이내 소형견 등',

    -- 유의사항.
    required_matter VARCHAR(500)          COMMENT 'acmpyNeedMtr — 목줄 착용 등',
    etc_info        TEXT                  COMMENT 'etcAcmpyInfo — 줄바꿈이 섞여 온다',
    risk_matter     TEXT                  COMMENT 'relaAcdntRiskMtr — 사고 위험 사항',

    -- 이용 가능 시설·품목. 실측에서 15건 중 1건만 채워져 있었다.
    facilities      VARCHAR(500)          COMMENT 'relaPosesFclty — 보유 시설',
    provided_items  VARCHAR(500)          COMMENT 'relaFrnshPrdlst — 제공 품목',
    rental_items    VARCHAR(500)          COMMENT 'relaRntlPrdlst — 대여 품목',

    fetched_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    -- 재적재가 같은 장소를 두 번 넣지 못하게 막는다.
    UNIQUE KEY uk_pet_friendly_place_content (content_id),
    -- "그 지역의 반려동반 장소" — 운영 확인과 후속 우선 선별이 쓸 조회 모양이다.
    KEY idx_pet_friendly_place_region (region_id)
);
