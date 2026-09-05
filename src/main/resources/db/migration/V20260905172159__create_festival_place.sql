-- 전국문화축제표준데이터 축제 (#433) — 볼거리 후보.
--
-- ## 왜 필요한가
--
-- **89곳에 축제가 사실상 없다.** TourAPI searchFestival2 를 89곳 전수로 돌린 결과가 합계 **1건**이었다
-- (#392, 축제 0건인 지역 86곳). 탐침 오류가 아니다 — 안동·정선·남해를 세 갈래로 교차 확인했고 셋 다 0인데,
-- 같은 지역이 볼거리·맛집·숙박은 임계를 넉넉히 넘긴다. **지역이 얇은 게 아니라 축제 타입만 비어 있다.**
--
-- 같은 89곳을 전국문화축제표준데이터(data.go.kr 15013104)로 재면 **446건**이고 88곳이 덮인다.
-- 0건인 곳은 경남 함안군 하나뿐이다.
--
-- ## 왜 festival_period 에 얹지 않나
--
-- festival_period 는 TourAPI contentId 를 PK 로 **기간만** 드는 표다(#388). 그쪽이 나뉜 이유는 TourAPI 가
-- 장소와 기간을 **다른 조회로** 주기 때문이다.
--
-- 표준데이터는 **한 응답에 둘 다 온다.** 나눠 담으면 쓰기가 두 번이 되고, 더 나쁜 것은 재적재마다 우리 id 가
-- 바뀌면 'FST-{id}' 키가 **다른 축제를 가리킨다**는 점이다. 그래서 기간을 이 표가 직접 든다.
--
-- ## 자연키로 잠근다
--
-- 매월 재적재하는 표라 같은 축제가 두 번 들어오면 안 된다. (지역·축제명·개최시작일)이 한 회차를 가리키는
-- 자연키다 — 같은 축제라도 회차가 다르면 시작일이 다르고, 같은 날 같은 이름의 다른 축제는 한 지역에 없다.
--
-- 이 UNIQUE 가 조회 인덱스 역할도 한다(앞자리가 region_id). 다만 "그 지역의 아직 안 끝난 축제" 를 보는
-- 질의가 event_end 를 타므로 그쪽 인덱스를 따로 둔다.
--
-- ## 크기
--
-- 89곳 합계 446건이다. 좌표 없는 101건은 적재 단계에서 빠지므로 실제로는 345건 남짓 — 인덱스를 얹어도
-- 수십 KB 다. 지금 DB 51MB 에 영향이 없다.

CREATE TABLE festival_place (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    region_id    BIGINT       NOT NULL COMMENT '인구감소지역 89곳 중 하나 (raw 참조)',
    name         VARCHAR(200) NOT NULL COMMENT '축제명',
    venue        VARCHAR(300)          COMMENT '개최장소 — 주소보다 사람 말에 가깝다',
    address      VARCHAR(300) NOT NULL COMMENT '소재지 도로명주소 (없으면 지번주소)',
    -- 좌표가 없으면 동선에 못 올려 코스에 쓸 수 없다. 적재 단계에서 거르고 여기서도 NOT NULL 로 막는다.
    lat          DOUBLE       NOT NULL,
    lng          DOUBLE       NOT NULL,
    event_start  DATE         NOT NULL COMMENT '개최 시작일',
    event_end    DATE         NOT NULL COMMENT '개최 종료일',
    description  TEXT                  COMMENT '축제내용 — 지자체가 쓴 글이라 길이·문체가 제각각',
    host         VARCHAR(200)          COMMENT '주관기관명',
    tel          VARCHAR(50),
    homepage_url VARCHAR(500),
    fetched_at   DATETIME     NOT NULL,
    PRIMARY KEY (id),
    -- 한 회차를 가리키는 자연키. 매월 재적재가 같은 축제를 두 번 넣지 못하게 막는다.
    UNIQUE KEY uk_festival_place (region_id, name, event_start),
    -- "그 지역의, 아직 안 끝난 축제" 가 실제 조회 모양이다.
    KEY idx_festival_place_region (region_id, event_end)
);
