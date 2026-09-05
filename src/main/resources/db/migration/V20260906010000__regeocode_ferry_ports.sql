-- 항구 좌표를 지역 검증까지 붙여 다시 만든다 (#452).
--
-- **철원군 코스에 여객선이 대표로 떴다.** 내륙 최북단인데. 원인은 `상노대` 항구 좌표가
-- (38.143, 127.219) — 역지오코딩하면 강원 철원군 동송읍 **상노리**다. 실제 상노대도는 통영이고,
-- 지오코더가 이름을 찾다가 앞 두 글자만 맞는 곳에 붙였다. `쑥섬`(전남 고흥)도 강화 두운리에 박혔다.
--
-- 열차역(#437)·버스터미널(#436)과 같은 부류다. 항구는 이름이 짧고 흔한 지명 조각이라 더 잘 걸린다.
--
-- **버스와 달리 도시를 못 받는다** — TAGO 여객선 목록(`GetPortList`)은 nodeId·nodeNm 뿐이고
-- cityCode 필터도 안 먹는다. 대신 두 가지를 요구했다:
--   1. 찾은 곳이 항구 계열일 것(이름에 항·선착장·여객·부두·나루)
--   2. 우리 이름이 그 이름이나 주소에 있을 것 — `여의도` 로 찾으면 나오는 서울의 식당을 걸러낸다
-- 이름에 `부산_연안부두` 처럼 지역 접두어가 있으면 그것도 주소와 대조했다. 그 접두어를 버리면
-- 인천항 연안부두(340km 밖)가 잡힌다.
--
-- 결과: 확인 302곳(그중 143곳이 1km 넘게 이동 · 43곳은 새로 채움) · 비움 128곳
-- 1km 넘게 움직인 곳은 눈으로 훑었고, 이름만 겹친 오매칭 3건은 따로 비웠다.
-- forward-only — 시드 파일을 고치지 않고 code(UNIQUE) 기준 UPDATE 로 되돌린다.

-- ── 확인된 좌표
-- 가력도 — 가력도항 · 전북특별자치도 부안군 변산면 대항리 583 · 0.1km 이동
UPDATE ferry_port SET lat = 35.72759111, lng = 126.53060620 WHERE code = 'SEA30500';
-- 가사도 — 가사도항 · 전남광주통합특별시 진도군 조도면 가사도리 850-5 · 46.1km 이동
UPDATE ferry_port SET lat = 34.47000692, lng = 126.05181619 WHERE code = 'SEA31130';
-- 가오치 — 가오치항 · 경남 통영시 도산면 오륜리 · 0.0km 이동
UPDATE ferry_port SET lat = 34.90875849, lng = 128.31408624 WHERE code = 'SEA96410';
-- 가의도 — 가의도항 · 충남 태안군 근흥면 가의도리 · 6.3km 이동
UPDATE ferry_port SET lat = 36.67228674, lng = 126.06459276 WHERE code = 'SEA22110';
-- 갈목도 — 갈목항 · 전남광주통합특별시 진도군 조도면 진목도리 산 14-4 · 0.1km 이동
UPDATE ferry_port SET lat = 34.30554850, lng = 125.94793845 WHERE code = 'SEA31210';
-- 강릉 — 강릉항 · 강원특별자치도 강릉시 견소동 286-10 · 7.0km 이동
UPDATE ferry_port SET lat = 37.77281071, lng = 128.95304153 WHERE code = 'SEA44060';
-- 개도 — 개도항(신안) · 전남광주통합특별시 신안군 하의면 후광리 산 216-1 · 새로 채움
UPDATE ferry_port SET lat = 34.63755721, lng = 126.01137731 WHERE code = 'SEA31230';
-- 개도_여석 — 여석항 · 전남광주통합특별시 여수시 화정면 개도리 1447-13 · 0.0km 이동
UPDATE ferry_port SET lat = 34.58099695, lng = 127.64990571 WHERE code = 'SEA31231';
-- 개도_화산 — 화산항 · 전남광주통합특별시 여수시 화정면 개도리 · 1.7km 이동
UPDATE ferry_port SET lat = 34.58313341, lng = 127.66693362 WHERE code = 'SEA31233';
-- 개야도 — 개야도선착장 · 전북특별자치도 군산시 옥도면 개야도리 785 · 9.1km 이동
UPDATE ferry_port SET lat = 36.03262102, lng = 126.55591466 WHERE code = 'SEA30110';
-- 거문도 — 거문도항 · 전남광주통합특별시 여수시 삼산면 거문길 103 · 0.0km 이동
UPDATE ferry_port SET lat = 34.02750252, lng = 127.30886522 WHERE code = 'SEA31310';
-- 거제 — 저구항 · 경남 거제시 남부면 저구리 216-6 · 16.8km 이동
UPDATE ferry_port SET lat = 34.73074748, lng = 128.60607382 WHERE code = 'SEA40030';
-- 거제_고현 — 고현항 방파제 · 경남 거제시 고현동 1105 · 1.7km 이동
UPDATE ferry_port SET lat = 34.89745319, lng = 128.61443229 WHERE code = 'SEA95010';
-- 거제_구영 — 구영카페리선착장 · 경남 거제시 장목면 구영리 329-9 · 0.0km 이동
UPDATE ferry_port SET lat = 35.03053943, lng = 128.69562019 WHERE code = 'SEA40035';
-- 거제_성포 — 성포항 · 경남 거제시 사등면 성포로3길 33 · 0.0km 이동
UPDATE ferry_port SET lat = 34.92106756, lng = 128.52593087 WHERE code = 'SEA40031';
-- 거제_어구 — 어구항 · 경남 거제시 둔덕면 어구리 142-18 · 21.2km 이동
UPDATE ferry_port SET lat = 34.81981225, lng = 128.50514922 WHERE code = 'SEA40036';
-- 거제_옥포 — 옥포항 · 경남 거제시 옥포동 · 2.9km 이동
UPDATE ferry_port SET lat = 34.89067227, lng = 128.69852153 WHERE code = 'SEA95050';
-- 거제_장승포 — 장승포유람선터미널 · 경남 거제시 장승로 138 · 0.0km 이동
UPDATE ferry_port SET lat = 34.86628954, lng = 128.72463533 WHERE code = 'SEA95090';
-- 거제_저구 — 저구항 · 경남 거제시 남부면 저구리 216-6 · 0.1km 이동
UPDATE ferry_port SET lat = 34.73074748, lng = 128.60607382 WHERE code = 'SEA97060';
-- 거제_해금강 — 해금강선착장휴게소 · 경남 거제시 남부면 해금강3길 35 · 0.3km 이동
UPDATE ferry_port SET lat = 34.73598421, lng = 128.67490260 WHERE code = 'SEA40033';
-- 격포 — 격포항 · 전북특별자치도 부안군 변산면 격포리 788-15 · 206.4km 이동
UPDATE ferry_port SET lat = 35.62043439, lng = 126.47001012 WHERE code = 'SEA30020';
-- 고금_상정 — 상정항 · 전남광주통합특별시 완도군 고금면 상정리 744-4 · 0.1km 이동
UPDATE ferry_port SET lat = 34.35412973, lng = 126.77602327 WHERE code = 'SEA31332';
-- 고금도 — 상정항 · 전남광주통합특별시 완도군 고금면 상정리 744-4 · 10.9km 이동
UPDATE ferry_port SET lat = 34.35412973, lng = 126.77602327 WHERE code = 'SEA31330';
-- 고대도 — 고대도항 · 충남 보령시 오천면 삽시도리 · 0.0km 이동
UPDATE ferry_port SET lat = 36.39002219, lng = 126.37136815 WHERE code = 'SEA22130';
-- 고성-용암포 — 용암포항 · 경남 고성군 하일면 춘암리 906-2 · 0.4km 이동
UPDATE ferry_port SET lat = 34.90232483, lng = 128.17447566 WHERE code = 'SEA94310';
-- 고이도 — 고이항 · 전남광주통합특별시 신안군 압해읍 고이리 1079-28 · 0.0km 이동
UPDATE ferry_port SET lat = 34.96016345, lng = 126.28959897 WHERE code = 'SEA31360';
-- 고파도 — 고파도항 · 충남 서산시 팔봉면 고파도리 · 9.4km 이동
UPDATE ferry_port SET lat = 36.91184338, lng = 126.33784800 WHERE code = 'SEA22150';
-- 고하도 — 고하도 선착장 · 전남광주통합특별시 목포시 고하도길 162 · 1.3km 이동
UPDATE ferry_port SET lat = 34.76543636, lng = 126.37274815 WHERE code = 'SEA31410';
-- 곽도 — 곽도항 · 전남광주통합특별시 진도군 조도면 맹골도리 산 12 · 0.0km 이동
UPDATE ferry_port SET lat = 34.19813072, lng = 125.85728217 WHERE code = 'SEA31430';
-- 관리도 — 관리도항 · 전북특별자치도 군산시 옥도면 관리도리 · 0.1km 이동
UPDATE ferry_port SET lat = 35.81855082, lng = 126.37469313 WHERE code = 'SEA30150';
-- 관매도 — 관매항 · 전남광주통합특별시 진도군 조도면 관매도리 · 0.5km 이동
UPDATE ferry_port SET lat = 34.23960744, lng = 126.04505411 WHERE code = 'SEA31460';
-- 관사도 — 관사항 · 전남광주통합특별시 진도군 조도면 관사도리 452-2 · 0.5km 이동
UPDATE ferry_port SET lat = 34.30926023, lng = 125.97759531 WHERE code = 'SEA31490';
-- 광대도 — 광대도항 · 전남광주통합특별시 진도군 조도면 · 38.0km 이동
UPDATE ferry_port SET lat = 34.52923325, lng = 126.10311338 WHERE code = 'SEA31510';
-- 광도 — 광도항 · 전남광주통합특별시 여수시 삼산면 손죽리 산 127 · 107.1km 이동
UPDATE ferry_port SET lat = 34.26282944, lng = 127.53037160 WHERE code = 'SEA31530';
-- 광양 — 광양항 · 전남광주통합특별시 광양시 컨부두로 240 · 5.3km 이동
UPDATE ferry_port SET lat = 34.90216459, lng = 127.66107680 WHERE code = 'SEA31560';
-- 교동도 — 월선포항 · 인천 강화군 교동면 상용리 · 2.2km 이동
UPDATE ferry_port SET lat = 37.77460446, lng = 126.31710753 WHERE code = 'SEA96770';
-- 구도_완도군 — 후장구도선착장 · 전남광주통합특별시 완도군 노화읍 장구도길 13 · 7.9km 이동
UPDATE ferry_port SET lat = 34.19732192, lng = 126.49126120 WHERE code = 'SEA31590';
-- 국도 — 국도선착장 · 경남 통영시 욕지면 국도길 17 · 새로 채움
UPDATE ferry_port SET lat = 34.54699272, lng = 128.44349102 WHERE code = 'SEA96860';
-- 국화도 — 국화도항A호방파제등대 · 경기 화성시 만세구 우정읍 국화리 137 · 2.7km 이동
UPDATE ferry_port SET lat = 37.06100624, lng = 126.55970688 WHERE code = 'SEA10620';
-- 군산 — 군산항 · 전북특별자치도 군산시 소룡동 1668 · 9.9km 이동
UPDATE ferry_port SET lat = 35.97853119, lng = 126.62882332 WHERE code = 'SEA30010';
-- 군산국제 — 군산 국제항 여객터미널 전기차충전소 · 전북특별자치도 군산시 임해로 378-14 · 0.1km 이동
UPDATE ferry_port SET lat = 35.97799089, lng = 126.62925827 WHERE code = 'SEA97520';
-- 굴업도 — 굴업항 · 인천 옹진군 덕적면 굴업리 · 61.7km 이동
UPDATE ferry_port SET lat = 37.18880973, lng = 125.98572804 WHERE code = 'SEA96380';
-- 궁평 — 궁평항 · 경기 화성시 만세구 서신면 궁평리 713 · 새로 채움
UPDATE ferry_port SET lat = 37.11541343, lng = 126.68072309 WHERE code = 'SEA10610';
-- 금당(가학) — 가학항 · 전남광주통합특별시 완도군 금당면 가학리 337-3 · 72.0km 이동
UPDATE ferry_port SET lat = 34.44685339, lng = 127.03404636 WHERE code = 'SEA31631';
-- 금당도 — 울포항 · 전남광주통합특별시 완도군 금당면 차우리 · 5.9km 이동
UPDATE ferry_port SET lat = 34.42497873, lng = 127.07553454 WHERE code = 'SEA31630';
-- 금오도 — 남면여객선터미널 · 전남광주통합특별시 여수시 남면 금오로 889 · 6.4km 이동
UPDATE ferry_port SET lat = 34.50868658, lng = 127.77045061 WHERE code = 'SEA31660';
-- 금일도 — 사동항 · 전남광주통합특별시 완도군 금일읍 사동리 31-39 · 7.9km 이동
UPDATE ferry_port SET lat = 34.33362327, lng = 127.07548190 WHERE code = 'SEA31690';
-- 기도 — 기도항 · 전남광주통합특별시 신안군 신의면 상태서리 808-1 · 새로 채움
UPDATE ferry_port SET lat = 34.63522124, lng = 126.08560459 WHERE code = 'SEA31710';
-- 남당 — 남당항 · 충남 홍성군 서부면 남당항로213번길 25-62 · 새로 채움
UPDATE ferry_port SET lat = 36.53693200, lng = 126.46889360 WHERE code = 'SEA22600';
-- 낭도 — 낭도항 · 전남광주통합특별시 여수시 화정면 낭도리 1314-11 · 0.1km 이동
UPDATE ferry_port SET lat = 34.60418696, lng = 127.53868007 WHERE code = 'SEA31730';
-- 낭도_규포 — 규포항 · 전남광주통합특별시 여수시 화정면 낭도리 336-10 · 0.1km 이동
UPDATE ferry_port SET lat = 34.61575490, lng = 127.55220416 WHERE code = 'SEA31731';
-- 낭도_사도 — 사도선착장 · 전남광주통합특별시 여수시 화정면 낭도리 210-13 · 0.1km 이동
UPDATE ferry_port SET lat = 34.59358933, lng = 127.55546604 WHERE code = 'SEA33210';
-- 내나로도 — 나로도항 · 전남광주통합특별시 고흥군 봉래면 신금리 1256-3 · 6.4km 이동
UPDATE ferry_port SET lat = 34.46393350, lng = 127.45374796 WHERE code = 'SEA31760';
-- 내병도 — 내병항 · 전남광주통합특별시 진도군 조도면 내병도리 77-1 · 59.0km 이동
UPDATE ferry_port SET lat = 34.37555143, lng = 125.96966179 WHERE code = 'SEA31790';
-- 노량 — 노량항 · 경남 하동군 금남면 노량리 742-12 · 새로 채움
UPDATE ferry_port SET lat = 34.94701134, lng = 127.86043978 WHERE code = 'SEA96280';
-- 노력도 — 노력항 · 전남광주통합특별시 장흥군 회진면 · 2.2km 이동
UPDATE ferry_port SET lat = 34.44529373, lng = 126.96552990 WHERE code = 'SEA31830';
-- 노화_산양_왕복 — 산양항 · 전남광주통합특별시 완도군 노화읍 신양리 155-31 · 새로 채움
UPDATE ferry_port SET lat = 34.22658377, lng = 126.57575548 WHERE code = 'SEA31894';
-- 노화도 — 산양항 · 전남광주통합특별시 완도군 노화읍 신양리 155-31 · 0.1km 이동
UPDATE ferry_port SET lat = 34.22658377, lng = 126.57575548 WHERE code = 'SEA31890';
-- 녹도 — 녹도항 · 충남 보령시 오천면 녹도리 240-1 · 11.0km 이동
UPDATE ferry_port SET lat = 36.26949585, lng = 126.26694919 WHERE code = 'SEA22190';
-- 녹동 — 녹동항 · 전남광주통합특별시 고흥군 도양읍 봉암리 3907 · 새로 채움
UPDATE ferry_port SET lat = 34.52305790, lng = 127.14364799 WHERE code = 'SEA31910';
-- 눌옥도 — 눌옥도항 · 전남광주통합특별시 진도군 조도면 눌옥도리 4-2 · 62.0km 이동
UPDATE ferry_port SET lat = 34.34823392, lng = 125.95807153 WHERE code = 'SEA31960';
-- 능산도 — 능산항 · 전남광주통합특별시 신안군 하의면 능산리 11-6 · 0.5km 이동
UPDATE ferry_port SET lat = 34.61465267, lng = 126.00957633 WHERE code = 'SEA31990';
-- 다물도 — 다물도항 · 전남광주통합특별시 신안군 흑산면 다물도리 25-2 · 새로 채움
UPDATE ferry_port SET lat = 34.73510097, lng = 125.44653770 WHERE code = 'SEA32030';
-- 달리도 — 달리도항 · 전남광주통합특별시 목포시 달동 33-4 · 새로 채움
UPDATE ferry_port SET lat = 34.77719013, lng = 126.32822800 WHERE code = 'SEA32060';
-- 당사도-소안면 — 당사항(소안면) · 전남광주통합특별시 완도군 소안면 당사리 산 18-1 · 8.2km 이동
UPDATE ferry_port SET lat = 34.10514965, lng = 126.59782632 WHERE code = 'SEA32110';
-- 당사도-암태면 — 당사도항(암태면) · 전남광주통합특별시 신안군 암태면 당사리 · 10.0km 이동
UPDATE ferry_port SET lat = 34.88961743, lng = 126.18901808 WHERE code = 'SEA32090';
-- 당진 — 당진항만 · 충남 당진시 송악읍 고대공단2길 227 · 14.4km 이동
UPDATE ferry_port SET lat = 36.98070712, lng = 126.76405815 WHERE code = 'SEA22030';
-- 당진_장고 — 장고항 · 충남 당진시 석문면 장고항로 324 · 0.2km 이동
UPDATE ferry_port SET lat = 37.03124290, lng = 126.55990573 WHERE code = 'SEA22700';
-- 대기점도 — 대기점도항 · 전남광주통합특별시 신안군 증도면 병풍리 산 170-2 · 10.6km 이동
UPDATE ferry_port SET lat = 34.94271777, lng = 126.20995259 WHERE code = 'SEA32160';
-- 대난지도 — 난지도항 · 충남 당진시 석문면 난지도리 4-25 · 0.1km 이동
UPDATE ferry_port SET lat = 37.05352583, lng = 126.44930624 WHERE code = 'SEA10080';
-- 대마도 — 대마항 · 전남광주통합특별시 진도군 조도면 · 0.0km 이동
UPDATE ferry_port SET lat = 34.27080136, lng = 125.99679047 WHERE code = 'SEA32210';
-- 대모도 — 모동항 · 전남광주통합특별시 완도군 청산면 모도리 130-3 · 1.6km 이동
UPDATE ferry_port SET lat = 34.19901741, lng = 126.76895561 WHERE code = 'SEA32230';
-- 대부도 — 방아머리항 · 경기 안산시 단원구 대부북동 · 0.6km 이동
UPDATE ferry_port SET lat = 37.29670403, lng = 126.57395060 WHERE code = 'SEA21110';
-- 대석만도 — 대석만항 · 전남광주통합특별시 영광군 낙월면 석만리 산 32 · 274.0km 이동
UPDATE ferry_port SET lat = 35.37289170, lng = 126.05565177 WHERE code = 'SEA32260';
-- 대야도 — 대야도항 · 전남광주통합특별시 신안군 하의면 능산리 583-4 · 0.0km 이동
UPDATE ferry_port SET lat = 34.63914138, lng = 125.96940401 WHERE code = 'SEA32290';
-- 대연평 — 대연평항 · 인천 옹진군 연평면 연평리 · 0.0km 이동
UPDATE ferry_port SET lat = 37.65551403, lng = 125.71374285 WHERE code = 'SEA10050';
-- 대이작도 — 대이작항 · 인천 옹진군 자월면 이작리 · 43.7km 이동
UPDATE ferry_port SET lat = 37.17857384, lng = 126.24771216 WHERE code = 'SEA10190';
-- 대장구도 — 대장구도항 · 전남광주통합특별시 완도군 노화읍 내리 산 249-3 · 14.8km 이동
UPDATE ferry_port SET lat = 34.26280394, lng = 126.45154403 WHERE code = 'SEA32310';
-- 대정원도 — 대정원도항 · 전남광주통합특별시 완도군 노화읍 · 새로 채움
UPDATE ferry_port SET lat = 34.26530642, lng = 126.42845459 WHERE code = 'SEA32330';
-- 대천 — 대천항 · 충남 보령시 신흑동 2245 · 7.1km 이동
UPDATE ferry_port SET lat = 36.32755928, lng = 126.51231142 WHERE code = 'SEA22010';
-- 대청도 — 대청항 · 인천 옹진군 대청면 대청리 377-29 · 0.1km 이동
UPDATE ferry_port SET lat = 37.82758886, lng = 124.71507127 WHERE code = 'SEA10020';
-- 대포작도 — 대포작도항 · 전남광주통합특별시 신안군 지도읍 어의리 557-1 · 0.4km 이동
UPDATE ferry_port SET lat = 35.11145151, lng = 126.20308755 WHERE code = 'SEA32360';
-- 대흑산도 — 흑산항 · 전남광주통합특별시 신안군 흑산면 예리 176-47 · 0.9km 이동
UPDATE ferry_port SET lat = 34.68384409, lng = 125.44164102 WHERE code = 'SEA32390';
-- 덕우도 — 덕우항 · 전남광주통합특별시 완도군 생일면 봉선리 · 24.4km 이동
UPDATE ferry_port SET lat = 34.24938809, lng = 127.01431597 WHERE code = 'SEA32410';
-- 도비도 — 도비도항 · 충남 당진시 석문면 난지도리 · 0.0km 이동
UPDATE ferry_port SET lat = 37.01797341, lng = 126.46148311 WHERE code = 'SEA22500';
-- 도초도 — 도초항(도초도) · 전남광주통합특별시 신안군 도초면 발매리 9-21 · 0.1km 이동
UPDATE ferry_port SET lat = 34.71553694, lng = 125.93585855 WHERE code = 'SEA32430';
-- 도초도(시목) — 시목항(도초도) · 전남광주통합특별시 신안군 도초면 오류리 산 44 · 5.3km 이동
UPDATE ferry_port SET lat = 34.66886280, lng = 125.94946039 WHERE code = 'SEA32432';
-- 독거도 — 독거항 · 전남광주통합특별시 진도군 조도면 독거도리 산 19 · 0.0km 이동
UPDATE ferry_port SET lat = 34.25681032, lng = 126.18010142 WHERE code = 'SEA32460';
-- 독도 — 독도항 · 경북 울릉군 울릉읍 독도리 27 · 0.0km 이동
UPDATE ferry_port SET lat = 37.23916284, lng = 131.86757784 WHERE code = 'SEA96330';
-- 돌산_신기 — 신기항 · 전남광주통합특별시 여수시 돌산읍 신복리 1626 · 0.5km 이동
UPDATE ferry_port SET lat = 34.59928059, lng = 127.74351132 WHERE code = 'SEA32490';
-- 동거차도 — 동거차도항 · 전남광주통합특별시 진도군 조도면 동거차도리 136-13 · 0.0km 이동
UPDATE ferry_port SET lat = 34.23896316, lng = 125.93137495 WHERE code = 'SEA32510';
-- 동도 — 동도항 · 전남광주통합특별시 여수시 삼산면 동도리 1144-3 · 새로 채움
UPDATE ferry_port SET lat = 34.04744270, lng = 127.31123352 WHERE code = 'SEA32530';
-- 동소우이도 — 동소우이도선착장 · 전남광주통합특별시 신안군 도초면 우이도리 · 50.2km 이동
UPDATE ferry_port SET lat = 34.60962931, lng = 125.87583740 WHERE code = 'SEA32560';
-- 동해 — 동해항 · 강원특별자치도 동해시 송정동 · 3.8km 이동
UPDATE ferry_port SET lat = 37.49044067, lng = 129.12355183 WHERE code = 'SEA44010';
-- 동해국제 — 동해항국제여객터미널 · 강원특별자치도 동해시 대동로 210 · 0.1km 이동
UPDATE ferry_port SET lat = 37.49182281, lng = 129.12534110 WHERE code = 'SEA97790';
-- 동화도 — 동화항 · 전남광주통합특별시 완도군 군외면 · 3.0km 이동
UPDATE ferry_port SET lat = 34.29119042, lng = 126.60880157 WHERE code = 'SEA32590';
-- 돝섬 — 돝섬선착장 · 경남 창원시 마산합포구 월영동 647-3 · 2.2km 이동
UPDATE ferry_port SET lat = 35.17801776, lng = 128.58114356 WHERE code = 'SEA96260';
-- 두미도 — 북구항 · 경남 통영시 욕지면 두미리 · 54.1km 이동
UPDATE ferry_port SET lat = 34.70933759, lng = 128.18175649 WHERE code = 'SEA40390';
-- 둔병도 — 둔병항 · 전남광주통합특별시 여수시 화정면 조발리 408-7 · 22.0km 이동
UPDATE ferry_port SET lat = 34.62781794, lng = 127.53332573 WHERE code = 'SEA32610';
-- 땅끝(갈두) — 땅끝항 · 전남광주통합특별시 해남군 송지면 송호리 1127-21 · 새로 채움
UPDATE ferry_port SET lat = 34.29827271, lng = 126.53035927 WHERE code = 'SEA31190';
-- 마삭도 — 마삭도항 · 전남광주통합특별시 완도군 노화읍 신양리 산 296-2 · 0.1km 이동
UPDATE ferry_port SET lat = 34.24333708, lng = 126.56726081 WHERE code = 'SEA32630';
-- 마산 — 마산도선착장 · 전남광주통합특별시 신안군 압해읍 매화리 2042-2 · 212.6km 이동
UPDATE ferry_port SET lat = 34.95261837, lng = 126.24947176 WHERE code = 'SEA40010';
-- 마산_어시장 — 항구상회 · 경남 창원시 마산합포구 어시장6길 57 · 0.2km 이동
UPDATE ferry_port SET lat = 35.20427381, lng = 128.57753155 WHERE code = 'SEA97470';
-- 마산도 — 마산도선착장 · 전남광주통합특별시 신안군 압해읍 매화리 2042-2 · 4.5km 이동
UPDATE ferry_port SET lat = 34.95261837, lng = 126.24947176 WHERE code = 'SEA32660';
-- 마안도 — 마안도선착장 · 전남광주통합특별시 완도군 노화읍 내리 산 18-6 · 155.0km 이동
UPDATE ferry_port SET lat = 34.20866629, lng = 126.51485853 WHERE code = 'SEA32690';
-- 마진도 — 마진항 · 전남광주통합특별시 신안군 장산면 · 0.6km 이동
UPDATE ferry_port SET lat = 34.62668180, lng = 126.20462486 WHERE code = 'SEA32710';
-- 막금도 — 막금선착장 · 전남광주통합특별시 신안군 장산면 다수리 804-3 · 0.5km 이동
UPDATE ferry_port SET lat = 34.62102785, lng = 126.12543492 WHERE code = 'SEA32730';
-- 만재도 — 만재도항 · 전남광주통합특별시 신안군 흑산면 만재도리 산 15 · 106.5km 이동
UPDATE ferry_port SET lat = 34.21029750, lng = 125.47171599 WHERE code = 'SEA32760';
-- 말도_군산시 — 말도여객선선착장 · 전북특별자치도 군산시 옥도면 말도리 · 31.1km 이동
UPDATE ferry_port SET lat = 35.85335683, lng = 126.32138900 WHERE code = 'SEA30170';
-- 매물도 — 매물도항 · 경남 통영시 한산면 매죽리 · 9.7km 이동
UPDATE ferry_port SET lat = 34.64764298, lng = 128.57510725 WHERE code = 'SEA40430';
-- 매화도 — 매화도항 · 전남광주통합특별시 신안군 압해읍 매화리 · 54.4km 이동
UPDATE ferry_port SET lat = 34.91352666, lng = 126.25512939 WHERE code = 'SEA32810';
-- 맹골도 — 맹골도선착장 · 전남광주통합특별시 진도군 조도면 맹골도리 83-24 · 0.0km 이동
UPDATE ferry_port SET lat = 34.21669676, lng = 125.85342898 WHERE code = 'SEA32830';
-- 명도 — 명도항 · 전북특별자치도 군산시 옥도면 말도리 142-2 · 새로 채움
UPDATE ferry_port SET lat = 35.84982216, lng = 126.34857987 WHERE code = 'SEA30190';
-- 모황도 — 모황도항 · 전남광주통합특별시 완도군 신지면 · 12.8km 이동
UPDATE ferry_port SET lat = 34.28765737, lng = 126.89609896 WHERE code = 'SEA32860';
-- 목포 — 목포북항 · 전남광주통합특별시 목포시 죽교동 620-320 · 2.6km 이동
UPDATE ferry_port SET lat = 34.80489420, lng = 126.36477776 WHERE code = 'SEA31010';
-- 목포국제 — 국제여객선터미널교차로 · 전남광주통합특별시 목포시 해안동1가 · 0.1km 이동
UPDATE ferry_port SET lat = 34.78140391, lng = 126.38301492 WHERE code = 'SEA31012';
-- 목포삼학 — 목포남항 · 전남광주통합특별시 목포시 삼학로158번길 15-4 · 4.0km 이동
UPDATE ferry_port SET lat = 34.78477950, lng = 126.40489228 WHERE code = 'SEA31013';
-- 무녀도 — 무녀도항 · 전북특별자치도 군산시 옥도면 무녀도리 · 1.8km 이동
UPDATE ferry_port SET lat = 35.80686631, lng = 126.41760176 WHERE code = 'SEA30210';
-- 무의도 — 광명항 · 인천 영종구 무의동 · 4.3km 이동
UPDATE ferry_port SET lat = 37.37474807, lng = 126.43657491 WHERE code = 'SEA10170';
-- 묵호 — 묵호항 · 강원특별자치도 동해시 임항로 121 · 0.5km 이동
UPDATE ferry_port SET lat = 37.54979743, lng = 129.11239126 WHERE code = 'SEA44030';
-- 문갑도 — 문갑도항 · 인천 옹진군 덕적면 문갑리 · 7.5km 이동
UPDATE ferry_port SET lat = 37.17071024, lng = 126.11286662 WHERE code = 'SEA96370';
-- 문병도 — 문병도항 · 전남광주통합특별시 신안군 하의면 후광리 산 80 · 새로 채움
UPDATE ferry_port SET lat = 34.66923168, lng = 126.03953088 WHERE code = 'SEA32890';
-- 문어포 — 문어포항 · 경남 통영시 한산면 두억리 · 0.2km 이동
UPDATE ferry_port SET lat = 34.79781731, lng = 128.46403818 WHERE code = 'SEA96170';
-- 미법 — 미법항 · 인천 강화군 삼산면 미법리 85-1 · 새로 채움
UPDATE ferry_port SET lat = 37.72589004, lng = 126.26976247 WHERE code = 'SEA96480';
-- 박지도 — 박지선착장 · 전남광주통합특별시 신안군 안좌면 박지리 344-1 · 0.5km 이동
UPDATE ferry_port SET lat = 34.71283326, lng = 126.12218708 WHERE code = 'SEA32980';
-- 반달섬 — 반달섬선착장 · 경기 안산시 단원구 반달섬1로 70 · 새로 채움
UPDATE ferry_port SET lat = 37.29927233, lng = 126.74011434 WHERE code = 'SEA10300';
-- 반월도 — 반월도선착장 · 전남광주통합특별시 신안군 안좌면 반월리 · 새로 채움
UPDATE ferry_port SET lat = 34.70620019, lng = 126.09390003 WHERE code = 'SEA32910';
-- 방축도 — 방축도항 · 전북특별자치도 군산시 옥도면 말도리 · 27.0km 이동
UPDATE ferry_port SET lat = 35.84817930, lng = 126.37732541 WHERE code = 'SEA30230';
-- 백령도 — 용기포신항 · 인천 옹진군 백령면 백령로 68-81 · 0.6km 이동
UPDATE ferry_port SET lat = 37.95592623, lng = 124.73464363 WHERE code = 'SEA10030';
-- 백아도 — 백아도항 · 인천 옹진군 덕적면 백아리 · 25.1km 이동
UPDATE ferry_port SET lat = 37.07590454, lng = 125.94486221 WHERE code = 'SEA96390';
-- 백야도 — 백야항(여수) · 전남광주통합특별시 여수시 화정면 백야리 58-3 · 0.6km 이동
UPDATE ferry_port SET lat = 34.62027501, lng = 127.64165543 WHERE code = 'SEA32930';
-- 백일도 — 백일항 · 전남광주통합특별시 완도군 군외면 당인리 552-5 · 새로 채움
UPDATE ferry_port SET lat = 34.29484143, lng = 126.59194658 WHERE code = 'SEA32960';
-- 병풍도 — 병풍도선착장 · 전남광주통합특별시 신안군 증도면 병풍리 557-3 · 3.3km 이동
UPDATE ferry_port SET lat = 34.95532734, lng = 126.21683834 WHERE code = 'SEA32990';
-- 보길_중리 — 중리선착장 · 전남광주통합특별시 완도군 보길면 보길동로 440 · 0.4km 이동
UPDATE ferry_port SET lat = 34.16777435, lng = 126.59399390 WHERE code = 'SEA33011';
-- 보길_청별 — 청별항 · 전남광주통합특별시 완도군 보길면 보길로 72 · 1.0km 이동
UPDATE ferry_port SET lat = 34.16767233, lng = 126.55944593 WHERE code = 'SEA33012';
-- 보길도 — 중리선착장 · 전남광주통합특별시 완도군 보길면 보길동로 440 · 2.7km 이동
UPDATE ferry_port SET lat = 34.16777435, lng = 126.59399390 WHERE code = 'SEA33010';
-- 볼음도 — 볼음항 · 인천 강화군 서도면 볼음도리 · 15.6km 이동
UPDATE ferry_port SET lat = 37.66481267, lng = 126.21119274 WHERE code = 'SEA96500';
-- 부산 — 부산항 · 부산 동구 초량동 45-66 · 7.3km 이동
UPDATE ferry_port SET lat = 35.11655342, lng = 129.04902062 WHERE code = 'SEA42010';
-- 부산_국제 — 부산항국제여객터미널 · 부산 동구 충장대로 206 · 0.1km 이동
UPDATE ferry_port SET lat = 35.11737739, lng = 129.04910925 WHERE code = 'SEA97560';
-- 부산_연안부두 — 부산항 제1부두 · 부산 중구 충장대로 26 · 0.2km 이동
UPDATE ferry_port SET lat = 35.10487639, lng = 129.04040177 WHERE code = 'SEA42011';
-- 부산_영도 — 우성부두 · 부산 영도구 해양로 33-52 · 4.6km 이동
UPDATE ferry_port SET lat = 35.09906565, lng = 129.06048106 WHERE code = 'SEA97700';
-- 부산_용호동 — 용호부두 · 부산 남구 분포로 66-38 · 1.5km 이동
UPDATE ferry_port SET lat = 35.13205198, lng = 129.11981454 WHERE code = 'SEA97590';
-- 부산_중앙동 — 국제여객부두 · 부산 중구 중앙동4가 · 0.5km 이동
UPDATE ferry_port SET lat = 35.10290032, lng = 129.04135258 WHERE code = 'SEA95530';
-- 부산_해운대 — 미포항 · 부산 해운대구 중동 957-22 · 1.1km 이동
UPDATE ferry_port SET lat = 35.15822327, lng = 129.17163814 WHERE code = 'SEA95510';
-- 부소도 — 부소도선착장 · 전남광주통합특별시 신안군 안좌면 존포리 산 68-3 · 0.9km 이동
UPDATE ferry_port SET lat = 34.69147204, lng = 126.14654147 WHERE code = 'SEA33110';
-- 불도 — 불도선착장 · 전남광주통합특별시 진도군 지산면 가학리 · 새로 채움
UPDATE ferry_port SET lat = 34.44118457, lng = 126.06523098 WHERE code = 'SEA33130';
-- 비견도 — 비견도선착장 · 전남광주통합특별시 완도군 금당면 차우리 1562-4 · 12.6km 이동
UPDATE ferry_port SET lat = 34.42463183, lng = 127.07815274 WHERE code = 'SEA33160';
-- 비금_가산 — 가산항 · 전남광주통합특별시 신안군 비금면 가산리 180-57 · 0.0km 이동
UPDATE ferry_port SET lat = 34.76105905, lng = 125.99859523 WHERE code = 'SEA33192';
-- 비금_수대 — 수대항 · 전남광주통합특별시 신안군 비금면 수대리 38-6 · 0.5km 이동
UPDATE ferry_port SET lat = 34.72117251, lng = 125.93784415 WHERE code = 'SEA33191';
-- 비금도 — 가산항 · 전남광주통합특별시 신안군 비금면 가산리 180-57 · 7.5km 이동
UPDATE ferry_port SET lat = 34.76105905, lng = 125.99859523 WHERE code = 'SEA33190';
-- 비산도 — 비산도항 · 경남 통영시 한산면 염호리 842-2 · 7.6km 이동
UPDATE ferry_port SET lat = 34.81159986, lng = 128.49613940 WHERE code = 'SEA40470';
-- 비안도 — 비안도항 · 전북특별자치도 군산시 옥도면 비안도리 · 6.2km 이동
UPDATE ferry_port SET lat = 35.73528382, lng = 126.46142396 WHERE code = 'SEA30250';
-- 비진도 — 외항항 · 경남 통영시 한산면 비진리 430-2 · 14.1km 이동
UPDATE ferry_port SET lat = 34.71669425, lng = 128.45841510 WHERE code = 'SEA40490';
-- 사량_금평 — 금평항 · 경남 통영시 사량면 금평리 208-4 · 3.5km 이동
UPDATE ferry_port SET lat = 34.84377295, lng = 128.22430550 WHERE code = 'SEA95310';
-- 사량_내지 — 내지항 · 경남 통영시 사량면 상도일주로 494 · 0.4km 이동
UPDATE ferry_port SET lat = 34.85550266, lng = 128.18039221 WHERE code = 'SEA40516';
-- 사량_능양 — 능양항 · 경남 통영시 사량면 · 0.8km 이동
UPDATE ferry_port SET lat = 34.81029062, lng = 128.24345030 WHERE code = 'SEA40514';
-- 사량_답포 — 답포항 · 경남 통영시 사량면 돈지리 75-2 · 0.1km 이동
UPDATE ferry_port SET lat = 34.85970274, lng = 128.20020386 WHERE code = 'SEA97750';
-- 사량_대항 — 대항항(사량도) · 경남 통영시 사량면 금평리 356-1 · 0.2km 이동
UPDATE ferry_port SET lat = 34.85186428, lng = 128.21450186 WHERE code = 'SEA95312';
-- 사량_덕동 — 덕동항 · 경남 통영시 사량면 읍덕리 · 0.0km 이동
UPDATE ferry_port SET lat = 34.83871215, lng = 128.22132700 WHERE code = 'SEA40511';
-- 사량_돈지 — 돈지항 · 경남 통영시 사량면 돈지리 554-4 · 0.2km 이동
UPDATE ferry_port SET lat = 34.83762108, lng = 128.18038285 WHERE code = 'SEA97755';
-- 사량_먹방 — 먹방항 · 경남 통영시 사량면 읍덕리 57-5 · 0.1km 이동
UPDATE ferry_port SET lat = 34.83283416, lng = 128.23962983 WHERE code = 'SEA97751';
-- 사량_백학 — 백학항 · 경남 통영시 사량면 백학길 391-14 · 0.9km 이동
UPDATE ferry_port SET lat = 34.81144252, lng = 128.25652376 WHERE code = 'SEA40515';
-- 사량_사금 — 사금항 · 경남 통영시 사량면 금평리 1094-3 · 0.1km 이동
UPDATE ferry_port SET lat = 34.83020073, lng = 128.19547863 WHERE code = 'SEA97752';
-- 사량_양지 — 양지항 · 경남 통영시 사량면 양지리 311 · 0.6km 이동
UPDATE ferry_port SET lat = 34.80810920, lng = 128.23692413 WHERE code = 'SEA40512';
-- 사량_옥동 — 옥동항 · 경남 통영시 사량면 금평리 833-5 · 0.1km 이동
UPDATE ferry_port SET lat = 34.84180931, lng = 128.20267824 WHERE code = 'SEA40513';
-- 사량_외지 — 외지항 · 경남 통영시 사량면 양지리 678-3 · 0.0km 이동
UPDATE ferry_port SET lat = 34.81228860, lng = 128.21583112 WHERE code = 'SEA97754';
-- 사량_읍덕 — 읍포항 · 경남 통영시 사량면 읍덕리 368-8 · 2.8km 이동
UPDATE ferry_port SET lat = 34.82427615, lng = 128.21124938 WHERE code = 'SEA95330';
-- 사량_읍포 — 읍포항 · 경남 통영시 사량면 읍덕리 368-8 · 0.6km 이동
UPDATE ferry_port SET lat = 34.82427615, lng = 128.21124938 WHERE code = 'SEA97753';
-- 사량_진촌 — 진촌항 · 경남 통영시 사량면 진촌1길 102 · 0.4km 이동
UPDATE ferry_port SET lat = 34.84223900, lng = 128.22025743 WHERE code = 'SEA95311';
-- 사량도 — 금평항 · 경남 통영시 사량면 금평리 208-4 · 0.0km 이동
UPDATE ferry_port SET lat = 34.84377295, lng = 128.22430550 WHERE code = 'SEA40510';
-- 사옥도 — 무인민원발급창구 지신개 선착장 · 전남광주통합특별시 신안군 지도읍 사옥길 465 · 3.2km 이동
UPDATE ferry_port SET lat = 35.02471353, lng = 126.17002525 WHERE code = 'SEA33230';
-- 사치도 — 사치항 · 전남광주통합특별시 신안군 안좌면 한운리 392-3 · 0.2km 이동
UPDATE ferry_port SET lat = 34.75525086, lng = 126.06107322 WHERE code = 'SEA33260';
-- 산등 — 산등항 · 경남 통영시 욕지면 노대리 · 새로 채움
UPDATE ferry_port SET lat = 34.67580382, lng = 128.23338456 WHERE code = 'SEA40532';
-- 산등 — 산등항 · 경남 통영시 욕지면 노대리 · 새로 채움
UPDATE ferry_port SET lat = 34.67580382, lng = 128.23338456 WHERE code = 'SEA96210';
-- 삼목 — 삼목항(장봉,신도) · 인천 영종구 운서동 · 새로 채움
UPDATE ferry_port SET lat = 37.49978578, lng = 126.45242832 WHERE code = 'SEA96700';
-- 삼천포 — 삼천포항 · 경남 사천시 어시장길 34-10 · 0.0km 이동
UPDATE ferry_port SET lat = 34.92644157, lng = 128.06998525 WHERE code = 'SEA96220';
-- 삼천포신항 — 삼천포신항 여객터미널 · 경남 사천시 신항만1길 76 · 1.7km 이동
UPDATE ferry_port SET lat = 34.92696953, lng = 128.08837681 WHERE code = 'SEA40020';
-- 삽시도 — 삽시도항 · 충남 보령시 오천면 삽시도리 · 0.0km 이동
UPDATE ferry_port SET lat = 36.35185637, lng = 126.36407848 WHERE code = 'SEA22230';
-- 상낙월도 — 상낙월도선착장 · 전남광주통합특별시 영광군 낙월면 상낙월리 산 209-1 · 19.9km 이동
UPDATE ferry_port SET lat = 35.20083402, lng = 126.14396756 WHERE code = 'SEA33290';
-- 상노대도 — 상리항 · 경남 통영시 욕지면 노대리 · 새로 채움
UPDATE ferry_port SET lat = 34.67010006, lng = 128.25229942 WHERE code = 'SEA40530';
-- 상왕등도 — 상왕등도항 · 전북특별자치도 부안군 위도면 상왕등리 · 92.3km 이동
UPDATE ferry_port SET lat = 35.65803951, lng = 126.11078331 WHERE code = 'SEA30270';
-- 상조도 — 나배도선착장 · 전남광주통합특별시 진도군 조도면 나배도리 25-5 · 3.3km 이동
UPDATE ferry_port SET lat = 34.31095557, lng = 126.01317203 WHERE code = 'SEA33310';
-- 상태도 — 상태도항 · 전남광주통합특별시 신안군 흑산면 태도리 산 113-3 · 77.5km 이동
UPDATE ferry_port SET lat = 34.43535038, lng = 125.28522640 WHERE code = 'SEA33330';
-- 상하죽도 — 상하죽도항 · 전남광주통합특별시 진도군 조도면 서거차도리 179-2 · 0.0km 이동
UPDATE ferry_port SET lat = 34.24986779, lng = 125.92364045 WHERE code = 'SEA33360';
-- 상화도 — 상화도항 · 전남광주통합특별시 여수시 화정면 · 43.6km 이동
UPDATE ferry_port SET lat = 34.59664857, lng = 127.60334899 WHERE code = 'SEA33390';
-- 생일_서성 — 서성항 · 전남광주통합특별시 완도군 생일면 유서리 390-18 · 0.1km 이동
UPDATE ferry_port SET lat = 34.33676289, lng = 126.99577477 WHERE code = 'SEA33412';
-- 생일도 — 서성항 · 전남광주통합특별시 완도군 생일면 유서리 390-18 · 3.4km 이동
UPDATE ferry_port SET lat = 34.33676289, lng = 126.99577477 WHERE code = 'SEA33410';
-- 서거차도 — 서거차도항 · 전남광주통합특별시 진도군 조도면 서거차도리 210 · 0.0km 이동
UPDATE ferry_port SET lat = 34.25392749, lng = 125.91623273 WHERE code = 'SEA33430';
-- 서검 — 서검항 · 인천 강화군 삼산면 서검리 · 49.8km 이동
UPDATE ferry_port SET lat = 37.73149440, lng = 126.23641783 WHERE code = 'SEA96460';
-- 서산_삼길포 — 삼길포항 · 충남 서산시 대산읍 화곡리 1891 · 0.0km 이동
UPDATE ferry_port SET lat = 37.00354176, lng = 126.45279027 WHERE code = 'SEA97490';
-- 서소우이도 — 서소우이도항 · 전남광주통합특별시 신안군 도초면 우이도리 1432 · 50.6km 이동
UPDATE ferry_port SET lat = 34.60964526, lng = 125.87141423 WHERE code = 'SEA33510';
-- 선도 — 선도항 · 전남광주통합특별시 신안군 지도읍 선도리 277-6 · 131.7km 이동
UPDATE ferry_port SET lat = 34.97611410, lng = 126.26971141 WHERE code = 'SEA33530';
-- 선수 — 무인민원발급창구 화도선수항 · 인천 강화군 화도면 해안남로 2781 · 새로 채움
UPDATE ferry_port SET lat = 37.63845251, lng = 126.38559762 WHERE code = 'SEA97090';
-- 선유도 — 선유도항 · 전북특별자치도 군산시 옥도면 선유도리 · 새로 채움
UPDATE ferry_port SET lat = 35.81079038, lng = 126.41649322 WHERE code = 'SEA30290';
-- 성남도 — 성남항 · 전남광주통합특별시 진도군 조도면 성남도리 37-4 · 52.9km 이동
UPDATE ferry_port SET lat = 34.39628281, lng = 126.04481669 WHERE code = 'SEA33590';
-- 소거문도 — 소거문항 · 전남광주통합특별시 여수시 삼산면 손죽리 산 475 · 59.7km 이동
UPDATE ferry_port SET lat = 34.28390575, lng = 127.38886769 WHERE code = 'SEA33630';
-- 소기점도 — 소기점도선착장 · 전남광주통합특별시 신안군 증도면 병풍리 1175 · 9.1km 이동
UPDATE ferry_port SET lat = 34.92795269, lng = 126.20789982 WHERE code = 'SEA32170';
-- 소난지도 — 소난지항 · 충남 당진시 석문면 난지도리 · 0.0km 이동
UPDATE ferry_port SET lat = 37.04143488, lng = 126.45309843 WHERE code = 'SEA22250';
-- 소도 — 소도항 · 충남 보령시 오천면 효자도리 · 새로 채움
UPDATE ferry_port SET lat = 36.40012429, lng = 126.43543417 WHERE code = 'SEA22270';
-- 소랑도 — 소랑항 · 전남광주통합특별시 완도군 금일읍 · 새로 채움
UPDATE ferry_port SET lat = 34.32956284, lng = 127.08403450 WHERE code = 'SEA33690';
-- 소마도 — 소마항 · 전남광주통합특별시 진도군 조도면 소마도리 14-8 · 0.0km 이동
UPDATE ferry_port SET lat = 34.30040318, lng = 125.98284583 WHERE code = 'SEA33710';
-- 소매물도 — 소매물도항 · 경남 통영시 한산면 매죽리 193-36 · 12.5km 이동
UPDATE ferry_port SET lat = 34.62922944, lng = 128.54798234 WHERE code = 'SEA40550';
-- 소모도 — 소모도항 · 전남광주통합특별시 완도군 청산면 · 9.8km 이동
UPDATE ferry_port SET lat = 34.22869478, lng = 126.77092590 WHERE code = 'SEA33730';
-- 소성남도 — 소성남항 · 전남광주통합특별시 진도군 조도면 성남도리 산 111-1 · 53.0km 이동
UPDATE ferry_port SET lat = 34.39995098, lng = 126.03611526 WHERE code = 'SEA33790';
-- 소악도 — 소악도선착장 · 전남광주통합특별시 신안군 증도면 병풍리 산 254-8 · 0.0km 이동
UPDATE ferry_port SET lat = 34.91861965, lng = 126.20096948 WHERE code = 'SEA33810';
-- 소안도 — 소안항 · 전남광주통합특별시 완도군 소안면 이월리 1238-10 · 0.1km 이동
UPDATE ferry_port SET lat = 34.17991415, lng = 126.63450400 WHERE code = 'SEA33830';
-- 소야도 — 소야도항 · 인천 옹진군 덕적면 소야리 · 0.4km 이동
UPDATE ferry_port SET lat = 37.22415373, lng = 126.16060923 WHERE code = 'SEA97020';
-- 소연평 — 소연평항 · 인천 옹진군 연평면 연평리 · 79.5km 이동
UPDATE ferry_port SET lat = 37.61376205, lng = 125.72109011 WHERE code = 'SEA10040';
-- 소이작도 — 소이작항 · 인천 옹진군 자월면 이작리 · 43.7km 이동
UPDATE ferry_port SET lat = 37.18176823, lng = 126.24279768 WHERE code = 'SEA10140';
-- 소청도 — 소청항 · 인천 옹진군 대청면 소청리 · 167.5km 이동
UPDATE ferry_port SET lat = 37.77629379, lng = 124.74761098 WHERE code = 'SEA10010';
-- 속초 — 속초항 · 강원특별자치도 속초시 청호동 · 0.3km 이동
UPDATE ferry_port SET lat = 38.20851840, lng = 128.59572777 WHERE code = 'SEA44050';
-- 손죽도 — 손죽도항 · 전남광주통합특별시 여수시 삼산면 손죽리 산 635 · 60.5km 이동
UPDATE ferry_port SET lat = 34.29016070, lng = 127.36132950 WHERE code = 'SEA33890';
-- 송이도 — 송이도항 · 전남광주통합특별시 영광군 낙월면 송이리 산 158-29 · 26.7km 이동
UPDATE ferry_port SET lat = 35.27338547, lng = 126.14974099 WHERE code = 'SEA33990';
-- 수도_신안군 — 수도선착장 · 전남광주통합특별시 신안군 임자면 수도리 34 · 0.6km 이동
UPDATE ferry_port SET lat = 35.08262196, lng = 126.14177455 WHERE code = 'SEA34010';
-- 수우도 — 수우도 선착장 · 경남 통영시 사량면 돈지리 산 363-7 · 11.4km 이동
UPDATE ferry_port SET lat = 34.83449630, lng = 128.13780022 WHERE code = 'SEA97000';
-- 수치도 — 수치항 · 전남광주통합특별시 신안군 비금면 수치리 939 · 0.0km 이동
UPDATE ferry_port SET lat = 34.74481150, lng = 126.01201759 WHERE code = 'SEA34030';
-- 슬도 — 슬도선착장 · 전남광주통합특별시 진도군 조도면 독거도리 286-5 · 328.5km 이동
UPDATE ferry_port SET lat = 34.26196212, lng = 126.15148585 WHERE code = 'SEA34060';
-- 승봉도 — 승봉리항 · 인천 옹진군 자월면 승봉리 · 28.7km 이동
UPDATE ferry_port SET lat = 37.17026741, lng = 126.29115217 WHERE code = 'SEA10160';
-- 시하도 — 시하도선착장 · 전남광주통합특별시 해남군 화원면 치하리 산 11-1 · 새로 채움
UPDATE ferry_port SET lat = 34.69816834, lng = 126.24252477 WHERE code = 'SEA34090';
-- 식도 — 식도항 · 전북특별자치도 부안군 위도면 식도리 · 새로 채움
UPDATE ferry_port SET lat = 35.62507810, lng = 126.28866197 WHERE code = 'SEA30310';
-- 신도_옹진군 — 신도선착장 공영주차장 · 인천 옹진군 북도면 신도리 523-61 · 0.1km 이동
UPDATE ferry_port SET lat = 37.51508516, lng = 126.43890272 WHERE code = 'SEA96710';
-- 신시도 — 신시도항 · 전북특별자치도 군산시 옥도면 신시도길 42 · 0.1km 이동
UPDATE ferry_port SET lat = 35.82324015, lng = 126.47388846 WHERE code = 'SEA30330';
-- 신지도 — 송곡항 · 전남광주통합특별시 완도군 신지면 송곡리 267-5 · 2.8km 이동
UPDATE ferry_port SET lat = 34.34314258, lng = 126.78306245 WHERE code = 'SEA34160';
-- 아차도 — 아차항 · 인천 강화군 서도면 아차도리 · 13.7km 이동
UPDATE ferry_port SET lat = 37.66002202, lng = 126.23453939 WHERE code = 'SEA96510';
-- 안도 — 안도항 · 전남광주통합특별시 여수시 남면 안도리 · 새로 채움
UPDATE ferry_port SET lat = 34.47684067, lng = 127.79566705 WHERE code = 'SEA34190';
-- 안마도 — 안마도항 · 전남광주통합특별시 영광군 낙월면 안마길 17 · 0.0km 이동
UPDATE ferry_port SET lat = 35.34552268, lng = 126.01793046 WHERE code = 'SEA34210';
-- 안면도 — 방포항 · 충남 태안군 안면읍 승언리 · 10.4km 이동
UPDATE ferry_port SET lat = 36.50382795, lng = 126.33585216 WHERE code = 'SEA22290';
-- 안좌_금산 — 금산항 · 전남광주통합특별시 신안군 안좌면 · 0.8km 이동
UPDATE ferry_port SET lat = 34.75591973, lng = 126.16318955 WHERE code = 'SEA34232';
-- 안좌_복호 — 복호항 · 전남광주통합특별시 신안군 안좌면 복호리 73-18 · 0.0km 이동
UPDATE ferry_port SET lat = 34.69988728, lng = 126.16736341 WHERE code = 'SEA34233';
-- 안좌_읍동 — 읍동선착장 · 전남광주통합특별시 신안군 안좌면 안좌동부길 764-32 · 1.0km 이동
UPDATE ferry_port SET lat = 34.75963985, lng = 126.13319490 WHERE code = 'SEA34231';
-- 안좌도 — 읍동선착장 · 전남광주통합특별시 신안군 안좌면 안좌동부길 764-32 · 0.1km 이동
UPDATE ferry_port SET lat = 34.75963985, lng = 126.13319490 WHERE code = 'SEA34230';
-- 안흥 — 안흥항 · 충남 태안군 근흥면 정죽리 1298-5 · 195.5km 이동
UPDATE ferry_port SET lat = 36.68099829, lng = 126.15241788 WHERE code = 'SEA22330';
-- 애도 — 애도선착장 · 전남광주통합특별시 고흥군 봉래면 사양리 809 · 새로 채움
UPDATE ferry_port SET lat = 34.46424942, lng = 127.44948759 WHERE code = 'SEA31770';
-- 야미도 — 야미도항 · 전북특별자치도 군산시 옥도면 야미도리 · 0.1km 이동
UPDATE ferry_port SET lat = 35.84035438, lng = 126.48839478 WHERE code = 'SEA30350';
-- 어류정 — 어류정항 · 인천 강화군 삼산면 어류정길177번길 117 · 새로 채움
UPDATE ferry_port SET lat = 37.64503520, lng = 126.34429272 WHERE code = 'SEA96690';
-- 어청도 — 어청도항 · 전북특별자치도 군산시 옥도면 어청도리 387-9 · 60.2km 이동
UPDATE ferry_port SET lat = 36.11646435, lng = 125.98327140 WHERE code = 'SEA30370';
-- 여의도 — 진성나루 · 서울 영등포구 여의도동 · 새로 채움
UPDATE ferry_port SET lat = 37.52496259, lng = 126.93902892 WHERE code = 'SEA97670';
-- 연안 — 인천연안항 · 인천 제물포구 항동7가 · 새로 채움
UPDATE ferry_port SET lat = 37.45429070, lng = 126.59716775 WHERE code = 'SEA97600';
-- 연화도 — 연화항 · 경남 통영시 욕지면 연화리 산 2-122 · 0.0km 이동
UPDATE ferry_port SET lat = 34.65039870, lng = 128.35176180 WHERE code = 'SEA40630';
-- 영흥면 — 넛출항드무리해안길 · 인천 옹진군 영흥면 선재리 산 15 · 2.7km 이동
UPDATE ferry_port SET lat = 37.25749138, lng = 126.51413867 WHERE code = 'SEA10200';
-- 오륙도 — 오륙도유람선선착장 · 부산 남구 용호동 산 196-4 · 0.0km 이동
UPDATE ferry_port SET lat = 35.09982619, lng = 129.12323848 WHERE code = 'SEA97710';
-- 오비도 — 오비도선착장 · 경남 통영시 산양읍 풍화리 산 300-2 · 2.3km 이동
UPDATE ferry_port SET lat = 34.82015609, lng = 128.36506863 WHERE code = 'SEA96900';
-- 오천 — 오천항 · 충남 보령시 오천면 소성리 · 265.1km 이동
UPDATE ferry_port SET lat = 36.43926778, lng = 126.51963236 WHERE code = 'SEA22350';
-- 완도 — 완도항 · 전남광주통합특별시 완도군 완도읍 장보고대로 339 · 1.2km 이동
UPDATE ferry_port SET lat = 34.31630911, lng = 126.75941169 WHERE code = 'SEA31020';
-- 완도_화흥포 — 화흥포항 대합실 · 전남광주통합특별시 완도군 완도읍 화흥포길 242 · 0.0km 이동
UPDATE ferry_port SET lat = 34.30536301, lng = 126.67938495 WHERE code = 'SEA31022';
-- 외연도 — 외연도항 · 충남 보령시 오천면 외연도리 160-87 · 40.2km 이동
UPDATE ferry_port SET lat = 36.22513252, lng = 126.08230636 WHERE code = 'SEA22370';
-- 외포 — 외포리선착장 · 인천 강화군 내가면 외포리 547-93 · 26.7km 이동
UPDATE ferry_port SET lat = 37.70043985, lng = 126.38092942 WHERE code = 'SEA96490';
-- 욕지도 — 욕지항 · 경남 통영시 욕지면 동항리 · 0.0km 이동
UPDATE ferry_port SET lat = 34.63460213, lng = 128.26702077 WHERE code = 'SEA40670';
-- 용초도 — 용초항 · 경남 통영시 한산면 용호리 · 11.9km 이동
UPDATE ferry_port SET lat = 34.74529106, lng = 128.48165601 WHERE code = 'SEA40690';
-- 용호도 — 오륙도유람선선착장 · 부산 남구 용호동 산 196-4 · 70.3km 이동
UPDATE ferry_port SET lat = 35.09982619, lng = 129.12323848 WHERE code = 'SEA96840';
-- 우도 — 우도항(통영) · 경남 통영시 욕지면 연화리 · 181.1km 이동
UPDATE ferry_port SET lat = 34.65748659, lng = 128.34437111 WHERE code = 'SEA97650';
-- 울도 — 울도항 · 인천 옹진군 덕적면 울도리 · 새로 채움
UPDATE ferry_port SET lat = 37.02710223, lng = 125.99822578 WHERE code = 'SEA96360';
-- 울릉_도동 — 도동항 · 경북 울릉군 울릉읍 도동리 · 1.1km 이동
UPDATE ferry_port SET lat = 37.48152580, lng = 130.90881500 WHERE code = 'SEA43113';
-- 울릉_사동 — 사동항(울릉도) · 경북 울릉군 울릉읍 울릉순환로 755 · 0.1km 이동
UPDATE ferry_port SET lat = 37.46122748, lng = 130.87891255 WHERE code = 'SEA43112';
-- 울릉_저동 — 저동항 · 경북 울릉군 울릉읍 저동리 48-8 · 0.4km 이동
UPDATE ferry_port SET lat = 37.49621818, lng = 130.90996948 WHERE code = 'SEA43111';
-- 울릉도 — 도동항 · 경북 울릉군 울릉읍 도동리 · 새로 채움
UPDATE ferry_port SET lat = 37.48152580, lng = 130.90881500 WHERE code = 'SEA43110';
-- 원산도 — 선촌항 · 충남 보령시 오천면 원산도리 474-9 · 2.1km 이동
UPDATE ferry_port SET lat = 36.38303010, lng = 126.43389941 WHERE code = 'SEA22390';
-- 월도 — 월도항 · 충남 보령시 오천면 효자도리 · 22.2km 이동
UPDATE ferry_port SET lat = 36.40948343, lng = 126.46875907 WHERE code = 'SEA22410';
-- 월선 — 월선포항 · 인천 강화군 교동면 상용리 · 47.0km 이동
UPDATE ferry_port SET lat = 37.77460446, lng = 126.31710753 WHERE code = 'SEA97080';
-- 위도 — 위도항 · 전북특별자치도 부안군 위도면 진리 · 1.9km 이동
UPDATE ferry_port SET lat = 35.61836727, lng = 126.30067306 WHERE code = 'SEA30410';
-- 위도_파장금 — 파장금선착장 · 전북특별자치도 부안군 위도면 진리 · 0.5km 이동
UPDATE ferry_port SET lat = 35.61820291, lng = 126.30092615 WHERE code = 'SEA30411';
-- 육도 — 육도항(안산) · 경기 안산시 단원구 풍도동 · 66.4km 이동
UPDATE ferry_port SET lat = 37.09729566, lng = 126.45358358 WHERE code = 'SEA10070';
-- 육도 — 육도항(안산) · 경기 안산시 단원구 풍도동 · 66.4km 이동
UPDATE ferry_port SET lat = 37.09729566, lng = 126.45358358 WHERE code = 'SEA96540';
-- 이작도 — 대이작항 · 인천 옹진군 자월면 이작리 · 43.7km 이동
UPDATE ferry_port SET lat = 37.17857384, lng = 126.24771216 WHERE code = 'SEA10150';
-- 인천 — 인천항 연안여객터미널 · 인천 제물포구 연안부두로 70 · 9.4km 이동
UPDATE ferry_port SET lat = 37.45409981, lng = 126.59852840 WHERE code = 'SEA10100';
-- 자갈치시장 — 남항유람선선착장 · 부산 중구 자갈치해안로 60 · 0.2km 이동
UPDATE ferry_port SET lat = 35.09669328, lng = 129.03168467 WHERE code = 'SEA95520';
-- 자월도 — 자월항 · 인천 옹진군 자월면 자월리 · 0.0km 이동
UPDATE ferry_port SET lat = 37.24462866, lng = 126.31830785 WHERE code = 'SEA10130';
-- 잠진도 — 잠진도선착장 · 인천 영종구 덕교동 103-21 · 0.0km 이동
UPDATE ferry_port SET lat = 37.41691090, lng = 126.41502696 WHERE code = 'SEA97130';
-- 장고도 — 장고도항 · 충남 보령시 오천면 삽시도리 · 72.1km 이동
UPDATE ferry_port SET lat = 36.39951555, lng = 126.35508028 WHERE code = 'SEA22470';
-- 장목_거제 — 시방항여객선터미널 · 경남 거제시 장목면 시방2길 27-5 · 3.7km 이동
UPDATE ferry_port SET lat = 34.96463609, lng = 128.70883485 WHERE code = 'SEA95070';
-- 장봉도 — 장봉항 · 인천 옹진군 북도면 장봉리 · 0.0km 이동
UPDATE ferry_port SET lat = 37.53089816, lng = 126.38423412 WHERE code = 'SEA96720';
-- 장사도 — 장사도선착장 · 경남 통영시 한산면 매죽리 · 16.7km 이동
UPDATE ferry_port SET lat = 34.71717677, lng = 128.55719623 WHERE code = 'SEA40790';
-- 장자도 — 장자도항 · 전북특별자치도 군산시 옥도면 장자도리 · 0.1km 이동
UPDATE ferry_port SET lat = 35.81010659, lng = 126.39952671 WHERE code = 'SEA30430';
-- 장항 — 장항항 · 충남 서천군 장항읍 장산로 232 · 183.9km 이동
UPDATE ferry_port SET lat = 36.00726016, lng = 126.68543262 WHERE code = 'SEA22490';
-- 조도 — 작은섬 선착장 · 경남 남해군 미조면 조도길 5-6 · 새로 채움
UPDATE ferry_port SET lat = 34.69735058, lng = 128.05025235 WHERE code = 'SEA40870';
-- 주문도 — 살곶이선착장 · 인천 강화군 서도면 주문도리 산 4-2 · 3.4km 이동
UPDATE ferry_port SET lat = 37.62965328, lng = 126.25938927 WHERE code = 'SEA96520';
-- 중화 — 중화항 · 경남 통영시 산양읍 연화리 706-17 · 새로 채움
UPDATE ferry_port SET lat = 34.78975477, lng = 128.38917673 WHERE code = 'SEA41100';
-- 지신개 — 지신개선착장 · 전남광주통합특별시 신안군 지도읍 탄동리 482-2 · 0.0km 이동
UPDATE ferry_port SET lat = 35.02458857, lng = 126.17046809 WHERE code = 'SEA32550';
-- 진해 — 진해항 · 경남 창원시 진해구 덕산동 · 2.5km 이동
UPDATE ferry_port SET lat = 35.13795554, lng = 128.68252612 WHERE code = 'SEA40930';
-- 진해_속천 — 속천항 · 경남 창원시 진해구 제황산동 · 0.0km 이동
UPDATE ferry_port SET lat = 35.14405327, lng = 128.67328595 WHERE code = 'SEA40932';
-- 진해_안골 — 안골부두 · 경남 창원시 진해구 안골동 · 1.3km 이동
UPDATE ferry_port SET lat = 35.09261275, lng = 128.78827878 WHERE code = 'SEA40931';
-- 창후 — 창후항 · 인천 강화군 하점면 창후리 · 새로 채움
UPDATE ferry_port SET lat = 37.76976726, lng = 126.35401593 WHERE code = 'SEA97070';
-- 추도_미조 — 미조항 · 경남 통영시 산양읍 추도리 · 0.2km 이동
UPDATE ferry_port SET lat = 34.75733159, lng = 128.28851179 WHERE code = 'SEA40952';
-- 추도_통영시 — 한목항 · 경남 통영시 산양읍 추도리 · 14.1km 이동
UPDATE ferry_port SET lat = 34.75848466, lng = 128.30111547 WHERE code = 'SEA40950';
-- 추봉도 — 봉암항 · 경남 통영시 한산면 추봉리 911-4 · 0.7km 이동
UPDATE ferry_port SET lat = 34.75935735, lng = 128.50986758 WHERE code = 'SEA40970';
-- 탄항 — 탄항항(통영) · 경남 통영시 욕지면 노대리 · 0.0km 이동
UPDATE ferry_port SET lat = 34.67314754, lng = 128.25554088 WHERE code = 'SEA40533';
-- 통영 — 통영항 · 경남 통영시 통영해안로 234 · 2.1km 이동
UPDATE ferry_port SET lat = 34.83944973, lng = 128.42032200 WHERE code = 'SEA40050';
-- 평택 — 평택항만 · 경기 평택시 포승읍 평택항만길 145 · 24.5km 이동
UPDATE ferry_port SET lat = 36.96130058, lng = 126.84109017 WHERE code = 'SEA22040';
-- 포항 — 포항항 · 경북 포항시 북구 항구동 58-51 · 1.5km 이동
UPDATE ferry_port SET lat = 36.05129201, lng = 129.37876342 WHERE code = 'SEA43010';
-- 포항영일만 — 포항영일만항 · 경북 포항시 북구 흥해읍 영일만항로 151 · 4.7km 이동
UPDATE ferry_port SET lat = 36.11047085, lng = 129.43529867 WHERE code = 'SEA43011';
-- 풍도 — 풍도항 · 경기 안산시 단원구 풍도동 · 0.2km 이동
UPDATE ferry_port SET lat = 37.11296333, lng = 126.39356174 WHERE code = 'SEA10060';
-- 하노대도 — 하노대도항 · 경남 통영시 욕지면 노대리 · 0.4km 이동
UPDATE ferry_port SET lat = 34.66799962, lng = 128.25179979 WHERE code = 'SEA41030';
-- 하동 — 노량항 · 경남 하동군 금남면 노량리 742-12 · 16.6km 이동
UPDATE ferry_port SET lat = 34.94701134, lng = 127.86043978 WHERE code = 'SEA96300';
-- 하리 — 하리항 · 인천 강화군 삼산면 하리 · 127.6km 이동
UPDATE ferry_port SET lat = 37.72778812, lng = 126.28739171 WHERE code = 'SEA96470';
-- 한산도 — 진두항 · 경남 통영시 한산면 하소리 43-13 · 4.6km 이동
UPDATE ferry_port SET lat = 34.76659734, lng = 128.50741309 WHERE code = 'SEA41050';
-- 허육도 — 허육도항 · 충남 보령시 오천면 효자도리 · 새로 채움
UPDATE ferry_port SET lat = 36.40487015, lng = 126.45767810 WHERE code = 'SEA22530';
-- 혈도 — 혈도항 · 전남광주통합특별시 진도군 조도면 가사도리 · 0.0km 이동
UPDATE ferry_port SET lat = 34.51685336, lng = 126.08719064 WHERE code = 'SEA34070';
-- 호도 — 호도항 · 충남 보령시 오천면 녹도리 · 97.6km 이동
UPDATE ferry_port SET lat = 36.30335762, lng = 126.26451593 WHERE code = 'SEA96240';
-- 화도 — 화도항 · 경남 거제시 둔덕면 술역리 · 새로 채움
UPDATE ferry_port SET lat = 34.82818578, lng = 128.47375696 WHERE code = 'SEA41070';
-- 화도(거제시) — 화도선착장 · 경남 거제시 둔덕면 술역리 389-3 · 새로 채움
UPDATE ferry_port SET lat = 34.83744008, lng = 128.49064266 WHERE code = 'SEA41090';
-- 효자도 — 효자도항 · 충남 보령시 오천면 효자도리 · 87.2km 이동
UPDATE ferry_port SET lat = 36.38490896, lng = 126.43923548 WHERE code = 'SEA22550';
-- 후포 — 후포항 · 경북 울진군 후포면 울진대게로 236-14 · 1.6km 이동
UPDATE ferry_port SET lat = 36.67820824, lng = 129.45794737 WHERE code = 'SEA43030';

-- ── 확인하지 못해 비우는 좌표
-- 가란도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA31110';
-- 가오도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40310';
-- 각흘도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA31160';
-- 개도-신안군 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA31260';
-- 개도_모전 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA31232';
-- 거금도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA31290';
-- 거륜도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA30130';
-- 거문_서도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA33490';
-- 거제_유호 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40032';
-- 거제_황포 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40034';
-- 고금_덕동 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA31331';
-- 고평사도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA31390';
-- 곡용포 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA96190';
-- 곤리도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA96890';
-- 구도_서산시 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA22170';
-- 군산_외항 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA30011';
-- 금오도_직포 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA31661';
-- 납도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40370';
-- 넙도_노화 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA31810';
-- 노록도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA31860';
-- 노화_이목 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA31893';
-- 당사_등대 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA32111';
-- 당진_장고(순회) — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA22701';
-- 대각시도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA32130';
-- 대모도_모동 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA32232';
-- 대모도_모서 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA32231';
-- 덕적도_북리 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA10111';
-- 덕적도_서포리 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA10112';
-- 덕적도_진리 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA10110';
-- 도장_금일도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA31691';
-- 독도(편도) — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA96331';
-- 돌산(순회) — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA32491';
-- 동백부두 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA97730';
-- 동송_금일도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA31693';
-- 두미_남구 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40391';
-- 두미_북구 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40392';
-- 마산_신마산 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA97480';
-- 막금(복) — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA32731';
-- 만지도 — 만지도 — 어의도선착장(신안)으로 잡혔다. 만지도는 통영이다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40410';
-- 말도_강화군 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA96780';
-- 모도_옹진군 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA96820';
-- 반월(복) — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA32911';
-- 부도_여수시 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA33090';
-- 부산_용호동(순회) — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA97591';
-- 비진_내항 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40491';
-- 비진_외항 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40492';
-- 삼천포_서동 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA97760';
-- 삼천포_서동(순회) — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA97761';
-- 삼천포_팔포 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA96222';
-- 상노대 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40531';
-- 상조_맹성 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA33311';
-- 상조_여미 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA33312';
-- 상조_율목 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA33313';
-- 생일_용출 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA33411';
-- 서넙도_노화 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA33460';
-- 서상_남해 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA97450';
-- 석모도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA96850';
-- 석모도_보문 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA96852';
-- 석모도_석포 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA96851';
-- 섭도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA33560';
-- 소흑산_가거도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA33863';
-- 소흑산도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA33860';
-- 송도_돌산 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA33960';
-- 송도_율촌 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA33930';
-- 송도_진도군 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA33910';
-- 송도_통영시 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40570';
-- 수도_통영시 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA97240';
-- 시도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA96810';
-- 신도_신안군 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA34130';
-- 신도_완도군 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA34110';
-- 신지_강독 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA34161';
-- 신지_동고 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA34163';
-- 신지_임촌 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA34162';
-- 실전_거제 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA95030';
-- 쑥섬 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA31771';
-- 안면_영목 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA22291';
-- 안흥_외항 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA22331';
-- 어의도_통영시 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA97250';
-- 연대도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40610';
-- 연도_군산시 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA30390';
-- 연도_통영시 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA97270';
-- 오곡도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA96200';
-- 완도(순회) — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA31021';
-- 용초_호두 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40692';
-- 우도_서산 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA97330';
-- 우도_통영 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40710';
-- 위도_벌금 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA30412';
-- 유부도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA22430';
-- 율도_눌도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA31930';
-- 읍도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA97260';
-- 인천(도착) — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA10101';
-- 입파도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA10630';
-- 자갈치시장(순회) — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA95521';
-- 작약도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA10180';
-- 저도_광도면 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA97290';
-- 저도_산양면 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40830';
-- 적촌(원평) — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40110';
-- 정서진T — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA97610';
-- 좌도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40890';
-- 좌도_동좌 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40892';
-- 좌도_서좌 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40891';
-- 죽도_군산시 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA30450';
-- 죽도_통영시 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40910';
-- 죽도_홍성군 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA22650';
-- 지도_옹진군 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA96400';
-- 지도_통영시 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA97230';
-- 추도_보령시 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA22510';
-- 추도대항(한목) — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40951';
-- 추봉_봉암 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40971';
-- 칠천_대곡 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40991';
-- 칠천도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA40990';
-- 하왕등도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA30470';
-- 한산_관암 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA41057';
-- 한산_두억 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA41051';
-- 한산_소고포 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA41059';
-- 한산_야소 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA41052';
-- 한산_여차 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA41053';
-- 한산_의암 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA41054';
-- 한산_의항 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA95210';
-- 한산_제승당 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA95230';
-- 한산_진두 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA41055';
-- 한산_진두(순회) — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA97780';
-- 한산_창동 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA41056';
-- 한산_하포 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA41058';
-- 해간도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA97220';
-- 해경부두 — 해경부두 — 완도항해경전용부두로 잡혔다. 이름이 너무 일반적이라 지역을 특정할 수 없다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA95550';
-- 해경부두(순회) — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA95551';
-- 화전_금일도 — 항구 계열에서 같은 이름을 찾지 못했다
UPDATE ferry_port SET lat = NULL, lng = NULL WHERE code = 'SEA31692';
