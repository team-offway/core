# OffWay 외부 데이터 인벤토리 · 발급 체크리스트

- 조사일: 2026-07-18
- 목적: OffWay가 끌어올 **모든** 외부 데이터의 발급처·인증·엔드포인트·가용 데이터를 한곳에. 이 문서가 곧 (1) 회원가입/키 발급 체크리스트, (2) 우리가 조합할 데이터 전체 목록.
- 전략: **미리 전부 확보·적재(라이브 API + 정적 시딩)** 해두고 조합해 서비스를 제공한다.

> ⚠️ 신뢰도 표기: 아래 상당수는 조사 기반이며 일부 오퍼레이션명·파라미터 철자는 **활용신청 후 각 상세페이지의 활용가이드/Swagger로 최종 확정** 필요. "추정" 표시된 항목 주의.

---

## 0. 회원가입은 딱 3곳 (대부분 data.go.kr 하나로 커버)

| # | 포털 | URL | 커버하는 데이터 | 인증 방식 |
|---|---|---|---|---|
| 1 | **공공데이터포털 data.go.kr** | https://www.data.go.kr | 특일정보 · TourAPI · 관광빅데이터 · TAGO(버스/열차) · 코레일 · 생활인구(파일) | 계정 1개 + API별 **활용신청** → `serviceKey` |
| 2 | **SK openapi.sk.com** | https://openapi.sk.com | TMAP 경로/경유지 최적화 | 앱 등록 → `appKey`(헤더) |
| 3 | (선택) **통계청 SGIS** | https://sgis.kostat.go.kr/developer | 센서스 인구·통계(생활인구 보강용) | key+secret → accessToken |

**data.go.kr 서비스키 핵심 함정**: 발급 화면에 **Encoding 키 / Decoding 키** 두 종류가 나온다. HTTP 클라이언트가 파라미터를 자동 인코딩하면 **Decoding 키**, 아니면 **Encoding 키**를 쓴다. 잘못 쓰면 `SERVICE_KEY_IS_NOT_REGISTERED_ERROR`. → **application secret으로 두 키 다 보관**하고, 한쪽 실패 시 반대로 시도.

---

## 1. 라이브 REST API (클라이언트 구현 대상 → `external/` port)

### 1-1. 특일 정보 (공휴일·대체공휴일) — 한국천문연구원
- **발급**: data.go.kr 데이터셋 **15012690** `한국천문연구원_특일 정보` → [활용신청](https://www.data.go.kr/data/15012690/openapi.do) (자동승인)
- **Base**: `https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService`
- **오퍼레이션**: `getRestDeInfo`(공휴일+대체공휴일 — **이걸 사용**), `getHoliDeInfo`(국경일), `getAnniversaryInfo`, `get24DivisionsInfo`
- **파라미터**: `serviceKey`, `solYear`(필수), `solMonth`(권장, 월별 반복 호출 안전), `_type=json`, `numOfRows`, `pageNo`
- **응답 필드**: `locdate`(YYYYMMDD), `dateName`(예 "대체공휴일"), `isHoliday`(Y/N), `dateKind`
- **한도**: 무료, 개발계정 일 10,000건(추정)
- **함정**: 대체공휴일은 별도 플래그 없이 `dateName`="대체공휴일"로 판별. 미래 연도는 지정 전 누락 가능.
- **OffWay 용도**: LNT 산출 · 샌드위치 연휴 탐지의 기반.

### 1-2. 국문 관광정보 서비스 (TourAPI) — 한국관광공사
- **발급**: data.go.kr 데이터셋 **15101578** `한국관광공사_국문 관광정보 서비스_GW` → [활용신청](https://www.data.go.kr/data/15101578/openapi.do) (자동승인)
- **Base(권장 v2, HTTPS)**: `https://apis.data.go.kr/B551011/KorService2` (레거시 `KorService1`은 단계적 폐지)
- **오퍼레이션**: `areaBasedList2`(지역기반 목록 — 핵심), `locationBasedList2`(위경도+반경), `searchKeyword2`, `detailCommon2`(주소·좌표·개요), `detailIntro2`(운영시간·휴무일), `detailImage2`, `areaCode2`(지역코드 조회)
- **파라미터**: `serviceKey`, `MobileOS`(ETC), `MobileApp`(offway), `_type=json`, `numOfRows`, `pageNo`, `arrange`, `areaCode`, `sigunguCode`, `contentTypeId`, `cat1/2/3`
- **contentTypeId**: 12=관광지 · 14=문화시설 · 15=축제공연행사 · 25=여행코스 · 28=레포츠 · 32=숙박 · 38=쇼핑 · 39=음식점
- **응답 필드**: `contentid`, `title`, `addr1`, `mapx`(경도)/`mapy`(위도), `areacode`, `sigungucode`, `firstimage`, `tel`; detailIntro는 `usetime`/`restdate`(운영·휴무, 자유텍스트)
- **한도**: 무료, 개발계정 일 1,000건 (운영 전환 시 증량)
- **⚠️ 최대 함정 — 지역코드**: `sigunguCode`는 KTO 전용 순번(법정 시군구 코드 아님)이고 `areaCode`와 **함께만** 유효. → **먼저 `areaCode2`로 (areaCode,sigunguCode)↔지명 매핑 테이블을 확보**한 뒤 인구감소지역 89곳 지명과 매칭. 지명 기준 조인.
- **OffWay 용도**: 인구감소지역 관광지·숙박·음식점 표출, 운영시간/휴무 기반 일정 생성.

### 1-3. 관광빅데이터(방문자수) — 한국관광공사 · **실호출 확인(#20)**
- **발급**: data.go.kr 데이터셋 **15101972** `한국관광공사_관광빅데이터 정보서비스_GW` → [활용신청](https://www.data.go.kr/data/15101972/openapi.do) (**15101578과 별개 신청**)
- **Base**: `https://apis.data.go.kr/B551011/DataLabService` ✅ 확인
- **오퍼레이션**: `metcoRegnVisitrDDList`(광역별 일별 방문자수) · `locgoRegnVisitrDDList`(**기초 시군구별 — OffWay 89에 사용**). 둘 다 실호출 성공(resultCode `0000`).
- **파라미터**: `serviceKey`·`MobileOS`(ETC)·`MobileApp`(offway)·`_type=json`·`startYmd`/`endYmd`(YYYYMMDD)·`pageNo`·`numOfRows`. (지역 필터 param 없이 기간 전체 반환 → 클라가 시군구로 매칭)
- **응답 필드(locgo)**: `signguCode`(11110)·`signguNm`(종로구)·`daywkDivCd`/`daywkDivNm`(요일)·`touDivCd`/`touDivNm`(**1=현지인·2=외지인·3=외국인**)·`touNum`(방문자수, 문자열)·`baseYmd`
- **한도**: 무료, 개발계정 일 1,000건
- **함정**: `signguCode`는 **법정 시군구코드**(행정표준코드)라 TourAPI KTO 코드와 다름 → **`signguNm`(지명)으로 region 매칭**(고성군 중복만 코드로 후속 처리). "방문자≠관광객" → 랭킹엔 **외지인+외국인**만.
- **OffWay 용도**: "평일에 한산한 시점" 한산도 뱃지·지역 랭킹 — 차별화 핵심 데이터.
- **미확인(후속)**: 관광지별 **집중률**·향후 방문자 **예측**은 별도 오퍼레이션(#21+ 필요 시 확인).

### 1-4. TAGO 대중교통 — 국토교통부 (여러 서비스, 동일 계정)
- **Base 공통**: `http://apis.data.go.kr/1613000/...` · 응답 래퍼 `resultCode`/`items>item[]`
- **한도**: 무료, 개발계정 일 10,000건

> **경로 명명 규칙(실측)**: TAGO 서비스명은 전부 `...InqireService` 로 끝난다(오타 아님 — 정부 API 원문 표기). 틀린 이름은 게이트웨이가 `404 "API not found"`, 맞는 이름은 `500 "Unexpected errors"`(전파 전) 또는 `200`. 아래는 그 규칙으로 확정한 값.

| 서비스 | 데이터셋 | 경로 | 핵심 op / 필드 | 비고 |
|---|---|---|---|---|
| 버스도착정보(시내) | 15098530 | `ArvlInfoInqireService` ✅ | `getSttnAcctoArvlPrearngeInfoList` → `arrtime`(초 단위 잔여) | `nodeId` 선조회 필요, 실시간 |
| 버스정류소정보 | 15098534 | `BusSttnInfoInqireService` ✅ | `getCtyCodeList` 등 | 200 확인 |
| 버스노선정보 | 15098529 | `BusRouteInfoInqireService` ✅ | 노선·경유정류소 | 200 확인 |
| 버스위치정보 | 15098531 | `BusLcInfoInqireService` ✅ | 실시간 차량 위치 | 200 확인 |
| 고속버스정보 | 15098522 | `ExpBusInfoInqireService` ✅ | 출발/도착시간 · 요금 | 터미널ID 선조회 (경로 확정: 500=경로존재) |
| 시외버스정보 | 15098541 | `SuburbsBusInfoInqireService`(추정) | 출발/도착/**소요시간**·요금 | ⚠️ **당일 배차만** (미래날짜 불가) |
| 열차정보 | 15098552 | `TrainInfoInqireService` ✅ | `getStrtpntAlocFndTrainInfo`(출발/도착역+날짜) | **KTX 포함, SRT 미포함** (경로 확정) |

- **경로 확정 근거**: 승인된 버스 4종은 `200`. 열차·고속버스는 올바른 `...InqireService` 이름에서 `500`(경로 존재·전파 진행 중), 틀린 이름(`TrainInfoService`·`ExpBusInfo`)에선 `404`. 즉 **이름은 확정, 남은 500 은 data.go.kr 응답 준비(전파) 대기** — 완료되면 자동 200.
- **역/정류소/터미널 ID**는 각 목록 오퍼레이션(`getCtyCodeList`, 역목록 등)으로 선조회.
- **OffWay 용도**: 반차·퇴근후 모드 도착시각, 교통수단별 동선.

### 1-5. 코레일 열차운행정보 — 한국철도공사
- **발급**: data.go.kr **15125762** `한국철도공사_열차운행정보` → [활용신청](https://www.data.go.kr/data/15125762/openapi.do)
- **제공**: 여객열차 운행계획/운행정보(실시간·지연). 출발역/도착역/운행일자 기반.
- **함정**: **요금·예매·좌석 미제공**(운행 트래킹용). 시간표는 1-4 TAGO 열차정보가 더 적합.
- **OffWay 용도**: KTX 지연/실제운행 보조 (선택).

### 1-6. TMAP 경로·경유지 최적화 — SK(티맵모빌리티) *별도 포털*
- **발급**: https://openapi.sk.com 회원가입 → 앱 등록 → TMAP 상품 구독 → **`appKey`(UUID)** 발급
- **인증**: 요청 헤더 `appKey: <키>`
- **Base**: `https://apis.openapi.sk.com/tmap/...`
  - 자동차 경로: `POST /tmap/routes?version=1`
  - 경유지 최적화(10곳): `POST /tmap/routes/routeOptimization10` (20/30곳은 엔드포인트 별도)
- **파라미터**: `startX/startY`, `endX/endY`(경도X/위도Y, `WGS84GEO`), `searchOption`; 최적화는 `viaPoints[]`(`viaX/viaY/viaTime`)
- **응답**: GeoJSON, `totalTime`(초)·`totalDistance`(m)·`totalFare`
- **한도(무료)**: 경로 **1,000/일**, 경유지 최적화(10곳) **50/일** (초과 시 유료)
- **⚠️ 함정**: **결과 데이터 24시간 이상 저장 금지**(약관) → 경로 결과 영구 캐싱 불가. 좌표 X=경도/Y=위도 순서.
- **OffWay 용도**: 자가용 기준 실시간 소요시간, 관광지 경유지 순서 최적화(일정 자동 생성).

---

## 2. 정적 데이터셋 (런타임 호출 아님 → **DB 시딩/임포트**)

| # | 데이터 | 출처 | 형식 | 방식 |
|---|---|---|---|---|
| 2-1 | **인구감소지역 89곳** | 행안부 고시 (mois.go.kr / nabis.go.kr) | 고시문/표 | **상수 시드**(거의 불변, "고시 기준일" 기록) |
| 2-2 | **생활인구(체류)** | 행안부 data.go.kr **15130539** (파일데이터) | **XLSX(분기)** | 분기마다 다운로드→임포트 |
| 2-3 | **로컬100** | 문체부 mcst.go.kr / culture.go.kr | PDF/리스트 | 시드 + 좌표는 지오코딩 보강 |
| 2-4 | **관광두레** | tourdure.mcst.go.kr | 웹/발간물 | 시드 + 좌표는 TourAPI/지오코딩 |
| 2-5 | **7대 여행 지원 혜택** | 전용 API 없음(제안서 명시) | 수동 | **수동 적재**(정책명·기간·대상지·할인) |

- **생활인구 주의**: 정책적 "생활인구(체류인구)"는 행안부 XLSX가 정본. SGIS는 센서스 인구라 개념이 다름. 서울시 생활인구 API는 2026-06 현행화 중지.

---

## 3. 데이터 조합 → 제공 가능한 기능 (전부 확보 시)

| 기능 | 필요한 데이터 조합 |
|---|---|
| LNT 가용시간·샌드위치 연휴 | 특일정보 |
| 인구감소지역 여행지 추천 | 인구감소지역89(seed) + TourAPI + 관광빅데이터(집중률→한산시점) + 생활인구(가중치) |
| 정책 혜택 매칭·뱃지 | 7대혜택(seed) + 인구감소지역 + 로컬100/관광두레(seed) |
| 교통·도착시각 (반차/퇴근후) | TAGO 버스/열차 + 코레일 + TMAP(자가용) |
| 일정 자동 생성 | TourAPI(운영시간/휴무) + TMAP(경유지 최적화) + 위 조합 |
| 연차 컨설팅 | 특일정보 (+ 위 전체) |

---

## 4. "모든 걸 미리 준비" 실행 제약 (설계에 반영)

- **SRT 공공API 없음** → SRT 시간표는 제외하거나 별도 처리. KTX는 TAGO로.
- **시외버스 미래날짜 조회 불가**(당일만) → 미래 일정은 "예상 소요시간"으로 근사, 실시간은 당일에.
- **TMAP 24h 저장 제한** → 경로 결과는 단기 캐시(≤24h)만. 영구 저장 금지.
- **개발계정 한도**(TourAPI/빅데이터 1,000/일, TMAP 최적화 50/일) → 대량 사전적재는 배치·페이징·운영계정 전환 고려.
- **지역코드 불일치**(TourAPI sigunguCode ↔ 빅데이터 코드 ↔ 인구감소지역 지명) → **지명 기준 매핑 테이블**을 먼저 구축(마스터 데이터).
- **Encoding/Decoding 서비스키** 두 개 다 보관.

---

## 5. 발급 체크리스트 (본인 액션)

- [ ] **data.go.kr 회원가입** (휴대폰 본인인증)
- [ ] 활용신청: 특일정보(15012690) · 국문관광정보(15101578) · 관광빅데이터(15101972) · TAGO 버스도착(15098530)·고속(15098522)·시외(15098541)·열차(15098552) · 코레일(15125762) · 생활인구 파일(15130539)
- [ ] 발급된 **serviceKey(Encoding+Decoding)** 확보
- [ ] **openapi.sk.com 회원가입** → 앱 등록 → TMAP **appKey** 발급
- [ ] (선택) SGIS 개발지원센터 key/secret
- [ ] 정적 데이터 다운로드: 인구감소지역 89곳 · 생활인구 XLSX · 로컬100 리스트 · 관광두레 목록
- [ ] 7대 혜택 정보 수동 정리(정책명·운영기간·대상지·할인율)

---

## 6. 볼거리 보강 후보 (k-skill 참고 · 미연동)

> 출처: `NomaDamas/k-skill` 조사(2026-07-21). TourAPI만으론 볼거리가 부족한 소도시(영양 10개급) 보강용 **추가** 소스. 아래는 후보이며 아직 클라이언트 미구현. **이미 우리가 쓰는 소스(특일정보·TourAPI·관광빅데이터·TAGO·코레일·TMAP)는 그대로 유지**한다. 관련 이슈 #44.

### 6-1. 국가유산 정보 (문화재) — 국가유산청 ⭐ 키 불필요, 즉시 사용 가능
- **인증**: 없음 (API key·로그인·프록시 불필요). GET · **XML 응답** · `User-Agent` 헤더 필요 · timeout ~20s
- **목록**: `https://www.khs.go.kr/cha/SearchKindOpenapiList.do`
  - params: `ccbaMnm1`(유산명 검색어) · `ccbaCtcd`(시도코드 2자리) · `pageUnit`(1~100) · `pageIndex` · `ccbaCncl=N`(지정해제 제외)
  - item 필드: `ccbaMnm1`(명) · `ccbaMnm2`(한자) · `ccbaCtcdNm`(시도명) · `ccbaAdmin`(관리기관) · `latitude` · `longitude` · `ccbaKdcd`(종목코드) · `ccbaAsno`(관리번호) · `ccbaCtcd`(시도코드)
- **상세**: `https://www.khs.go.kr/cha/SearchKindOpenapiDt.do` — params `ccbaKdcd`+`ccbaAsno`+`ccbaCtcd` → 설명(`content`)·주소·좌표·이미지
- **행사**: `https://www.khs.go.kr/cha/openapi/selectEventListOpenapi.do` — params 연도(YYYY)·월(1~12) → 행사명·기간·지역·본문(`subContent`)·링크(`subPath`)
- **⭐ 좌표 제공** → 지명·좌표로 우리 region/POI에 매핑. TourAPI `contentTypeId=14`(문화시설) 계열 볼거리 보강.
- **함정**: 공식 페이지 명시 한도 확인 필요. 지명↔시도코드 매핑 테이블 선구축(우리 89 지명 기준).
- **OffWay 용도**: 콘텐츠 충분성(#21)에서 인접 50km 확장 전 자체 볼거리 확대 · 지역 상세에 문화재·이달의 행사.

### 6-2. 날씨 (기상청 단기예보) — 후보, 우리 키로 직접 호출
- **upstream**: 공공데이터포털 기상청 단기예보 조회서비스(`VilageFcstInfoService`). k-skill은 자기 프록시를 쓰나 우리는 **기존 `DATA_GO_KR` 키로 직접** 호출.
- **입력**: 격자 `nx`/`ny` (위경도만 있으면 격자 변환 필요) · 선택 `baseDate`/`baseTime`(생략 시 최신 발표시각).
- **용도**: 지역 카드/코스에 여행일 날씨(우천 시 실내 대안). Nice-to-have.

### 6-3. 미세먼지 (에어코리아) — 후보, 우리 키로 직접 호출
- **upstream**: 에어코리아(한국환경공단) 대기오염정보. 측정소명(행정구역) 기반 조회.
- **용도**: 지역 카드에 대기질 뱃지. Nice-to-have.

### 6-4. (검토) 공연/전시·자연휴양림 — 추가 키/스크래핑 필요
- **KOPIS** 공연예술통합전산망: 공연·전시(체험거리). **별도 KOPIS 키 발급 필요** → 우선순위 낮음.
- **숲나들e**(자연휴양림, foresttrip.go.kr): 7대 혜택 농촌체험/치유관광과 매핑되나 **공식 API 없이 스크래핑** → 백엔드 안정성 낮아 보류.

### 참고: KTX/SRT 예매 — 채택 안 함
- k-skill의 ktx/srt는 비공식 라이브러리(`korail2-ncard`/`SRTrain`) + **개인 로그인 계정** + anti-bot 토큰 기반이라 공개 서비스 백엔드에 부적합. 열차는 §1-4 TAGO(KTX 포함)·§1-5 코레일 공식 API를 유지한다. (§4의 "SRT 공공API 없음" 재확인.)
