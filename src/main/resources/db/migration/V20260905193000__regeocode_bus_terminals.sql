-- 버스 터미널 좌표를 원천부터 다시 만든다 (#436 · #447).
--
-- **시드가 틀린 이유는 지역을 몰랐기 때문이다.** 터미널 이름만으로 지오코딩해서 동음이의에 걸렸다 —
-- 김포공항이 서울역 옆에, 광주(유·스퀘어)가 여수와 경기 광주에, 성전(강진군)이 광주 북구에 박혔다.
-- 좌표 최근접으로 출발 터미널을 고르므로, 그 하나가 지역 전체의 안내를 망가뜨린다(#443 실측).
--
-- **지역을 알 수 있었다.** TAGO 터미널 목록이 시외는 cityName 을 함께 주고, 고속은 cityCode 로
-- 걸러 받을 수 있다. 시드 주석의 '목록 API 가 코드·이름만 준다' 는 사실과 다르다.
--
-- 그래서 도시를 붙여 다시 찾고, **돌아온 주소의 시도가 그 도시와 맞는지** 확인했다(카카오 로컬).
-- 시도가 다르면 버리므로 동음이의가 구조적으로 못 들어온다.
--
-- 확인 못 한 곳은 **좌표를 비운다.** 틀린 좌표는 resolver 가 엉뚱한 곳을 답하게 하지만, 빈 좌표는
-- 최근접 탐색에서 빠질 뿐이다 — 시드가 이미 쓰던 정책이다.
--
-- 결과: 확인 314곳 · 비움 217곳 1km 넘게 이동 123 · 비움 217 · 새로 채움 3 · 원래 비어있음 256 · 제자리 확인 188
-- forward-only — 시드 파일을 고치지 않고 code(UNIQUE) 기준 UPDATE 로 되돌린다.

-- ── 확인된 좌표
-- 김포 (EXPRESS) — 김포국제공항 도심공항터미널 · 서울 강서구 하늘길 38 · 22.7km 이동
UPDATE bus_terminal SET lat = 37.56602263, lng = 126.80118526 WHERE code = 'NAEK103';
-- 김포공항 (EXPRESS) — 김포공항터미널 · 서울 강서구 공항동 1373 · 0.0km 이동
UPDATE bus_terminal SET lat = 37.55998518, lng = 126.80232745 WHERE code = 'NAEK060';
-- 김포공항 (EXPRESS) — 김포공항터미널 · 서울 강서구 공항동 1373 · 0.0km 이동
UPDATE bus_terminal SET lat = 37.55998518, lng = 126.80232745 WHERE code = 'NAEK104';
-- 독바위 (EXPRESS) — 독바위역 6호선 · 서울 은평구 불광로 지하 129-1 · 새로 채움
UPDATE bus_terminal SET lat = 37.61841416, lng = 126.93305365 WHERE code = 'NAEK480';
-- 동서울 (EXPRESS) — 동서울종합터미널 · 서울 광진구 강변역로 50 · 0.1km 이동
UPDATE bus_terminal SET lat = 37.53460761, lng = 127.09418832 WHERE code = 'NAEK030';
-- 동서울 (EXPRESS) — 동서울종합터미널 · 서울 광진구 강변역로 50 · 0.1km 이동
UPDATE bus_terminal SET lat = 37.53460761, lng = 127.09418832 WHERE code = 'NAEK031';
-- 동서울 (EXPRESS) — 동서울종합터미널 · 서울 광진구 강변역로 50 · 0.1km 이동
UPDATE bus_terminal SET lat = 37.53460761, lng = 127.09418832 WHERE code = 'NAEK032';
-- 동서울 (EXPRESS) — 동서울종합터미널 · 서울 광진구 강변역로 50 · 0.1km 이동
UPDATE bus_terminal SET lat = 37.53460761, lng = 127.09418832 WHERE code = 'NAEK035';
-- 상봉 (EXPRESS) — 상봉임시정류장 · 서울 중랑구 상봉동 160-27 · 새로 채움
UPDATE bus_terminal SET lat = 37.59650798, lng = 127.09285991 WHERE code = 'NAEK040';
-- 서울경부 (EXPRESS) — 서울고속버스터미널(경부) · 서울 서초구 신반포로 194 · 0.1km 이동
UPDATE bus_terminal SET lat = 37.50619217, lng = 127.00745096 WHERE code = 'NAEK010';
-- 서울남부 (EXPRESS) — 서울남부터미널 · 서울 서초구 효령로 292 · 0.1km 이동
UPDATE bus_terminal SET lat = 37.48456901, lng = 127.01555341 WHERE code = 'NAEK050';
-- 석계 (EXPRESS) — 석계역 1호선 · 서울 노원구 화랑로 341 · 새로 채움
UPDATE bus_terminal SET lat = 37.61500553, lng = 127.06568946 WHERE code = 'NAEK889';
-- 센트럴시티(서울) (EXPRESS) — 센트럴시티터미널(호남) · 서울 서초구 신반포로 176 · 0.2km 이동
UPDATE bus_terminal SET lat = 37.50503994, lng = 127.00425246 WHERE code = 'NAEK020';
-- 센트럴시티(서울) (EXPRESS) — 센트럴시티터미널(호남) · 서울 서초구 신반포로 176 · 0.2km 이동
UPDATE bus_terminal SET lat = 37.50503994, lng = 127.00425246 WHERE code = 'NAEK021';
-- 간성 (INTERCITY) — 간성터미널 · 강원특별자치도 고성군 간성읍 간성로 24 · 0.4km 이동
UPDATE bus_terminal SET lat = 38.37913723, lng = 128.47242349 WHERE code = 'NAI2473401';
-- 강릉 (INTERCITY) — 강릉시외버스터미널 · 강원특별자치도 강릉시 하슬라로 27 · 0.3km 이동
UPDATE bus_terminal SET lat = 37.75468042, lng = 128.87887571 WHERE code = 'NAI2551901';
-- 강진 (INTERCITY) — 강진버스여객터미널 · 전남광주통합특별시 강진군 강진읍 영랑로 35 · 0.4km 이동
UPDATE bus_terminal SET lat = 34.63855259, lng = 126.76787003 WHERE code = 'NAI5923401';
-- 강천사 (INTERCITY) — 강천사정류소 · 전북특별자치도 순창군 팔덕면 청계리 957-1 · 0.2km 이동
UPDATE bus_terminal SET lat = 35.40927699, lng = 127.07010579 WHERE code = 'NAI5602002';
-- 개양(진주) (INTERCITY) — 개양시외버스승강장 · 경남 진주시 가좌동 1397-17 · 0.0km 이동
UPDATE bus_terminal SET lat = 35.15968782, lng = 128.10659704 WHERE code = 'NAI5282202';
-- 거진 (INTERCITY) — 거진종합버스터미널 · 강원특별자치도 고성군 거진읍 거진길 21-2 · 8.3km 이동
UPDATE bus_terminal SET lat = 38.44611523, lng = 128.45402517 WHERE code = 'NAI2472501';
-- 거창 (INTERCITY) — 거창버스터미널 · 경남 거창군 거창읍 강남로 236 · 1.1km 이동
UPDATE bus_terminal SET lat = 35.68677040, lng = 127.92239356 WHERE code = 'NAI5282201';
-- 건국대(충주) (INTERCITY) — 건국터미널 · 충북 충주시 단월동 305-11 · 0.1km 이동
UPDATE bus_terminal SET lat = 36.95135874, lng = 127.90453537 WHERE code = 'NAI2747801';
-- 격포 (INTERCITY) — 격포터미널 · 전북특별자치도 부안군 변산면 격포항길 2 · 0.4km 이동
UPDATE bus_terminal SET lat = 35.62527383, lng = 126.47176205 WHERE code = 'NAI5633701';
-- 경북도청(신) (INTERCITY) — 경북도청시외버스정류장 · 경북 안동시 풍천면 갈전리 1625 · 0.9km 이동
UPDATE bus_terminal SET lat = 36.57059118, lng = 128.50019489 WHERE code = 'NAI3684901';
-- 경산 (INTERCITY) — 경산시외버스정류장 · 경북 경산시 경안로 196 · 0.5km 이동
UPDATE bus_terminal SET lat = 35.82501301, lng = 128.73752535 WHERE code = 'NAI3861901';
-- 경주고속 (INTERCITY) — 경주고속버스터미널 · 경북 경주시 태종로685번길 2 · 0.1km 이동
UPDATE bus_terminal SET lat = 35.83882821, lng = 129.20367350 WHERE code = 'NAI3815702';
-- 경주시외 (INTERCITY) — 경주시외버스터미널 · 경북 경주시 강변로 184 · 0.1km 이동
UPDATE bus_terminal SET lat = 35.83980001, lng = 129.20246875 WHERE code = 'NAI3815701';
-- 계룡금암 (INTERCITY) — 계룡금암정류소 · 충남 계룡시 금암동 166 · 0.3km 이동
UPDATE bus_terminal SET lat = 36.27252310, lng = 127.25289435 WHERE code = 'NAI3282601';
-- 고금 (INTERCITY) — 고금시외버스정류장 · 전남광주통합특별시 완도군 고금면 덕암리 756-5 · 0.2km 이동
UPDATE bus_terminal SET lat = 34.39650756, lng = 126.80301433 WHERE code = 'NAI5913001';
-- 고령 (INTERCITY) — 고령시외버스정류장 · 경북 고령군 대가야읍 중앙로 29 · 0.8km 이동
UPDATE bus_terminal SET lat = 35.73006301, lng = 128.27111564 WHERE code = 'NAI4013501';
-- 고북 (INTERCITY) — 고북정류소 · 충남 서산시 고북면 고북1로 288 · 0.1km 이동
UPDATE bus_terminal SET lat = 36.66661085, lng = 126.53313159 WHERE code = 'NAI3202701';
-- 고성 (INTERCITY) — 고성여객자동차터미널 · 경남 고성군 고성읍 송학고분로 339 · 0.9km 이동
UPDATE bus_terminal SET lat = 34.98165944, lng = 128.32706386 WHERE code = 'NAI5293101';
-- 고양종합 (INTERCITY) — 고양종합터미널 · 경기 고양시 일산동구 중앙로 1036 · 0.1km 이동
UPDATE bus_terminal SET lat = 37.64327521, lng = 126.78975883 WHERE code = 'NAI1045001';
-- 고창 (INTERCITY) — 고창임시터미널 · 전북특별자치도 고창군 고창읍 읍내리 675-2 · 0.8km 이동
UPDATE bus_terminal SET lat = 35.43800679, lng = 126.69395433 WHERE code = 'NAI5643301';
-- 고한사북공영 (INTERCITY) — 고한.사북공영버스터미널 · 강원특별자치도 정선군 고한읍 지장천로 856 · 0.0km 이동
UPDATE bus_terminal SET lat = 37.21980708, lng = 128.83460024 WHERE code = 'NAI2615501';
-- 고흥 (INTERCITY) — 고흥공용버스정류장 · 전남광주통합특별시 고흥군 고흥읍 여산당촌길 19 · 0.5km 이동
UPDATE bus_terminal SET lat = 34.60740511, lng = 127.28106074 WHERE code = 'NAI5954001';
-- 곤양 (INTERCITY) — 곤양공용터미널 · 경남 사천시 곤양면 성내로 2 · 0.0km 이동
UPDATE bus_terminal SET lat = 35.05740419, lng = 127.96089444 WHERE code = 'NAI5250401';
-- 공주 (INTERCITY) — 공주종합버스터미널 · 충남 공주시 신관로 74 · 2.8km 이동
UPDATE bus_terminal SET lat = 36.46857368, lng = 127.13475802 WHERE code = 'NAI3258501';
-- 과역 (INTERCITY) — 과역버스터미널 · 전남광주통합특별시 고흥군 과역면 무궁화길 5 · 0.3km 이동
UPDATE bus_terminal SET lat = 34.67831619, lng = 127.36117614 WHERE code = 'NAI5951101';
-- 관산 (INTERCITY) — 관산버스터미널 · 전남광주통합특별시 장흥군 관산읍 관산로 121-12 · 0.4km 이동
UPDATE bus_terminal SET lat = 34.56579520, lng = 126.93662263 WHERE code = 'NAI5935101';
-- 광나루역 (INTERCITY) — 광나루역 5호선 · 서울 광진구 아차산로 지하 571 · 0.1km 이동
UPDATE bus_terminal SET lat = 37.54529917, lng = 127.10352146 WHERE code = 'NAI0496801';
-- 광릉내 (INTERCITY) — 광릉내정류소 · 경기 남양주시 진접읍 팔야리 759-2 · 0.7km 이동
UPDATE bus_terminal SET lat = 37.74605529, lng = 127.20537015 WHERE code = 'NAI1202001';
-- 광양 (INTERCITY) — 광양터미널 · 전남광주통합특별시 광양시 광양읍 순광로 688 · 16.8km 이동
UPDATE bus_terminal SET lat = 34.96971727, lng = 127.58998374 WHERE code = 'NAI5775801';
-- 광주(경기) (INTERCITY) — 광주종합터미널 · 경기 광주시 광주대로 30 · 2.1km 이동
UPDATE bus_terminal SET lat = 37.40960883, lng = 127.26130663 WHERE code = 'NAI1275701';
-- 광주(유·스퀘어) (INTERCITY) — 유스퀘어광주종합버스터미널 · 전남광주통합특별시 서구 무진대로 904 · 84.0km 이동
UPDATE bus_terminal SET lat = 35.16040761, lng = 126.87931250 WHERE code = 'NAI6193701';
-- 광천(전남) (INTERCITY) — 광천정류소 · 전남광주통합특별시 순천시 주암면 동주로 2040-1 · 33.7km 이동
UPDATE bus_terminal SET lat = 35.07669480, lng = 127.23425217 WHERE code = 'NAI5791001';
-- 광혜원 (INTERCITY) — 광혜원시외버스터미널 · 충북 진천군 광혜원면 진광로 1563 · 0.7km 이동
UPDATE bus_terminal SET lat = 36.99325863, lng = 127.44384070 WHERE code = 'NAI2780501';
-- 괴산 (INTERCITY) — 괴산시외버스터미널 · 충북 괴산군 괴산읍 읍내로 286 · 0.4km 이동
UPDATE bus_terminal SET lat = 36.80853180, lng = 127.79459248 WHERE code = 'NAI2803301';
-- 구례 (INTERCITY) — 구례공영버스터미널 · 전남광주통합특별시 구례군 구례읍 중앙로 8 · 0.7km 이동
UPDATE bus_terminal SET lat = 35.20670460, lng = 127.46859623 WHERE code = 'NAI5765401';
-- 구미 (INTERCITY) — 구미종합터미널 · 경북 구미시 송원동로 72 · 0.9km 이동
UPDATE bus_terminal SET lat = 36.12270234, lng = 128.35208925 WHERE code = 'NAI3923301';
-- 구미공단 (INTERCITY) — 구미공단매표소 · 경북 구미시 공단동 200 · 5.1km 이동
UPDATE bus_terminal SET lat = 36.11072142, lng = 128.37828608 WHERE code = 'NAI3926701';
-- 구인사 (INTERCITY) — 구인사공용정류장 · 충북 단양군 영춘면 구인사길 60 · 0.9km 이동
UPDATE bus_terminal SET lat = 37.03649765, lng = 128.48066153 WHERE code = 'NAI2702001';
-- 구천동 (INTERCITY) — 구천동버스터미널 · 전북특별자치도 무주군 설천면 구천동1로 166 · 4.7km 이동
UPDATE bus_terminal SET lat = 35.90077433, lng = 127.77606380 WHERE code = 'NAI5555701';
-- 군북 (INTERCITY) — 군북버스터미널 · 경남 함안군 군북면 함마대로 772 · 0.3km 이동
UPDATE bus_terminal SET lat = 35.26345027, lng = 128.34345349 WHERE code = 'NAI5206501';
-- 군산 (INTERCITY) — 군산시외버스터미널 · 전북특별자치도 군산시 해망로 18 · 1.2km 이동
UPDATE bus_terminal SET lat = 35.97741721, lng = 126.72444685 WHERE code = 'NAI5403701';
-- 군위 (INTERCITY) — 군위공용버스터미널 · 대구 군위군 군위읍 중앙길 12 · 1.4km 이동
UPDATE bus_terminal SET lat = 36.22996430, lng = 128.56856367 WHERE code = 'NAI3901801';
-- 금산 (INTERCITY) — 금산시외고속버스터미널 · 충남 금산군 금산읍 후곤천길 77 · 0.4km 이동
UPDATE bus_terminal SET lat = 36.10529496, lng = 127.49075998 WHERE code = 'NAI3273501';
-- 금촌역 (INTERCITY) — 금촌고속버스정류소 · 경기 파주시 새꽃로 193 · 0.0km 이동
UPDATE bus_terminal SET lat = 37.76538887, lng = 126.77382773 WHERE code = 'NAI1093002';
-- 기지시 (INTERCITY) — 기지시버스정류장 · 충남 당진시 송악읍 반촌로 98-2 · 0.9km 이동
UPDATE bus_terminal SET lat = 36.90415581, lng = 126.69438325 WHERE code = 'NAI3173301';
-- 김제 (INTERCITY) — 김제종합버스터미널 · 전북특별자치도 김제시 동서로 241 · 1.2km 이동
UPDATE bus_terminal SET lat = 35.80343440, lng = 126.89370144 WHERE code = 'NAI5437901';
-- 김천 (INTERCITY) — 김천공용버스터미널 · 경북 김천시 자산로 152-8 · 1.8km 이동
UPDATE bus_terminal SET lat = 36.12366671, lng = 128.11832599 WHERE code = 'NAI3958601';
-- 김포공항 (INTERCITY) — 김포공항터미널 · 서울 강서구 공항동 1373 · 15.0km 이동
UPDATE bus_terminal SET lat = 37.55998518, lng = 126.80232745 WHERE code = 'NAI0750501';
-- 김포공항(도심공항) (INTERCITY) — 김포국제공항 도심공항터미널 · 서울 강서구 하늘길 38 · 15.1km 이동
UPDATE bus_terminal SET lat = 37.56602263, lng = 126.80118526 WHERE code = 'NAI0750503';
-- 김해 (INTERCITY) — 김해여객터미널 · 경남 김해시 김해대로 2232 · 1.5km 이동
UPDATE bus_terminal SET lat = 35.22786917, lng = 128.87339504 WHERE code = 'NAI5093801';
-- 김해공항 (INTERCITY) — 김해국제공항 국제선청사 · 부산 강서구 대저2동 2350-1 · 0.1km 이동
UPDATE bus_terminal SET lat = 35.17248776, lng = 128.94678530 WHERE code = 'NAI4671801';
-- 김해공항(태화)국제 (INTERCITY) — 김해국제공항 국제선청사 · 부산 강서구 대저2동 2350-1 · 0.1km 이동
UPDATE bus_terminal SET lat = 35.17248776, lng = 128.94678530 WHERE code = 'NAI4671806';
-- 김해공항국제(세인공항리무진) (INTERCITY) — 김해국제공항 국제선청사 · 부산 강서구 대저2동 2350-1 · 0.1km 이동
UPDATE bus_terminal SET lat = 35.17248776, lng = 128.94678530 WHERE code = 'NAI4671804';
-- 나로도 (INTERCITY) — 나로도공용터미널 · 전남광주통합특별시 고흥군 봉래면 축정1길 25 · 1.4km 이동
UPDATE bus_terminal SET lat = 34.46316042, lng = 127.46015965 WHERE code = 'NAI5956901';
-- 나주 (INTERCITY) — 나주버스터미널 · 전남광주통합특별시 나주시 나주로 192 · 2.1km 이동
UPDATE bus_terminal SET lat = 35.03361936, lng = 126.72149985 WHERE code = 'NAI5825501';
-- 나주혁신도시 (INTERCITY) — 나주터미널 빛가람지점 · 전남광주통합특별시 나주시 문화로 204 · 0.5km 이동
UPDATE bus_terminal SET lat = 35.01682149, lng = 126.78447688 WHERE code = 'NAI5821701';
-- 남악 (INTERCITY) — 남악(도청)시외정류소 · 전남광주통합특별시 무안군 삼향읍 남악리 1462 · 0.2km 이동
UPDATE bus_terminal SET lat = 34.81417637, lng = 126.46212526 WHERE code = 'NAI5856701';
-- 남원 (INTERCITY) — 남원공용버스터미널 · 전북특별자치도 남원시 용성로 109 · 0.7km 이동
UPDATE bus_terminal SET lat = 35.40969002, lng = 127.38797301 WHERE code = 'NAI5576001';
-- 남청주 (INTERCITY) — 청주남부정류장 · 충북 청주시 서원구 1순환로 1012 · 9.7km 이동
UPDATE bus_terminal SET lat = 36.60830782, lng = 127.47957519 WHERE code = 'NAI2863501';
-- 남해 (INTERCITY) — 남해공용터미널 · 경남 남해군 남해읍 남해대로 2835 · 12.7km 이동
UPDATE bus_terminal SET lat = 34.84183204, lng = 127.89847137 WHERE code = 'NAI5241401';
-- 내포 (INTERCITY) — 내포신도시고속시외버스정류소 · 충남 예산군 삽교읍 목리 767 · 0.2km 이동
UPDATE bus_terminal SET lat = 36.65902489, lng = 126.67052053 WHERE code = 'NAI3241601';
-- 노고단(성삼재) (INTERCITY) — 노고단(성삼재)정류소 · 전남광주통합특별시 구례군 산동면 노고단로 1068 · 0.1km 이동
UPDATE bus_terminal SET lat = 35.30597680, lng = 127.51108744 WHERE code = 'NAI5760601';
-- 노포 (INTERCITY) — 노포동 버스환승센터 · 부산 금정구 중앙대로 2238 · 0.2km 이동
UPDATE bus_terminal SET lat = 35.28325366, lng = 129.09475847 WHERE code = 'NAI4620402';
-- 녹동 (INTERCITY) — 녹동공영버스터미널 · 전남광주통합특별시 고흥군 도양읍 천마로 57 · 0.9km 이동
UPDATE bus_terminal SET lat = 34.53287468, lng = 127.13899947 WHERE code = 'NAI5955501';
-- 녹동신항 (INTERCITY) — 녹동신항정류소 · 전남광주통합특별시 고흥군 도양읍 봉암리 3907 · 0.1km 이동
UPDATE bus_terminal SET lat = 34.52336446, lng = 127.14358970 WHERE code = 'NAI5956001';
-- 논산 (INTERCITY) — 논산버스터미널 · 충남 논산시 계백로 1000 · 2.1km 이동
UPDATE bus_terminal SET lat = 36.20312149, lng = 127.08823564 WHERE code = 'NAI3295401';
-- 능주 (INTERCITY) — 능주버스정류장 · 전남광주통합특별시 화순군 능주면 죽수길 93 · 0.8km 이동
UPDATE bus_terminal SET lat = 34.99154603, lng = 126.95803393 WHERE code = 'NAI5815301';
-- 다목리 (INTERCITY) — 다목리버스터미널 · 강원특별자치도 화천군 상서면 수피령로 1297 · 3.7km 이동
UPDATE bus_terminal SET lat = 38.17560658, lng = 127.53325783 WHERE code = 'NAI2410401';
-- 다시 (INTERCITY) — 다시터미널 · 전남광주통합특별시 나주시 다시면 영산로 4526 · 28.5km 이동
UPDATE bus_terminal SET lat = 35.02000100, lng = 126.63982597 WHERE code = 'NAI5820301';
-- 단양 (INTERCITY) — 단양시외버스공용터미널 · 충북 단양군 단양읍 수변로 111 · 0.4km 이동
UPDATE bus_terminal SET lat = 36.98553151, lng = 128.37080268 WHERE code = 'NAI2701101';
-- 담양 (INTERCITY) — 담양공용버스터미널 · 전남광주통합특별시 담양군 담양읍 중앙로 22-2 · 0.7km 이동
UPDATE bus_terminal SET lat = 35.31522179, lng = 126.98383821 WHERE code = 'NAI5734401';
-- 당목 (INTERCITY) — 당목정류소 · 전남광주통합특별시 완도군 약산면 당목길 140 · 0.0km 이동
UPDATE bus_terminal SET lat = 34.37801635, lng = 126.94626064 WHERE code = 'NAI5913601';
-- 당진 (INTERCITY) — 당진버스터미널 · 충남 당진시 밤절로 149 · 19.0km 이동
UPDATE bus_terminal SET lat = 36.90245620, lng = 126.64596403 WHERE code = 'NAI3177101';
-- 대구대 (INTERCITY) — 대구대학교 경산캠퍼스 시내버스정차장 · 경북 경산시 진량읍 대구대로 201 · 75.6km 이동
UPDATE bus_terminal SET lat = 35.89741653, lng = 128.85164207 WHERE code = 'NAI3845402';
-- 대구북부 (INTERCITY) — 대구북부시외버스터미널 · 대구 서구 서대구로 295 · 6.1km 이동
UPDATE bus_terminal SET lat = 35.88421746, lng = 128.55522333 WHERE code = 'NAI4171101';
-- 대구서부 (INTERCITY) — 대구서부정류장 · 대구 남구 월배로 496 · 0.1km 이동
UPDATE bus_terminal SET lat = 35.83672828, lng = 128.55782718 WHERE code = 'NAI4248201';
-- 대소 (INTERCITY) — 대소버스정류장 · 충북 음성군 대소읍 오류리 750-1 · 1.1km 이동
UPDATE bus_terminal SET lat = 36.97065099, lng = 127.48276644 WHERE code = 'NAI2766801';
-- 대전복합 (INTERCITY) — 대전복합터미널 · 대전 동구 동서대로 1689 · 0.1km 이동
UPDATE bus_terminal SET lat = 36.35032438, lng = 127.43674997 WHERE code = 'NAI3455101';
-- 대전서남부 (INTERCITY) — 대전서남부터미널 · 대전 중구 유등천동로 346 · 3.9km 이동
UPDATE bus_terminal SET lat = 36.31287714, lng = 127.38810152 WHERE code = 'NAI3498701';
-- 대진 (INTERCITY) — 대진시외버스종합터미널 · 강원특별자치도 고성군 현내면 금강산로 196 · 0.3km 이동
UPDATE bus_terminal SET lat = 38.49247165, lng = 128.42760238 WHERE code = 'NAI2570801';
-- 독천 (INTERCITY) — 독천터미널 · 전남광주통합특별시 영암군 학산면 영산로 6 · 0.2km 이동
UPDATE bus_terminal SET lat = 34.72474793, lng = 126.56959210 WHERE code = 'NAI5843901';
-- 동두천터미널 (INTERCITY) — 동두천터미널 · 경기 동두천시 평화로2169번길 21 · 0.1km 이동
UPDATE bus_terminal SET lat = 37.88098121, lng = 127.05316102 WHERE code = 'NAI1136601';
-- 동서울 (INTERCITY) — 동서울종합터미널 · 서울 광진구 강변역로 50 · 15.1km 이동
UPDATE bus_terminal SET lat = 37.53460761, lng = 127.09418832 WHERE code = 'NAI0511601';
-- 동송 (INTERCITY) — 동송시외버스공용터미널 · 강원특별자치도 철원군 동송읍 금학로 215 · 0.7km 이동
UPDATE bus_terminal SET lat = 38.20776081, lng = 127.21803492 WHERE code = 'NAI2401401';
-- 동아방송대 (INTERCITY) — 동아방송대정류소 · 경기 안성시 삼죽면 동아예대길 47 · 0.1km 이동
UPDATE bus_terminal SET lat = 37.05699405, lng = 127.36097445 WHERE code = 'NAI1751601';
-- 동탄(자이파밀리에) (INTERCITY) — 동탄(능동마을)정류장 · 경기 화성시 동탄구 능동 1178 · 3.4km 이동
UPDATE bus_terminal SET lat = 37.21341583, lng = 127.06253557 WHERE code = 'NAI1850701';
-- 땅끝 (INTERCITY) — 땅끝정류소 · 전남광주통합특별시 해남군 송지면 땅끝마을길 70-14 · 0.3km 이동
UPDATE bus_terminal SET lat = 34.29822198, lng = 126.52935588 WHERE code = 'NAI5906502';
-- 마량 (INTERCITY) — 마량공용버스터미널 · 전남광주통합특별시 강진군 마량면 마량4길 11 · 0.1km 이동
UPDATE bus_terminal SET lat = 34.45139297, lng = 126.81727179 WHERE code = 'NAI5926901';
-- 마산 (INTERCITY) — 마산시외버스터미널 · 경남 창원시 마산회원구 3.15대로 756 · 5.0km 이동
UPDATE bus_terminal SET lat = 35.23906692, lng = 128.58346365 WHERE code = 'NAI5135601';
-- 마산남부 (INTERCITY) — 마산남부시외버스터미널 · 경남 창원시 마산합포구 월영동서로 10 · 0.0km 이동
UPDATE bus_terminal SET lat = 35.17900442, lng = 128.56038453 WHERE code = 'NAI5175001';
-- 마산역(공항리무진) (INTERCITY) — 마산시외버스터미널 · 경남 창원시 마산회원구 3.15대로 756 · 0.7km 이동
UPDATE bus_terminal SET lat = 35.23906692, lng = 128.58346365 WHERE code = 'NAI5130101';
-- 마전(충남) (INTERCITY) — 마전정류소 · 충남 금산군 추부면 마전로 55 · 0.2km 이동
UPDATE bus_terminal SET lat = 36.19103266, lng = 127.46684992 WHERE code = 'NAI3271401';
-- 만리포 (INTERCITY) — 만리포시외버스정류소 · 충남 태안군 소원면 서해로 24 · 0.1km 이동
UPDATE bus_terminal SET lat = 36.78356183, lng = 126.14324072 WHERE code = 'NAI3212301';
-- 목포 (INTERCITY) — 목포종합버스터미널 · 전남광주통합특별시 목포시 영산로 525 · 2.3km 이동
UPDATE bus_terminal SET lat = 34.81274206, lng = 126.41782065 WHERE code = 'NAI5864201';
-- 무안 (INTERCITY) — 무안버스터미널 · 전남광주통합특별시 무안군 무안읍 성동리 873-11 · 19.1km 이동
UPDATE bus_terminal SET lat = 34.98811734, lng = 126.47822059 WHERE code = 'NAI5852401';
-- 무주 (INTERCITY) — 무주공용버스터미널 · 전북특별자치도 무주군 무주읍 한풍루로 351 · 0.4km 이동
UPDATE bus_terminal SET lat = 36.00468271, lng = 127.66438191 WHERE code = 'NAI5551501';
-- 문경 (INTERCITY) — 문경버스터미널 · 경북 문경시 문경읍 새재로 458 · 17.9km 이동
UPDATE bus_terminal SET lat = 36.73380905, lng = 128.10754954 WHERE code = 'NAI3691701';
-- 문장 (INTERCITY) — 문장공영터미널 · 전남광주통합특별시 함평군 해보면 문장리 822-24 · 0.0km 이동
UPDATE bus_terminal SET lat = 35.18118512, lng = 126.60561545 WHERE code = 'NAI5711701';
-- 배둔 (INTERCITY) — 배둔시외버스터미널 · 경남 고성군 회화면 회진로 11 · 0.2km 이동
UPDATE bus_terminal SET lat = 35.05555753, lng = 128.36684894 WHERE code = 'NAI5291501';
-- 백담사입구 (INTERCITY) — 백담입구터미널 · 강원특별자치도 인제군 북면 미시령로 1142 · 1.0km 이동
UPDATE bus_terminal SET lat = 38.19712629, lng = 128.34254451 WHERE code = 'NAI2460501';
-- 백무동 (INTERCITY) — 백무동시외버스정류소 · 경남 함양군 마천면 강청리 203-4 · 0.5km 이동
UPDATE bus_terminal SET lat = 35.36313159, lng = 127.68068053 WHERE code = 'NAI5005701';
-- 벌교 (INTERCITY) — 벌교버스공용터미널 · 전남광주통합특별시 보성군 벌교읍 조정래길 2-8 · 0.8km 이동
UPDATE bus_terminal SET lat = 34.84828266, lng = 127.35109351 WHERE code = 'NAI5942301';
-- 보령 (INTERCITY) — 보령종합터미널 · 충남 보령시 터미널길 8 · 20.5km 이동
UPDATE bus_terminal SET lat = 36.34238301, lng = 126.58962744 WHERE code = 'NAI3345801';
-- 보성 (INTERCITY) — 보성버스터미널 · 전남광주통합특별시 보성군 보성읍 현충로 20 · 26.1km 이동
UPDATE bus_terminal SET lat = 34.76391237, lng = 127.07561693 WHERE code = 'NAI5945801';
-- 보은 (INTERCITY) — 보은시외버스공용정류장 · 충북 보은군 보은읍 삼산남로 8 · 0.3km 이동
UPDATE bus_terminal SET lat = 36.48314912, lng = 127.72177465 WHERE code = 'NAI2891101';
-- 부구 (INTERCITY) — 부구터미널 · 경북 울진군 북면 울진북로 2098 · 0.0km 이동
UPDATE bus_terminal SET lat = 37.10324082, lng = 129.37449651 WHERE code = 'NAI3630501';
-- 부산동래 (INTERCITY) — 동래시외버스정류소 · 부산 동래구 중앙대로1325번길 24 · 1.7km 이동
UPDATE bus_terminal SET lat = 35.20572087, lng = 129.07656883 WHERE code = 'NAI4773401';
-- 부산동부 (INTERCITY) — 부산종합버스터미널 · 부산 금정구 중앙대로 2238 · 13.3km 이동
UPDATE bus_terminal SET lat = 35.28477323, lng = 129.09547241 WHERE code = 'NAI4620401';
-- 부산서부(사상) (INTERCITY) — 부산서부버스터미널 · 부산 사상구 사상로 201 · 0.1km 이동
UPDATE bus_terminal SET lat = 35.16323947, lng = 128.98252543 WHERE code = 'NAI4696901';
-- 부산해운대 (INTERCITY) — 해운대시외버스정류소 · 부산 해운대구 우동 552-21 · 0.4km 이동
UPDATE bus_terminal SET lat = 35.16516574, lng = 129.16060099 WHERE code = 'NAI4809501';
-- 부산해운대(수도권) (INTERCITY) — 해운대시외버스정류소 · 부산 해운대구 우동 552-21 · 0.4km 이동
UPDATE bus_terminal SET lat = 35.16516574, lng = 129.16060099 WHERE code = 'NAI4808801';
-- 부안 (INTERCITY) — 부안종합버스터미널 · 전북특별자치도 부안군 부안읍 석정로 210 · 0.6km 이동
UPDATE bus_terminal SET lat = 35.72664562, lng = 126.73701372 WHERE code = 'NAI5630801';
-- 부여 (INTERCITY) — 부여시외버스터미널 · 충남 부여군 부여읍 사비로 87 · 0.5km 이동
UPDATE bus_terminal SET lat = 36.28039264, lng = 126.91030620 WHERE code = 'NAI3315201';
-- 부천 (INTERCITY) — 부천터미널소풍 · 경기 부천시 원미구 송내대로 239 · 1.7km 이동
UPDATE bus_terminal SET lat = 37.50363259, lng = 126.75671001 WHERE code = 'NAI1454501';
-- 사창리 (INTERCITY) — 사창리버스터미널 · 강원특별자치도 화천군 사내면 사내로 10-13 · 0.2km 이동
UPDATE bus_terminal SET lat = 38.07153704, lng = 127.52286356 WHERE code = 'NAI2415401';
-- 사천 (INTERCITY) — 사천시외버스터미널 · 경남 사천시 사천읍 옥산로 120 · 8.9km 이동
UPDATE bus_terminal SET lat = 35.07839794, lng = 128.09607456 WHERE code = 'NAI5251801';
-- 산양리 (INTERCITY) — 산양리터미널 · 강원특별자치도 화천군 상서면 영서로 7715 · 0.1km 이동
UPDATE bus_terminal SET lat = 38.20704988, lng = 127.66232955 WHERE code = 'NAI2410101';
-- 삼성(대소) (INTERCITY) — 삼성버스터미널 · 충북 음성군 삼성면 덕정로 76-5 · 46.1km 이동
UPDATE bus_terminal SET lat = 37.02061018, lng = 127.50016207 WHERE code = 'NAI2764801';
-- 삼척 (INTERCITY) — 삼척종합버스정류장 · 강원특별자치도 삼척시 봉황로 9-22 · 0.9km 이동
UPDATE bus_terminal SET lat = 37.44014690, lng = 129.16903789 WHERE code = 'NAI2592901';
-- 삼천포 (INTERCITY) — 삼천포터미널 · 경남 사천시 중앙로 158 · 2.9km 이동
UPDATE bus_terminal SET lat = 34.93946037, lng = 128.08038737 WHERE code = 'NAI5255901';
-- 삽교천 (INTERCITY) — 삽교천시외버스터미널 · 충남 당진시 신평면 삽교천길 103 · 35.2km 이동
UPDATE bus_terminal SET lat = 36.88844060, lng = 126.82483371 WHERE code = 'NAI3174401';
-- 상봉 (INTERCITY) — 상봉임시정류장 · 서울 중랑구 상봉동 160-27 · 0.6km 이동
UPDATE bus_terminal SET lat = 37.59650798, lng = 127.09285991 WHERE code = 'NAI0215101';
-- 상주 (INTERCITY) — 상주종합버스터미널 · 경북 상주시 삼백로 71 · 1.1km 이동
UPDATE bus_terminal SET lat = 36.41907395, lng = 128.15143898 WHERE code = 'NAI3718101';
-- 서산 (INTERCITY) — 서산공용버스터미널 · 충남 서산시 안견로 190 · 29.9km 이동
UPDATE bus_terminal SET lat = 36.78184662, lng = 126.45854902 WHERE code = 'NAI3198101';
-- 서수원 (INTERCITY) — 서수원버스터미널 · 경기 수원시 권선구 수인로 291 · 4.8km 이동
UPDATE bus_terminal SET lat = 37.28253344, lng = 126.97076045 WHERE code = 'NAI1640501';
-- 서울고속버스터미널(경부) (INTERCITY) — 서울고속버스터미널(경부) · 서울 서초구 신반포로 194 · 0.1km 이동
UPDATE bus_terminal SET lat = 37.50619217, lng = 127.00745096 WHERE code = 'NAI0654501';
-- 서울남부 (INTERCITY) — 서울남부터미널 · 서울 서초구 효령로 292 · 10.6km 이동
UPDATE bus_terminal SET lat = 37.48456901, lng = 127.01555341 WHERE code = 'NAI0671801';
-- 성남 (INTERCITY) — 성남종합버스터미널 · 경기 성남시 분당구 성남대로925번길 16 · 0.8km 이동
UPDATE bus_terminal SET lat = 37.41312486, lng = 127.12741533 WHERE code = 'NAI1349701';
-- 성전 (INTERCITY) — 성전터미널 · 전남광주통합특별시 강진군 성전면 별뫼로 379 · 57.6km 이동
UPDATE bus_terminal SET lat = 34.69138450, lng = 126.70791249 WHERE code = 'NAI5920601';
-- 성주 (INTERCITY) — 임시성주버스정류장 · 경북 성주군 성주읍 성산리 1521 · 0.9km 이동
UPDATE bus_terminal SET lat = 35.91274191, lng = 128.28327297 WHERE code = 'NAI4002701';
-- 세종시 (INTERCITY) — 세종고속시외버스터미널 · 세종특별자치시 갈매로 37-12 · 1.6km 이동
UPDATE bus_terminal SET lat = 36.46922276, lng = 127.27365746 WHERE code = 'NAI3015401';
-- 세종청사 (INTERCITY) — 세종고속시외버스터미널 · 세종특별자치시 갈매로 37-12 · 1.5km 이동
UPDATE bus_terminal SET lat = 36.46922276, lng = 127.27365746 WHERE code = 'NAI3010701';
-- 속리산 (INTERCITY) — 속리산터미널 · 충북 보은군 속리산면 법주사로 216 · 0.7km 이동
UPDATE bus_terminal SET lat = 36.52784826, lng = 127.81965472 WHERE code = 'NAI2890801';
-- 속초 (INTERCITY) — 속초시외버스터미널 · 강원특별자치도 속초시 장안로 16 · 0.5km 이동
UPDATE bus_terminal SET lat = 38.21122269, lng = 128.59081355 WHERE code = 'NAI2482701';
-- 송광사 (INTERCITY) — 송광사시외버스정류장 · 전남광주통합특별시 순천시 송광면 신평리 274-1 · 1.2km 이동
UPDATE bus_terminal SET lat = 35.00992758, lng = 127.25555109 WHERE code = 'NAI5791301';
-- 수락산역(직통) (INTERCITY) — 수락산역정류소 · 서울 노원구 동일로 지하 1662 · 0.1km 이동
UPDATE bus_terminal SET lat = 37.67714619, lng = 127.05550459 WHERE code = 'NAI0162503';
-- 수안보 (INTERCITY) — 수안보시외버스정류장 · 충북 충주시 수안보면 온천리 252-3 · 0.2km 이동
UPDATE bus_terminal SET lat = 36.84741065, lng = 127.99252736 WHERE code = 'NAI2749601';
-- 수원터미널 (INTERCITY) — 수원버스터미널 · 경기 수원시 권선구 경수대로 270 · 0.2km 이동
UPDATE bus_terminal SET lat = 37.25108002, lng = 127.01982909 WHERE code = 'NAI1658501';
-- 순창 (INTERCITY) — 순창공용버스정류장 · 전북특별자치도 순창군 순창읍 장류로 355 · 2.9km 이동
UPDATE bus_terminal SET lat = 35.37680937, lng = 127.14130868 WHERE code = 'NAI5603501';
-- 순천 (INTERCITY) — 순천종합버스터미널 · 전남광주통합특별시 순천시 장천3길 13 · 0.5km 이동
UPDATE bus_terminal SET lat = 34.94759306, lng = 127.49136191 WHERE code = 'NAI5796001';
-- 순천북부 (INTERCITY) — 순천북부정류소 · 전남광주통합특별시 순천시 매곡동 551-3 · 0.9km 이동
UPDATE bus_terminal SET lat = 34.96596842, lng = 127.48489194 WHERE code = 'NAI5794001';
-- 시종 (INTERCITY) — 시종공용버스정류장 · 전남광주통합특별시 영암군 시종면 마한로 1281-1 · 0.2km 이동
UPDATE bus_terminal SET lat = 34.86892511, lng = 126.60915526 WHERE code = 'NAI5842701';
-- 시흥 (INTERCITY) — 시흥종합버스터미널 · 경기 시흥시 옥구공원로 225 · 7.3km 이동
UPDATE bus_terminal SET lat = 37.34262047, lng = 126.73583801 WHERE code = 'NAI1506601';
-- 신남 (INTERCITY) — 신남버스터미널 · 강원특별자치도 인제군 남면 신남로 23 · 0.5km 이동
UPDATE bus_terminal SET lat = 37.96598878, lng = 128.07726627 WHERE code = 'NAI2464801';
-- 신례원 (INTERCITY) — 신례원정류소 · 충남 예산군 예산읍 신례원리 253-8 · 0.0km 이동
UPDATE bus_terminal SET lat = 36.72722062, lng = 126.84527698 WHERE code = 'NAI3242101';
-- 신양 (INTERCITY) — 신양정류소 · 충남 예산군 신양면 청신로 386 · 0.2km 이동
UPDATE bus_terminal SET lat = 36.60283161, lng = 126.86907711 WHERE code = 'NAI3245301';
-- 신창 (INTERCITY) — 신창정류소 · 충남 아산시 신창면 읍내리 481-1 · 1.8km 이동
UPDATE bus_terminal SET lat = 36.77733727, lng = 126.93508610 WHERE code = 'NAI3153401';
-- 신탄진역 (INTERCITY) — 신탄진역고속버스정류소 · 대전 대덕구 신탄진로 806 · 0.1km 이동
UPDATE bus_terminal SET lat = 36.44937136, lng = 127.42933580 WHERE code = 'NAI3431101';
-- 신태인 (INTERCITY) — 신태인공영터미널 · 전북특별자치도 정읍시 신태인읍 서태길 29 · 0.2km 이동
UPDATE bus_terminal SET lat = 35.69034874, lng = 126.88728149 WHERE code = 'NAI5610601';
-- 쌍계사 (INTERCITY) — 쌍계사정류소 · 경남 하동군 화개면 용강리 400-6 · 0.5km 이동
UPDATE bus_terminal SET lat = 35.23127133, lng = 127.64245213 WHERE code = 'NAI5230201';
-- 쏠비치 진도 (INTERCITY) — 쏠비치진도정류장 · 전남광주통합특별시 진도군 의신면 송군길 30-40 · 0.1km 이동
UPDATE bus_terminal SET lat = 34.40039516, lng = 126.32765191 WHERE code = 'NAI5893607';
-- 아산(온양) (INTERCITY) — 아산고속버스터미널 · 충남 아산시 번영로 223 · 0.4km 이동
UPDATE bus_terminal SET lat = 36.78446199, lng = 127.01535027 WHERE code = 'NAI3151704';
-- 안계 (INTERCITY) — 안계버스정류장 · 경북 의성군 안계면 용기4길 18 · 0.9km 이동
UPDATE bus_terminal SET lat = 36.38951673, lng = 128.43357948 WHERE code = 'NAI3731201';
-- 안동 (INTERCITY) — 안동터미널 · 경북 안동시 경동로 130 · 4.9km 이동
UPDATE bus_terminal SET lat = 36.57437126, lng = 128.67612823 WHERE code = 'NAI3663601';
-- 안면도 (INTERCITY) — 안면버스정류소 · 충남 태안군 안면읍 장터로 126-1 · 9.0km 이동
UPDATE bus_terminal SET lat = 36.51830026, lng = 126.34563787 WHERE code = 'NAI3216401';
-- 안산터미널 (INTERCITY) — 안산버스터미널 · 경기 안산시 상록구 항가울로 410 · 0.1km 이동
UPDATE bus_terminal SET lat = 37.31684779, lng = 126.84626978 WHERE code = 'NAI1529901';
-- 안성 (INTERCITY) — 안성종합버스터미널 · 경기 안성시 비봉로 85 · 1.3km 이동
UPDATE bus_terminal SET lat = 37.01223615, lng = 127.29360820 WHERE code = 'NAI1758501';
-- 안양역 (INTERCITY) — 안양역시외버스정류장 · 경기 안양시 만안구 만안로223번길 25 · 0.1km 이동
UPDATE bus_terminal SET lat = 37.40113627, lng = 126.92170148 WHERE code = 'NAI1399201';
-- 안중 (INTERCITY) — 안중버스터미널 · 경기 평택시 안중읍 안현로서9길 14-4 · 1.1km 이동
UPDATE bus_terminal SET lat = 36.97767743, lng = 126.92334932 WHERE code = 'NAI1794301';
-- 양구 (INTERCITY) — 양구시외버스터미널 · 강원특별자치도 양구군 양구읍 중심로 196 · 0.2km 이동
UPDATE bus_terminal SET lat = 38.10770684, lng = 127.98940431 WHERE code = 'NAI2452401';
-- 양구정중앙 (INTERCITY) — 양구정중앙터미널 · 강원특별자치도 양구군 국토정중앙면 정중앙로 610 · 0.1km 이동
UPDATE bus_terminal SET lat = 38.10809327, lng = 128.03326023 WHERE code = 'NAI2455401';
-- 양덕원 (INTERCITY) — 양덕원정류소 · 강원특별자치도 홍천군 남면 양덕원로 82 · 0.1km 이동
UPDATE bus_terminal SET lat = 37.61658750, lng = 127.76836203 WHERE code = 'NAI2510801';
-- 양산 (INTERCITY) — 양산시외버스터미널 · 경남 양산시 양산역1길 7 · 1.0km 이동
UPDATE bus_terminal SET lat = 35.33564641, lng = 129.02676168 WHERE code = 'NAI5062901';
-- 양양 (INTERCITY) — 양양종합여객터미널 · 강원특별자치도 양양군 양양읍 동해대로 2700 · 1.5km 이동
UPDATE bus_terminal SET lat = 38.08529403, lng = 128.62977670 WHERE code = 'NAI2503101';
-- 양지 (INTERCITY) — 양지정류소 · 경기 용인시 처인구 양지읍 양대로 7 · 0.5km 이동
UPDATE bus_terminal SET lat = 37.23384049, lng = 127.28577016 WHERE code = 'NAI1715801';
-- 여수 (INTERCITY) — 여수종합버스터미널 · 전남광주통합특별시 여수시 좌수영로 268 · 5.0km 이동
UPDATE bus_terminal SET lat = 34.75820302, lng = 127.71699693 WHERE code = 'NAI5971501';
-- 여주 (INTERCITY) — 여주종합터미널 · 경기 여주시 세종로 85 · 5.9km 이동
UPDATE bus_terminal SET lat = 37.29017003, lng = 127.63520963 WHERE code = 'NAI1263101';
-- 여천 (INTERCITY) — 여천시외버스정류장 · 전남광주통합특별시 여수시 선원동 1287-1 · 0.8km 이동
UPDATE bus_terminal SET lat = 34.77768385, lng = 127.65141473 WHERE code = 'NAI5963501';
-- 연천 (INTERCITY) — 연천공영버스터미널 · 경기 연천군 연천읍 연천역로 29 · 0.6km 이동
UPDATE bus_terminal SET lat = 38.10134825, lng = 127.07841849 WHERE code = 'NAI1100001';
-- 영광 (INTERCITY) — 영광종합터미널 · 전남광주통합특별시 영광군 영광읍 신남로 180 · 10.4km 이동
UPDATE bus_terminal SET lat = 35.27587406, lng = 126.50205377 WHERE code = 'NAI5704301';
-- 영덕 (INTERCITY) — 영덕터미널 · 경북 영덕군 영덕읍 군청길 58 · 0.6km 이동
UPDATE bus_terminal SET lat = 36.41449104, lng = 129.37264184 WHERE code = 'NAI3643101';
-- 영양 (INTERCITY) — 영양버스정류장 · 경북 영양군 영양읍 시장길 1-10 · 0.5km 이동
UPDATE bus_terminal SET lat = 36.66175509, lng = 129.11427048 WHERE code = 'NAI3653301';
-- 영월 (INTERCITY) — 영월버스터미널 · 강원특별자치도 영월군 영월읍 중앙1로 23 · 0.4km 이동
UPDATE bus_terminal SET lat = 37.18324208, lng = 128.46563099 WHERE code = 'NAI2623601';
-- 영주 (INTERCITY) — 영주종합터미널 · 경북 영주시 대학로 352 · 10.6km 이동
UPDATE bus_terminal SET lat = 36.82709934, lng = 128.60576011 WHERE code = 'NAI3607801';
-- 영천 (INTERCITY) — 영천버스터미널 · 경북 영천시 강변로 44 · 1.7km 이동
UPDATE bus_terminal SET lat = 35.96066762, lng = 128.92753286 WHERE code = 'NAI3888501';
-- 영해 (INTERCITY) — 영해버스터미널 · 경북 영덕군 영해면 예주시장길 5 · 0.3km 이동
UPDATE bus_terminal SET lat = 36.53853276, lng = 129.40458956 WHERE code = 'NAI3641101';
-- 예산 (INTERCITY) — 예산종합터미널 · 충남 예산군 예산읍 금오대로 35-14 · 2.0km 이동
UPDATE bus_terminal SET lat = 36.69446343, lng = 126.83234898 WHERE code = 'NAI3242801';
-- 예천 (INTERCITY) — 예천시외버스터미널 · 경북 예천군 예천읍 충효로 165 · 1.3km 이동
UPDATE bus_terminal SET lat = 36.64834705, lng = 128.44320494 WHERE code = 'NAI3682601';
-- 예천삼거리 (INTERCITY) — 삼거리정류소 · 경북 예천군 감천면 충효로 1078 · 12.8km 이동
UPDATE bus_terminal SET lat = 36.70116509, lng = 128.50445112 WHERE code = 'NAI3682301';
-- 오리역 (INTERCITY) — 오리역정류장 · 경기 성남시 분당구 구미동 197-1 · 0.3km 이동
UPDATE bus_terminal SET lat = 37.33736964, lng = 127.10916769 WHERE code = 'NAI1363601';
-- 오산 (INTERCITY) — 오산역환승센터 · 경기 오산시 역광장로 59 · 1.0km 이동
UPDATE bus_terminal SET lat = 37.14635067, lng = 127.06683979 WHERE code = 'NAI1813701';
-- 오색 (INTERCITY) — 오색버스터미널 · 강원특별자치도 양양군 서면 대청봉길 122 · 4.7km 이동
UPDATE bus_terminal SET lat = 38.07716620, lng = 128.45378947 WHERE code = 'NAI2503304';
-- 오송(오스코) (INTERCITY) — KTX오송역 버스환승센터 · 충북 청주시 흥덕구 오송읍 오송가락로 123 · 0.3km 이동
UPDATE bus_terminal SET lat = 36.61919284, lng = 127.32692319 WHERE code = 'NAI2816403';
-- 옥과 (INTERCITY) — 옥과터미널 · 전남광주통합특별시 곡성군 옥과면 대학로 156-1 · 0.4km 이동
UPDATE bus_terminal SET lat = 35.27641431, lng = 127.13659134 WHERE code = 'NAI5750401';
-- 옥천 (INTERCITY) — 옥천시외버스공영정류소 · 충북 옥천군 옥천읍 삼양로 26 · 0.8km 이동
UPDATE bus_terminal SET lat = 36.30700525, lng = 127.56429464 WHERE code = 'NAI2903301';
-- 온정 (INTERCITY) — 온정종합터미널 · 경북 울진군 온정면 백암온천로 1298-4 · 0.1km 이동
UPDATE bus_terminal SET lat = 36.72078254, lng = 129.34448585 WHERE code = 'NAI3635801';
-- 와수리 (INTERCITY) — 와수터미널 · 강원특별자치도 철원군 서면 와수로173번길 21 · 0.3km 이동
UPDATE bus_terminal SET lat = 38.23873777, lng = 127.43725463 WHERE code = 'NAI2405901';
-- 완도 (INTERCITY) — 완도공용버스터미널 · 전남광주통합특별시 완도군 완도읍 개포로130번길 20 · 1.2km 이동
UPDATE bus_terminal SET lat = 34.31857143, lng = 126.74495481 WHERE code = 'NAI5911401';
-- 용궁 (INTERCITY) — 용궁버스정류소 · 경북 예천군 용궁면 용궁로 150 · 0.4km 이동
UPDATE bus_terminal SET lat = 36.60758234, lng = 128.28191630 WHERE code = 'NAI3685801';
-- 용문 (INTERCITY) — 용문버스터미널 · 경기 양평군 용문면 용문로 430 · 0.4km 이동
UPDATE bus_terminal SET lat = 37.48915791, lng = 127.59912606 WHERE code = 'NAI1252101';
-- 용원(녹산,명지) (INTERCITY) — 용원정류소 · 경남 창원시 진해구 용원로 114 · 0.4km 이동
UPDATE bus_terminal SET lat = 35.09783706, lng = 128.82144848 WHERE code = 'NAI5160301';
-- 용인 (INTERCITY) — 용인공영버스터미널 · 경기 용인시 처인구 중부대로 1486 · 2.9km 이동
UPDATE bus_terminal SET lat = 37.23287601, lng = 127.20970248 WHERE code = 'NAI1706301';
-- 우석대 (INTERCITY) — 우석대정류소 · 전북특별자치도 완주군 삼례읍 삼례로 443 · 0.1km 이동
UPDATE bus_terminal SET lat = 35.91480391, lng = 127.06668249 WHERE code = 'NAI5533801';
-- 운산 (INTERCITY) — 운산정류소 · 충남 서산시 운산면 오리안2길 1 · 16.1km 이동
UPDATE bus_terminal SET lat = 36.81126845, lng = 126.58464849 WHERE code = 'NAI3194501';
-- 울산 (INTERCITY) — 울산시외버스터미널 · 울산 남구 화합로 133 · 2.5km 이동
UPDATE bus_terminal SET lat = 35.53656233, lng = 129.33972853 WHERE code = 'NAI4472001';
-- 울산(태화) (INTERCITY) — 울산시외버스터미널 · 울산 남구 화합로 133 · 2.5km 이동
UPDATE bus_terminal SET lat = 35.53656233, lng = 129.33972853 WHERE code = 'NAI4467901';
-- 울진 (INTERCITY) — 울진종합버스터미널 · 경북 울진군 울진읍 울진중앙로 18 · 29.5km 이동
UPDATE bus_terminal SET lat = 36.98372954, lng = 129.39722254 WHERE code = 'NAI3632601';
-- 원주 (INTERCITY) — 원주종합버스터미널 · 강원특별자치도 원주시 서원대로 171 · 0.9km 이동
UPDATE bus_terminal SET lat = 37.34491889, lng = 127.93071661 WHERE code = 'NAI2638201';
-- 원통 (INTERCITY) — 원통버스터미널 · 강원특별자치도 인제군 북면 원통로147번길 31 · 0.2km 이동
UPDATE bus_terminal SET lat = 38.12331814, lng = 128.20295405 WHERE code = 'NAI2461201';
-- 유성복합 (INTERCITY) — 유성복합터미널 · 대전 유성구 유성대로 693 · 6.2km 이동
UPDATE bus_terminal SET lat = 36.35553950, lng = 127.33033493 WHERE code = 'NAI3417501';
-- 율포(전남) (INTERCITY) — 율포터미널 · 전남광주통합특별시 보성군 회천면 우암길 4 · 0.1km 이동
UPDATE bus_terminal SET lat = 34.67046415, lng = 127.08671435 WHERE code = 'NAI5947101';
-- 음성 (INTERCITY) — 음성공용버스터미널 · 충북 음성군 음성읍 수정로 31 · 0.5km 이동
UPDATE bus_terminal SET lat = 36.93442932, lng = 127.68978146 WHERE code = 'NAI2769501';
-- 음암 (INTERCITY) — 음암시외버스정류장 · 충남 서산시 음암면 운암로 389 · 0.1km 이동
UPDATE bus_terminal SET lat = 36.79960488, lng = 126.51875409 WHERE code = 'NAI3193601';
-- 의령 (INTERCITY) — 의령버스터미널 · 경남 의령군 의령읍 의병로6길 13 · 0.7km 이동
UPDATE bus_terminal SET lat = 35.32050580, lng = 128.25292400 WHERE code = 'NAI5213801';
-- 의신 (INTERCITY) — 의신정류소 · 경남 하동군 화개면 화개로 1420 · 83.9km 이동
UPDATE bus_terminal SET lat = 35.28891967, lng = 127.64726924 WHERE code = 'NAI5230001';
-- 의정부 (INTERCITY) — 의정부시외버스터미널 · 경기 의정부시 동일로 640 · 1.9km 이동
UPDATE bus_terminal SET lat = 37.74521676, lng = 127.05506977 WHERE code = 'NAI1174901';
-- 이월(충북) (INTERCITY) — 이월터미널 · 충북 진천군 이월면 진광로 793 · 2.4km 이동
UPDATE bus_terminal SET lat = 36.93100542, lng = 127.42870164 WHERE code = 'NAI2782001';
-- 이천 (INTERCITY) — 이천터미널 · 경기 이천시 이섭대천로 1200 · 0.9km 이동
UPDATE bus_terminal SET lat = 37.27742062, lng = 127.44693565 WHERE code = 'NAI1737301';
-- 익산 (INTERCITY) — 익산시외고속버스터미널 · 전북특별자치도 익산시 익산대로 52 · 2.2km 이동
UPDATE bus_terminal SET lat = 35.93129575, lng = 126.94378771 WHERE code = 'NAI5467401';
-- 인월 (INTERCITY) — 인월지리산공용터미널 · 전북특별자치도 남원시 인월면 인월로 73 · 0.5km 이동
UPDATE bus_terminal SET lat = 35.46146395, lng = 127.60332747 WHERE code = 'NAI5571501';
-- 인제 (INTERCITY) — 인제터미널 · 강원특별자치도 인제군 인제읍 비봉로 43 · 6.5km 이동
UPDATE bus_terminal SET lat = 38.06582542, lng = 128.17484189 WHERE code = 'NAI2463501';
-- 인천 (INTERCITY) — 인천종합터미널 · 인천 미추홀구 연남로 35 · 8.3km 이동
UPDATE bus_terminal SET lat = 37.44177794, lng = 126.70148313 WHERE code = 'NAI2224201';
-- 일동 (INTERCITY) — 일동버스터미널 · 경기 포천시 일동면 화동로1051번길 4-4 · 85.0km 이동
UPDATE bus_terminal SET lat = 37.95745121, lng = 127.31748923 WHERE code = 'NAI1111601';
-- 임실 (INTERCITY) — 임실공용터미널 · 전북특별자치도 임실군 임실읍 운수로 20 · 11.0km 이동
UPDATE bus_terminal SET lat = 35.61499158, lng = 127.28281671 WHERE code = 'NAI5592801';
-- 임원 (INTERCITY) — 임원버스정류소 · 강원특별자치도 삼척시 원덕읍 삼척로 1216 · 0.4km 이동
UPDATE bus_terminal SET lat = 37.23318983, lng = 129.34338980 WHERE code = 'NAI2595601';
-- 임자(대광) (INTERCITY) — 임자(대광)매표소 · 전남광주통합특별시 신안군 임자면 대기리 2523-32 · 4.7km 이동
UPDATE bus_terminal SET lat = 35.10182613, lng = 126.07349163 WHERE code = 'NAI5880301';
-- 자운대 (INTERCITY) — 자운대정류소 · 대전 유성구 신봉동 7-6 · 2.3km 이동
UPDATE bus_terminal SET lat = 36.41294430, lng = 127.34116632 WHERE code = 'NAI3405901';
-- 잠실역 (INTERCITY) — 잠실역(중앙)시외버스정류소 · 서울 송파구 잠실동 31 · 5.8km 이동
UPDATE bus_terminal SET lat = 37.51384226, lng = 127.09962772 WHERE code = 'NAI0550201';
-- 장성사거리 (INTERCITY) — 장성사거리버스여객터미널 · 전남광주통합특별시 장성군 북이면 백양로 3 · 0.2km 이동
UPDATE bus_terminal SET lat = 35.43041523, lng = 126.80988161 WHERE code = 'NAI5720101';
-- 장수 (INTERCITY) — 장수공용버스터미널 · 전북특별자치도 장수군 장수읍 장천로 175 · 0.3km 이동
UPDATE bus_terminal SET lat = 35.64805387, lng = 127.51842363 WHERE code = 'NAI5563201';
-- 장승포 (INTERCITY) — 장승포시외버스정류장 · 경남 거제시 옥수로2길 10 · 0.9km 이동
UPDATE bus_terminal SET lat = 34.87538829, lng = 128.73087520 WHERE code = 'NAI5331601';
-- 장신대 (INTERCITY) — 장신대정류소 · 전북특별자치도 완주군 상관면 왜목로 726-15 · 10.2km 이동
UPDATE bus_terminal SET lat = 35.76430086, lng = 127.20496005 WHERE code = 'NAI5535901';
-- 장항 (INTERCITY) — 장항버스공용정류장 · 충남 서천군 장항읍 장서로 78 · 0.4km 이동
UPDATE bus_terminal SET lat = 36.01471226, lng = 126.69906952 WHERE code = 'NAI3367401';
-- 장흥 (INTERCITY) — 장흥시외버스터미널 · 전남광주통합특별시 장흥군 장흥읍 중앙로1길 8 · 0.6km 이동
UPDATE bus_terminal SET lat = 34.67729611, lng = 126.90957512 WHERE code = 'NAI5932401';
-- 전남인재개발원 (INTERCITY) — 전남인재개발원정류소 · 전남광주통합특별시 강진군 도암면 만덕리 437-16 · 0.2km 이동
UPDATE bus_terminal SET lat = 34.57421685, lng = 126.74742428 WHERE code = 'NAI5924807';
-- 전도 (INTERCITY) — 전도정류소 · 경남 하동군 금남면 계천리 264-5 · 26.7km 이동
UPDATE bus_terminal SET lat = 34.99733343, lng = 127.80776693 WHERE code = 'NAI5235004';
-- 전주대 (INTERCITY) — 전주대학교시외버스정류장 · 전북특별자치도 전주시 완산구 천잠로 303 · 5.7km 이동
UPDATE bus_terminal SET lat = 35.81655700, lng = 127.08939932 WHERE code = 'NAI5506901';
-- 전주시외터미널 (INTERCITY) — 전주시외버스공용터미널 · 전북특별자치도 전주시 덕진구 가리내로 30 · 0.0km 이동
UPDATE bus_terminal SET lat = 35.83437999, lng = 127.13267242 WHERE code = 'NAI5493301';
-- 점촌 (INTERCITY) — 점촌터미널 · 경북 문경시 모전로 54 · 0.4km 이동
UPDATE bus_terminal SET lat = 36.58612218, lng = 128.19233906 WHERE code = 'NAI3695102';
-- 정산 (INTERCITY) — 정산정류소 · 충남 청양군 정산면 칠갑산로 1912 · 2.5km 이동
UPDATE bus_terminal SET lat = 36.41182881, lng = 126.95001593 WHERE code = 'NAI3334601';
-- 정선 (INTERCITY) — 정선공영버스터미널 · 강원특별자치도 정선군 정선읍 정선로 1226 · 24.1km 이동
UPDATE bus_terminal SET lat = 37.37888132, lng = 128.65029260 WHERE code = 'NAI2613201';
-- 정읍 (INTERCITY) — 정읍공용버스터미널 · 전북특별자치도 정읍시 중앙로 32 · 0.9km 이동
UPDATE bus_terminal SET lat = 35.57299966, lng = 126.84544708 WHERE code = 'NAI5615801';
-- 제주종합 (INTERCITY) — 제주시버스터미널 · 제주특별자치도 제주시 서광로 174 · 0.0km 이동
UPDATE bus_terminal SET lat = 33.49972578, lng = 126.51488042 WHERE code = 'NAI6314601';
-- 제천 (INTERCITY) — 제천버스터미널 · 충북 제천시 칠성로10길 21 · 2.2km 이동
UPDATE bus_terminal SET lat = 37.14208645, lng = 128.21085121 WHERE code = 'NAI2716501';
-- 조치원 (INTERCITY) — 조치원버스터미널 · 세종특별자치시 조치원읍 조치원로 54 · 0.4km 이동
UPDATE bus_terminal SET lat = 36.60197325, lng = 127.30260880 WHERE code = 'NAI3002601';
-- 주문진 (INTERCITY) — 주문진공영버스터미널 · 강원특별자치도 강릉시 주문진읍 주문로 6 · 1.0km 이동
UPDATE bus_terminal SET lat = 37.88437030, lng = 128.82552236 WHERE code = 'NAI2541901';
-- 주왕산 (INTERCITY) — 주왕산정류소 · 경북 청송군 주왕산면 공원길 146 · 0.2km 이동
UPDATE bus_terminal SET lat = 36.39064456, lng = 129.14193526 WHERE code = 'NAI3743701';
-- 죽변 (INTERCITY) — 죽변시외버스정류장 · 경북 울진군 죽변면 죽변중앙로 74 · 0.2km 이동
UPDATE bus_terminal SET lat = 37.05406611, lng = 129.41493179 WHERE code = 'NAI3631601';
-- 줄포 (INTERCITY) — 줄포정류소 · 전북특별자치도 부안군 줄포면 줄포리 408-6 · 2.3km 이동
UPDATE bus_terminal SET lat = 35.59073611, lng = 126.67770785 WHERE code = 'NAI5632601';
-- 중산리(산청군) (INTERCITY) — 중산리버스정류소 · 경남 산청군 시천면 지리산대로 518 · 0.1km 이동
UPDATE bus_terminal SET lat = 35.29404617, lng = 127.75432867 WHERE code = 'NAI5223604';
-- 증평 (INTERCITY) — 증평시외버스터미널 · 충북 증평군 증평읍 광장로 89 · 0.1km 이동
UPDATE bus_terminal SET lat = 36.78584808, lng = 127.58252912 WHERE code = 'NAI2793101';
-- 지축역 (INTERCITY) — 지축역 3호선 · 경기 고양시 덕양구 삼송로 300 · 0.0km 이동
UPDATE bus_terminal SET lat = 37.64814864, lng = 126.91385427 WHERE code = 'NAI1058501';
-- 진도 (INTERCITY) — 진도공용터미널 · 전남광주통합특별시 진도군 진도읍 남문길 5 · 0.3km 이동
UPDATE bus_terminal SET lat = 34.47873407, lng = 126.26349992 WHERE code = 'NAI5892201';
-- 진도항 (INTERCITY) — 진도항시외버스정류장 · 전남광주통합특별시 진도군 임회면 진도항길 90 · 0.1km 이동
UPDATE bus_terminal SET lat = 34.37478567, lng = 126.13489210 WHERE code = 'NAI5894507';
-- 진보 (INTERCITY) — 진보버스터미널 · 경북 청송군 진보면 진보로 110-15 · 0.4km 이동
UPDATE bus_terminal SET lat = 36.52881858, lng = 129.04269779 WHERE code = 'NAI3740901';
-- 진영 (INTERCITY) — 진영시외버스터미널 · 경남 김해시 진영읍 진영로 215 · 3.3km 이동
UPDATE bus_terminal SET lat = 35.30275011, lng = 128.73649210 WHERE code = 'NAI5086801';
-- 진주 (INTERCITY) — 진주고속버스터미널 · 경남 진주시 동진로 16 · 1.3km 이동
UPDATE bus_terminal SET lat = 35.17869800, lng = 128.09300646 WHERE code = 'NAI5275901';
-- 진주 (INTERCITY) — 진주고속버스터미널 · 경남 진주시 동진로 16 · 1.3km 이동
UPDATE bus_terminal SET lat = 35.17869800, lng = 128.09300646 WHERE code = 'NAI5275907';
-- 진주(경남) (INTERCITY) — 진주시외버스터미널 · 경남 진주시 남강로 712 · 2.1km 이동
UPDATE bus_terminal SET lat = 35.19132007, lng = 128.08937667 WHERE code = 'NAI5275905';
-- 진주(경원) (INTERCITY) — 진주시외버스터미널 · 경남 진주시 남강로 712 · 2.1km 이동
UPDATE bus_terminal SET lat = 35.19132007, lng = 128.08937667 WHERE code = 'NAI5275910';
-- 진주(경전) (INTERCITY) — 진주시외버스터미널 · 경남 진주시 남강로 712 · 2.1km 이동
UPDATE bus_terminal SET lat = 35.19132007, lng = 128.08937667 WHERE code = 'NAI5275903';
-- 진주(부산교통) (INTERCITY) — 진주시외버스터미널 · 경남 진주시 남강로 712 · 2.1km 이동
UPDATE bus_terminal SET lat = 35.19132007, lng = 128.08937667 WHERE code = 'NAI5275908';
-- 진천 (INTERCITY) — 진천종합터미널 · 충북 진천군 진천읍 중앙북1길 3 · 0.6km 이동
UPDATE bus_terminal SET lat = 36.85959371, lng = 127.43888714 WHERE code = 'NAI2783101';
-- 진해 (INTERCITY) — 진해시외버스터미널 · 경남 창원시 진해구 태평로34번길 17 · 5.3km 이동
UPDATE bus_terminal SET lat = 35.14464765, lng = 128.66153165 WHERE code = 'NAI5170301';
-- 창기리 (INTERCITY) — 창기리정류소 · 충남 태안군 안면읍 안면대로 2119 · 0.2km 이동
UPDATE bus_terminal SET lat = 36.57489736, lng = 126.33465414 WHERE code = 'NAI3216201';
-- 창원 (INTERCITY) — 창원종합버스터미널 · 경남 창원시 의창구 창원대로 371 · 3.8km 이동
UPDATE bus_terminal SET lat = 35.23633077, lng = 128.63936569 WHERE code = 'NAI5139301';
-- 창원남산 (INTERCITY) — 남산시외버스정류소 · 경남 창원시 성산구 정동로162번길 69 · 0.0km 이동
UPDATE bus_terminal SET lat = 35.20198236, lng = 128.69749597 WHERE code = 'NAI5153601';
-- 천안 (INTERCITY) — 천안시외버스터미널 · 충남 천안시 동남구 만남로 43 · 3.6km 이동
UPDATE bus_terminal SET lat = 36.81973608, lng = 127.15633919 WHERE code = 'NAI3112001';
-- 청송 (INTERCITY) — 청송버스터미널 · 경북 청송군 청송읍 중앙로 184 · 0.6km 이동
UPDATE bus_terminal SET lat = 36.43742300, lng = 129.05099146 WHERE code = 'NAI3743101';
-- 청양 (INTERCITY) — 청양시외버스터미널 · 충남 청양군 청양읍 중앙로 142 · 11.0km 이동
UPDATE bus_terminal SET lat = 36.45224910, lng = 126.80353307 WHERE code = 'NAI3332601';
-- 청주 (INTERCITY) — 청주시외버스터미널 · 충북 청주시 흥덕구 풍산로 6 · 5.1km 이동
UPDATE bus_terminal SET lat = 36.62533754, lng = 127.43170159 WHERE code = 'NAI2839701';
-- 청주공항 (INTERCITY) — 청주국제공항 시외버스정류장 · 충북 청주시 청원구 내수읍 오창대로 980 · 0.0km 이동
UPDATE bus_terminal SET lat = 36.72205822, lng = 127.49527212 WHERE code = 'NAI2814201';
-- 청주대정류소 (INTERCITY) — 청주대정류소 · 충북 청주시 청원구 직지대로 874-2 · 0.0km 이동
UPDATE bus_terminal SET lat = 36.65045243, lng = 127.48698146 WHERE code = 'NAI2848501';
-- 청주북부터미널 (INTERCITY) — 청주북부터미널 · 충북 청주시 청원구 오창읍 오창공원로 133 · 0.0km 이동
UPDATE bus_terminal SET lat = 36.71173817, lng = 127.42725314 WHERE code = 'NAI2812001';
-- 청주율량 (INTERCITY) — 율량정류소승차장 · 충북 청주시 청원구 율량동 1101 · 0.6km 이동
UPDATE bus_terminal SET lat = 36.67005169, lng = 127.48437885 WHERE code = 'NAI2833901';
-- 춘양 (INTERCITY) — 춘양공용버스터미널 · 경북 봉화군 춘양면 의양로5길 3 · 0.0km 이동
UPDATE bus_terminal SET lat = 36.93508239, lng = 128.91346060 WHERE code = 'NAI3621401';
-- 춘천 (INTERCITY) — 춘천고속버스터미널 · 강원특별자치도 춘천시 터미널길14번길 15 · 2.0km 이동
UPDATE bus_terminal SET lat = 37.86469440, lng = 127.71752939 WHERE code = 'NAI2443501';
-- 충북혁신도시 (INTERCITY) — 충북혁신도시공용터미널 · 충북 음성군 맹동면 원중로 1363 · 1.0km 이동
UPDATE bus_terminal SET lat = 36.90575682, lng = 127.54720625 WHERE code = 'NAI2773901';
-- 충주 (INTERCITY) — 충주공용버스터미널 · 충북 충주시 봉계1길 49 · 1.3km 이동
UPDATE bus_terminal SET lat = 36.98204148, lng = 127.91488549 WHERE code = 'NAI2736001';
-- 태백 (INTERCITY) — 태백버스터미널 · 강원특별자치도 태백시 광장로 6 · 1.4km 이동
UPDATE bus_terminal SET lat = 37.17676283, lng = 128.98522153 WHERE code = 'NAI2600701';
-- 태안 (INTERCITY) — 태안버스터미널 · 충남 태안군 태안읍 동백로 304 · 0.4km 이동
UPDATE bus_terminal SET lat = 36.74805446, lng = 126.30323819 WHERE code = 'NAI3214401';
-- 통영터미널 (INTERCITY) — 통영종합버스터미널 · 경남 통영시 광도면 죽림4로 24 · 5.0km 이동
UPDATE bus_terminal SET lat = 34.88510268, lng = 128.41692892 WHERE code = 'NAI5302001';
-- 평창 (INTERCITY) — 평창버스터미널 · 강원특별자치도 평창군 평창읍 백오1길 3 · 60.5km 이동
UPDATE bus_terminal SET lat = 37.36644785, lng = 128.39335081 WHERE code = 'NAI2537601';
-- 평택 (INTERCITY) — 평택시외버스터미널 · 경기 평택시 평택로 31 · 2.3km 이동
UPDATE bus_terminal SET lat = 36.99036504, lng = 127.08786053 WHERE code = 'NAI1791901';
-- 평해 (INTERCITY) — 평해버스정류장 · 경북 울진군 평해읍 평해로 55 · 8.7km 이동
UPDATE bus_terminal SET lat = 36.72575704, lng = 129.44115351 WHERE code = 'NAI3636601';
-- 포항 (INTERCITY) — 포항터미널 · 경북 포항시 남구 중흥로 85 · 3.5km 이동
UPDATE bus_terminal SET lat = 36.01347278, lng = 129.34967716 WHERE code = 'NAI3776001';
-- 하동 (INTERCITY) — 하동버스터미널 · 경남 하동군 하동읍 너뱅이길 35 · 0.9km 이동
UPDATE bus_terminal SET lat = 35.06420128, lng = 127.76057583 WHERE code = 'NAI5232501';
-- 한국민속촌 (INTERCITY) — 한국민속촌정류장 · 경기 용인시 기흥구 보라동 409-10 · 0.4km 이동
UPDATE bus_terminal SET lat = 37.25473429, lng = 127.10857040 WHERE code = 'NAI1707501';
-- 한서대 (INTERCITY) — 한서대정류소 · 충남 서산시 해미면 대곡리 388-8 · 0.4km 이동
UPDATE bus_terminal SET lat = 36.69022251, lng = 126.57613428 WHERE code = 'NAI3196201';
-- 함안 (INTERCITY) — 함안버스터미널 · 경남 함안군 가야읍 함마대로 1636-16 · 0.8km 이동
UPDATE bus_terminal SET lat = 35.27939176, lng = 128.40903698 WHERE code = 'NAI5201701';
-- 함양 (INTERCITY) — 함양시외버스터미널 · 경남 함양군 함양읍 한들로 159 · 0.6km 이동
UPDATE bus_terminal SET lat = 35.52109299, lng = 127.73294435 WHERE code = 'NAI5003901';
-- 함창 (INTERCITY) — 함창버스정류장 · 경북 상주시 함창읍 함창중앙로 99 · 0.3km 이동
UPDATE bus_terminal SET lat = 36.56829786, lng = 128.17572414 WHERE code = 'NAI3711801';
-- 함평 (INTERCITY) — 함평공영터미널 · 전남광주통합특별시 함평군 함평읍 중앙길 46 · 0.6km 이동
UPDATE bus_terminal SET lat = 35.06208898, lng = 126.52335531 WHERE code = 'NAI5715301';
-- 합덕 (INTERCITY) — 합덕버스터미널 · 충남 당진시 합덕읍 합덕시장로 214-7 · 0.9km 이동
UPDATE bus_terminal SET lat = 36.81027110, lng = 126.77570145 WHERE code = 'NAI3181201';
-- 합천 (INTERCITY) — 합천버스정류장 · 경남 합천군 합천읍 대야로 883 · 0.4km 이동
UPDATE bus_terminal SET lat = 35.56722799, lng = 128.16292112 WHERE code = 'NAI5023301';
-- 해남 (INTERCITY) — 해남종합버스터미널 · 전남광주통합특별시 해남군 해남읍 해남로 8 · 31.0km 이동
UPDATE bus_terminal SET lat = 34.57066373, lng = 126.60793646 WHERE code = 'NAI5903801';
-- 해미 (INTERCITY) — 해미정류장 · 충남 서산시 해미면 읍내리 305-1 · 0.5km 이동
UPDATE bus_terminal SET lat = 36.71256278, lng = 126.54434047 WHERE code = 'NAI3196001';
-- 해인사 (INTERCITY) — 해인사시외버스터미널 · 경남 합천군 가야면 가야산로 1808 · 0.2km 이동
UPDATE bus_terminal SET lat = 35.79226130, lng = 128.08852310 WHERE code = 'NAI5020001';
-- 현리(강원) (INTERCITY) — 현리시외버스터미널 · 강원특별자치도 인제군 기린면 내린천로 4024 · 0.1km 이동
UPDATE bus_terminal SET lat = 37.95533745, lng = 128.31723691 WHERE code = 'NAI2465901';
-- 호산 (INTERCITY) — 호산버스정류장 · 강원특별자치도 삼척시 원덕읍 삼척로 381 · 0.2km 이동
UPDATE bus_terminal SET lat = 37.17178576, lng = 129.33730796 WHERE code = 'NAI2596101';
-- 홍농 (INTERCITY) — 홍농버스터미널 · 전남광주통합특별시 영광군 홍농읍 상하길 28 · 0.1km 이동
UPDATE bus_terminal SET lat = 35.39426084, lng = 126.44475982 WHERE code = 'NAI5700401';
-- 홍성 (INTERCITY) — 홍성종합터미널 · 충남 홍성군 홍성읍 조양로247번길 9 · 1.3km 이동
UPDATE bus_terminal SET lat = 36.60094585, lng = 126.67609654 WHERE code = 'NAI3222001';
-- 홍천 (INTERCITY) — 홍천종합버스터미널 · 강원특별자치도 홍천군 홍천읍 홍천로 301 · 17.6km 이동
UPDATE bus_terminal SET lat = 37.68897141, lng = 127.87870250 WHERE code = 'NAI2513501';
-- 화령 (INTERCITY) — 화령공용버스터미널 · 경북 상주시 화서면 화령남5길 9 · 0.5km 이동
UPDATE bus_terminal SET lat = 36.44372495, lng = 127.94871009 WHERE code = 'NAI3713901';
-- 화성(청양) (INTERCITY) — 화성정류소 · 충남 청양군 화성면 무한로 113 · 60.6km 이동
UPDATE bus_terminal SET lat = 36.42784707, lng = 126.71491931 WHERE code = 'NAI3331201';
-- 화순 (INTERCITY) — 화순시외버스공용정류장 · 전남광주통합특별시 화순군 화순읍 시장길 7 · 0.4km 이동
UPDATE bus_terminal SET lat = 35.05634512, lng = 126.98327223 WHERE code = 'NAI5812001';
-- 화엄사 (INTERCITY) — 화엄사정류소 · 전남광주통합특별시 구례군 마산면 화엄사로 372 · 1.5km 이동
UPDATE bus_terminal SET lat = 35.24256812, lng = 127.48818082 WHERE code = 'NAI5761601';
-- 화천 (INTERCITY) — 화천공영버스터미널 · 강원특별자치도 화천군 화천읍 상승로 38-5 · 16.4km 이동
UPDATE bus_terminal SET lat = 38.10449313, lng = 127.70434448 WHERE code = 'NAI2413001';
-- 후포 (INTERCITY) — 후포터미널 · 경북 울진군 후포면 상밤터1길 5 · 1.8km 이동
UPDATE bus_terminal SET lat = 36.68264739, lng = 129.44204017 WHERE code = 'NAI3636901';
-- 흥덕 (INTERCITY) — 흥덕버스터미널 · 전북특별자치도 고창군 흥덕면 선운대로 3722 · 0.5km 이동
UPDATE bus_terminal SET lat = 35.51884562, lng = 126.69918088 WHERE code = 'NAI5641501';

-- ── 확인하지 못해 비우는 좌표
-- 가평 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK255';
-- 강진 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK535';
-- 경기광주 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK151';
-- 경북도청 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK852';
-- 경주 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK815';
-- 경주 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK894';
-- 고양백석 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK116';
-- 고창 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK635';
-- 고한사북 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK273';
-- 고현 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK732';
-- 고흥 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK540';
-- 곡성 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK589';
-- 공주 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK320';
-- 관산 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK576';
-- 광명 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK125';
-- 광양 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK520';
-- 광주(유·스퀘어) (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK500';
-- 광주운암 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK504';
-- 괴산 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK457';
-- 구리 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK169';
-- 구미 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK810';
-- 구미 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK933';
-- 군산 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK610';
-- 군산대야 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK611';
-- 금강 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK923';
-- 금산 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK330';
-- 금산추부 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK331';
-- 기지시 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK388';
-- 김제 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK620';
-- 김천 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK820';
-- 김천 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK931';
-- 김해 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK735';
-- 김해장유 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK736';
-- 나주 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK530';
-- 나주혁신 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK531';
-- 낙산 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK243';
-- 남악 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK592';
-- 남원 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK625';
-- 남청주 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK402';
-- 내서 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK706';
-- 내포 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK390';
-- 녹동 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK545';
-- 논산 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK370';
-- 단양 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK460';
-- 담양 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK582';
-- 당진 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK312';
-- 대구서부 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK811';
-- 대구용계 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK807';
-- 대산 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK385';
-- 대신 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK932';
-- 대창 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK897';
-- 동광양(중마) (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK525';
-- 동래 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK887';
-- 동백 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK471';
-- 동해 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK210';
-- 마산 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK705';
-- 목포 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK505';
-- 무안 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK550';
-- 무주 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK655';
-- 문장 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK584';
-- 밀양 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK750';
-- 벌교 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK555';
-- 보령 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK395';
-- 보성 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK554';
-- 보은 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK409';
-- 복지 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK936';
-- 봉산 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK930';
-- 봉화 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK858';
-- 부산사상 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK703';
-- 부안 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK640';
-- 부여 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK372';
-- 부천 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK101';
-- 사평리 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK461';
-- 삼척 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK220';
-- 삼척해변 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK221';
-- 삼호 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK591';
-- 상주 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK825';
-- 서대구 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK805';
-- 서산 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK313';
-- 서산 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK393';
-- 서수원 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK109';
-- 서천 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK384';
-- 서충주 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK419';
-- 서충주 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK421';
-- 선산 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK812';
-- 선산 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK813';
-- 속리산 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK408';
-- 속초 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK230';
-- 송광사 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK556';
-- 송내 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK102';
-- 송도 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK106';
-- 순창 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK645';
-- 순천신대지구 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK513';
-- 시흥시화 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK195';
-- 아주대 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK113';
-- 안성 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK130';
-- 안천 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK651';
-- 양산 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK745';
-- 양산 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK888';
-- 양양 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK270';
-- 언양 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK891';
-- 여수 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK510';
-- 여천 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK509';
-- 연무대 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK380';
-- 영광 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK560';
-- 영덕 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK843';
-- 영산포 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK565';
-- 영암 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK570';
-- 영월 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK272';
-- 영주 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK835';
-- 영해 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK842';
-- 예산 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK398';
-- 예천 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK851';
-- 오천 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK142';
-- 옥과 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK588';
-- 옥천 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK410';
-- 옥천 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK921';
-- 완주 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK618';
-- 왜관 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK935';
-- 용인 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK150';
-- 울산신복 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK716';
-- 울진 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK853';
-- 원동 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK578';
-- 원주 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK240';
-- 원주문막 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK245';
-- 유구 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK321';
-- 유성복합 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK360';
-- 인삼랜드 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK324';
-- 인삼랜드 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK325';
-- 인천 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK100';
-- 장성 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK583';
-- 장승포 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK731';
-- 장흥 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK580';
-- 전북혁신 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK621';
-- 전주 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK600';
-- 전주 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK601';
-- 전주 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK602';
-- 전주 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK603';
-- 전주 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK604';
-- 전주시외 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK609';
-- 점촌 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK850';
-- 정산 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK392';
-- 정선 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK222';
-- 정안 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK315';
-- 정안 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK316';
-- 정읍 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK630';
-- 정읍 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK631';
-- 주문진 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK202';
-- 증평 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK455';
-- 지도 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK585';
-- 진도 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK590';
-- 진안 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK650';
-- 진주개양 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK723';
-- 진주혁신 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK724';
-- 진해 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK704';
-- 창원 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK710';
-- 창원역 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK711';
-- 청양 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK391';
-- 청평 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK252';
-- 추풍령 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK490';
-- 추풍령 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK929';
-- 충주 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK420';
-- 태백 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK274';
-- 태안 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK394';
-- 태안 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK495';
-- 태인 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK629';
-- 통영 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK730';
-- 평해 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK844';
-- 포천 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK146';
-- 풍기 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK834';
-- 함평 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK581';
-- 해남 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK595';
-- 해미 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK383';
-- 해제 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK552';
-- 홍성 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK389';
-- 홍천 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK242';
-- 화순 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK586';
-- 화천 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK260';
-- 황간 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK440';
-- 황간 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK928';
-- 회진 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK577';
-- 횡계 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK235';
-- 후포 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK857';
-- 흥덕 (EXPRESS) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAEK634';
-- 가야 (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI5020101';
-- 감일문화공원(하남) (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI1300101';
-- 거제(고현) (INTERCITY) — 거제(고현) — 거제옥포정류소로 잡혔다. 고현과 옥포는 다른 곳이다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI5325101';
-- 교통대학 (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI2746901';
-- 김천부곡(공항) (INTERCITY) — 김천부곡(공항) — 김천공용버스터미널로 잡혔다. 부곡은 별도 정류소다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI3962602';
-- 낙양동차고지 (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI1176903';
-- 단국대(죽전캠퍼스) (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI1689001';
-- 대구공항 (INTERCITY) — 대구공항 — 동대구터미널로 잡혔다. 이름의 '대구' 만 겹친 다른 곳이다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI4105201';
-- 대구동부 (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI4124601';
-- 대한리무진(전주) (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI5493401';
-- 동광양(중마) (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI5778701';
-- 동대문 디자인플라자(DDP) (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI0456505';
-- 목포대 (INTERCITY) — 목포대 — 목포종합버스터미널로 잡혔다. 목포대는 무안군에 있다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI5855401';
-- 문산공용 (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI1082301';
-- 배방정류소 (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI3148401';
-- 백운 (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI2710401';
-- 부곡 (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI5036501';
-- 성사고교 (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI1029401';
-- 세교한신더휴(오산) (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI1812702';
-- 소록도 (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI5956101';
-- 순천역 (INTERCITY) — 순천역 — 순천종합버스터미널로 잡혔다. 역과 터미널은 다른 자리다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI5796201';
-- 신평 (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI3174801';
-- 쌍용스위닷홈(덕소) (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI1227501';
-- 원병원(마석) (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI1217902';
-- 위시티(3,4단지) (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI1032301';
-- 을지대(성남) (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI1313401';
-- 인천공항2터미널 (INTERCITY) — 인천공항2터미널 — 한국도심공항터미널로 잡혔다. T2 가 아니다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI2238202';
-- 정우상가 (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI5143601';
-- 코엑스(도심공항) (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI0616401';
-- 판교(풍경채7단지) (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI1354401';
-- 하남BRT (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI1302301';
-- 한방단지 (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI3710402';
-- 현리 (INTERCITY) — 도시 안에서 같은 이름의 터미널·정류장을 찾지 못했다
UPDATE bus_terminal SET lat = NULL, lng = NULL WHERE code = 'NAI2900601';
