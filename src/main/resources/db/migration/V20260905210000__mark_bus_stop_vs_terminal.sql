-- 터미널과 경유 정류소를 가른다 (#446).
--
-- TAGO 터미널 목록에는 **경유 정류소가 섞여 있다** — 잠실역·광나루역·DDP·대학 앞 정류장 같은 것들이다.
-- 출발 지점을 좌표 최근접으로만 고르면 그런 곳이 뽑힌다. 실제로 서울역에서 DDP(3.8km)가
-- 동서울(11km)을 이겼다. 정류소는 특정 노선만 서므로 '거기서 타세요' 가 틀린 안내가 될 수 있고,
-- 구간 소요시간·출발 시각 조회도 터미널 코드를 전제한다.
--
-- 분류 근거는 재지오코딩(#436)이 확인한 **실제 장소명**이다. 카카오 분류(category_name)는 쓰지 않는다 —
-- '일동버스터미널'·'성전터미널' 같은 진짜 터미널 16곳을 '고속,시외버스정류장' 으로 묶어 준다.
--
-- **정류소를 버리지 않는다.** 터미널만 남기면 인구감소지역 커버리지가 86곳에서 83곳으로 준다.
-- 우선순위만 바꾸고, 반경 안에 터미널이 없으면 정류소를 그대로 쓴다.
--
-- 확인된 지점 중 정류소로 가른 것: 110곳

ALTER TABLE bus_terminal
    ADD COLUMN is_terminal BOOLEAN NOT NULL DEFAULT TRUE
    COMMENT '터미널이면 TRUE, 경유 정류소면 FALSE. 근거를 못 찾은 행은 TRUE 로 둔다(보수적)';

-- ── 경유 정류소
-- 독바위 (EXPRESS) — 독바위역 6호선
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAEK480';
-- 상봉 (EXPRESS) — 상봉임시정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAEK040';
-- 석계 (EXPRESS) — 석계역 1호선
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAEK889';
-- 강천사 (INTERCITY) — 강천사정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5602002';
-- 개양(진주) (INTERCITY) — 개양시외버스승강장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5282202';
-- 거제(고현) (INTERCITY) — 거제옥포정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5325101';
-- 경북도청(신) (INTERCITY) — 경북도청시외버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3684901';
-- 경산 (INTERCITY) — 경산시외버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3861901';
-- 계룡금암 (INTERCITY) — 계룡금암정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3282601';
-- 고금 (INTERCITY) — 고금시외버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5913001';
-- 고령 (INTERCITY) — 고령시외버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI4013501';
-- 고북 (INTERCITY) — 고북정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3202701';
-- 고흥 (INTERCITY) — 고흥공용버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5954001';
-- 광나루역 (INTERCITY) — 광나루역 5호선
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI0496801';
-- 광릉내 (INTERCITY) — 광릉내정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI1202001';
-- 광천(전남) (INTERCITY) — 광천정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5791001';
-- 구미공단 (INTERCITY) — 구미공단매표소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3926701';
-- 구인사 (INTERCITY) — 구인사공용정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI2702001';
-- 금촌역 (INTERCITY) — 금촌고속버스정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI1093002';
-- 기지시 (INTERCITY) — 기지시버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3173301';
-- 김해공항 (INTERCITY) — 김해국제공항 국제선청사
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI4671801';
-- 김해공항(태화)국제 (INTERCITY) — 김해국제공항 국제선청사
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI4671806';
-- 김해공항국제(세인공항리무진) (INTERCITY) — 김해국제공항 국제선청사
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI4671804';
-- 남악 (INTERCITY) — 남악(도청)시외정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5856701';
-- 남청주 (INTERCITY) — 청주남부정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI2863501';
-- 내포 (INTERCITY) — 내포신도시고속시외버스정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3241601';
-- 노고단(성삼재) (INTERCITY) — 노고단(성삼재)정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5760601';
-- 노포 (INTERCITY) — 노포동 버스환승센터
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI4620402';
-- 녹동신항 (INTERCITY) — 녹동신항정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5956001';
-- 능주 (INTERCITY) — 능주버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5815301';
-- 당목 (INTERCITY) — 당목정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5913601';
-- 대구대 (INTERCITY) — 대구대학교 경산캠퍼스 시내버스정차장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3845402';
-- 대구서부 (INTERCITY) — 대구서부정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI4248201';
-- 대소 (INTERCITY) — 대소버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI2766801';
-- 동아방송대 (INTERCITY) — 동아방송대정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI1751601';
-- 동탄(자이파밀리에) (INTERCITY) — 동탄(능동마을)정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI1850701';
-- 땅끝 (INTERCITY) — 땅끝정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5906502';
-- 마전(충남) (INTERCITY) — 마전정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3271401';
-- 만리포 (INTERCITY) — 만리포시외버스정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3212301';
-- 백무동 (INTERCITY) — 백무동시외버스정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5005701';
-- 보은 (INTERCITY) — 보은시외버스공용정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI2891101';
-- 부산동래 (INTERCITY) — 동래시외버스정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI4773401';
-- 부산해운대 (INTERCITY) — 해운대시외버스정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI4809501';
-- 부산해운대(수도권) (INTERCITY) — 해운대시외버스정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI4808801';
-- 삼척 (INTERCITY) — 삼척종합버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI2592901';
-- 상봉 (INTERCITY) — 상봉임시정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI0215101';
-- 성주 (INTERCITY) — 임시성주버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI4002701';
-- 세명대 (INTERCITY) — 세명대정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI2713601';
-- 송광사 (INTERCITY) — 송광사시외버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5791301';
-- 수락산역(직통) (INTERCITY) — 수락산역정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI0162503';
-- 수안보 (INTERCITY) — 수안보시외버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI2749601';
-- 순창 (INTERCITY) — 순창공용버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5603501';
-- 순천북부 (INTERCITY) — 순천북부정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5794001';
-- 시종 (INTERCITY) — 시종공용버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5842701';
-- 신례원 (INTERCITY) — 신례원정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3242101';
-- 신양 (INTERCITY) — 신양정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3245301';
-- 신창 (INTERCITY) — 신창정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3153401';
-- 신탄진역 (INTERCITY) — 신탄진역고속버스정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3431101';
-- 쌍계사 (INTERCITY) — 쌍계사정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5230201';
-- 쏠비치 진도 (INTERCITY) — 쏠비치진도정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5893607';
-- 안계 (INTERCITY) — 안계버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3731201';
-- 안면도 (INTERCITY) — 안면버스정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3216401';
-- 안양역 (INTERCITY) — 안양역시외버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI1399201';
-- 양덕원 (INTERCITY) — 양덕원정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI2510801';
-- 양지 (INTERCITY) — 양지정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI1715801';
-- 여천 (INTERCITY) — 여천시외버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5963501';
-- 영양 (INTERCITY) — 영양버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3653301';
-- 예천삼거리 (INTERCITY) — 삼거리정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3682301';
-- 오리역 (INTERCITY) — 오리역정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI1363601';
-- 오산 (INTERCITY) — 오산역환승센터
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI1813701';
-- 오송(오스코) (INTERCITY) — KTX오송역 버스환승센터
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI2816403';
-- 옥천 (INTERCITY) — 옥천시외버스공영정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI2903301';
-- 용궁 (INTERCITY) — 용궁버스정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3685801';
-- 용원(녹산,명지) (INTERCITY) — 용원정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5160301';
-- 우석대 (INTERCITY) — 우석대정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5533801';
-- 운산 (INTERCITY) — 운산정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3194501';
-- 음암 (INTERCITY) — 음암시외버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3193601';
-- 의신 (INTERCITY) — 의신정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5230001';
-- 임원 (INTERCITY) — 임원버스정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI2595601';
-- 임자(대광) (INTERCITY) — 임자(대광)매표소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5880301';
-- 자운대 (INTERCITY) — 자운대정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3405901';
-- 잠실역 (INTERCITY) — 잠실역(중앙)시외버스정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI0550201';
-- 장승포 (INTERCITY) — 장승포시외버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5331601';
-- 장신대 (INTERCITY) — 장신대정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5535901';
-- 장항 (INTERCITY) — 장항버스공용정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3367401';
-- 전남인재개발원 (INTERCITY) — 전남인재개발원정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5924807';
-- 전도 (INTERCITY) — 전도정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5235004';
-- 전주대 (INTERCITY) — 전주대학교시외버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5506901';
-- 정산 (INTERCITY) — 정산정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3334601';
-- 주왕산 (INTERCITY) — 주왕산정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3743701';
-- 죽변 (INTERCITY) — 죽변시외버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3631601';
-- 줄포 (INTERCITY) — 줄포정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5632601';
-- 중산리(산청군) (INTERCITY) — 중산리버스정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5223604';
-- 지축역 (INTERCITY) — 지축역 3호선
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI1058501';
-- 진도항 (INTERCITY) — 진도항시외버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5894507';
-- 창기리 (INTERCITY) — 창기리정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3216201';
-- 창원남산 (INTERCITY) — 남산시외버스정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5153601';
-- 청주공항 (INTERCITY) — 청주국제공항 시외버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI2814201';
-- 청주대정류소 (INTERCITY) — 청주대정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI2848501';
-- 청주율량 (INTERCITY) — 율량정류소승차장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI2833901';
-- 평해 (INTERCITY) — 평해버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3636601';
-- 한국민속촌 (INTERCITY) — 한국민속촌정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI1707501';
-- 한서대 (INTERCITY) — 한서대정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3196201';
-- 함창 (INTERCITY) — 함창버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3711801';
-- 합천 (INTERCITY) — 합천버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5023301';
-- 해미 (INTERCITY) — 해미정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3196001';
-- 호산 (INTERCITY) — 호산버스정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI2596101';
-- 화성(청양) (INTERCITY) — 화성정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI3331201';
-- 화순 (INTERCITY) — 화순시외버스공용정류장
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5812001';
-- 화엄사 (INTERCITY) — 화엄사정류소
UPDATE bus_terminal SET is_terminal = FALSE WHERE code = 'NAI5761601';
