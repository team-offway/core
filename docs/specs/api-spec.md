# 🧭 OffWay API 명세 (v0.1 · MVP)

> 📌 **버전** v0.1 (초안) · **Base URL** `/api/v1` · **인증** 게스트(로그인 후순위) · **연동** 백엔드 ↔ 프론트
>
> 🔄 **Living doc** — 개발하면서 계속 업데이트한다. 스키마·필드·엔드포인트는 구현 중 확정되며, 변경 시 이 문서를 함께 갱신한다.

---

## 📚 엔드포인트 한눈에

| # | Method | Path | 화면 | 기능 |
| --- | --- | --- | --- | --- |
| 1 | `POST` | `/leaves/available-time` | 연차 입력 | 가용시간(LNT) 산출 |
| 2 | `GET` | `/leaves/sandwich` | (샌드위치) | 황금 연차 추천 |
| 3 | `GET` | `/home` | 홈 | 남은연차·추천지역·혜택 |
| 4 | `POST` | `/regions/recommend` | 후보 지역 | 도달 가능 지역 추천 |
| 5 | `POST` | `/courses/generate` | 코스 확정 | **코스 타임라인 생성** |
| 6 | `GET` | `/policies/{id}` | 정책 상세 | 혜택 + 여행지 매핑 |
| 7 | `GET` | `/categories` | 필터칩 | 카테고리 목록 |
| 8 | `GET` | `/pois/{id}` | 장소 상세 | 장소 정보 |
| 9 | `POST/GET` | `/courses` | 내 코스 | 저장·조회 |
| 10 | `GET` | `/regions` | 지역 목록(더보기) | 89곳 페이지 조회·카테고리 필터 |

---

## 🔧 공통 규칙

> 💡 **모든 응답은 아래 래퍼로 감싼다.**

```json
{ "status": 200, "data": { }, "detail": "정상적으로 처리되었습니다.", "code": "OK" }
```

- 실패 시 `status`=4xx/5xx · `data`=null · `code`=도메인 코드(예: `LEAVE-001`)
- 좌표: `lat`(위도) · `lng`(경도) · 시간: 분 단위 정수 · 날짜: `YYYY-MM-DD`

> ⚠️ **로그인 후순위** → 지금은 게스트. 유저 종속 값(남은 연차·내 코스)은 클라이언트가 들고 다니거나 게스트 토큰으로 임시 저장.

### 🏷️ 공통 enum

| enum | 값 |
| --- | --- |
| `periodStyle` | `DAY_TRIP` 당일치기 · `WEEKEND` 주말포함 · `CONNECTED` 연차이어서 |
| `transport` | `CAR` 자차 · `TRANSIT` 대중교통 |
| `density` | `RELAXED` 널널(2~3) · `PACKED` 빡빡(5~6) |
| `category` | `ALL` · `SIGHT` 관광지 · `STAY` 숙박 · `EXPERIENCE` 체험 · `FOOD` 맛집 |
| `crowdLevel` | `LOW` 평일여유 · `MID` 보통 · `HIGH` 붐빔 |
| `policyType` | `DIGITAL_TOURIST_CARD` · `REGIONAL_VOUCHER` · `STAY_FESTA` · `WORKER_VACATION` · `RAIL_DISCOUNT` · `RURAL` |

---

### 1️⃣ 가용시간(LNT) 산출 · `POST /api/v1/leaves/available-time`

> 🎯 확정된 날짜 구간 → 여행일수·소모 연차·이동한계 · **기능 F1** · **데이터** 특일정보 · **구현** #17
>
> 📌 결정 #38 반영: "가용시간(시간 수) 72h" 큰 숫자와 `window`(18:00 등)는 폐기. 소모 연차는 **입력이 아니라 날짜에서 계산**(평일−공휴일). `periodStyle`(당일치기/주말포함/연차이어서)→실제 날짜 해석은 상위 2층(#46)이 맡고, 이 엔드포인트는 확정 날짜만 받는다.

**요청**

```json
{ "startDate": "2026-05-06", "endDate": "2026-05-08",
  "transport": "CAR", "halfDayStart": false }
```

- `startDate`·`endDate`·`transport` 필수. `halfDayStart` 선택(기본 false, 출발일 반차).
- 종료일이 시작일보다 앞서거나 구간이 2박 3일(`MAX_TRIP_DAYS`)을 넘으면 400(`LEAVE-001`·`LEAVE-002`).

**응답 `data`**

```json
{
  "travelDays": 3,
  "consumedLeaveDays": 3.0,
  "maxReachMinutes": 420
}
```

- `travelDays` 1=당일치기·2=1박2일·3=2박3일 · `consumedLeaveDays` 평일−공휴일(반차 0.5) · `maxReachMinutes` 편도 도달 한계(분, 대중교통은 ×0.7).

---

### 2️⃣ 샌드위치 연휴 추천 · `GET /api/v1/leaves/sandwich`

> 🎯 향후 N개월 황금 연차일 추천 · **기능 F2** · **데이터** 특일정보 · **구현** #18

**쿼리** `?fromDate=2026-05-01&months=6&remainingLeave=8`

- `fromDate` 필수. `months` 1~12(기본 6, 벗어나면 400 `LEAVE-003`). `remainingLeave` 선택 — 있으면 그 이하 연차로 가능한 연휴만.
- 효율(연차 1일당 휴식) 높은 순 정렬. 없으면 `items` 빈 배열(200). 공휴일 조회 실패 시 502.

**응답 `data`**

```json
{ "items": [
  { "leaveDates": ["2026-05-04"], "totalRestDays": 5, "efficiency": "1일=5일",
    "window": { "start": "2026-05-01", "end": "2026-05-05" } }
] }
```

> 예시: 5/1(금·노동절) + 5/2~3(주말) + **5/4(연차)** + 5/5(화·어린이날) = 5일 휴식. (연차일은 창 안의 평일 하나 = 5/4)

---

### 3️⃣ 홈 · `GET /api/v1/home`

> 🎯 남은 연차 + 이번주 추천 지역(혜택·한산도) + 필터칩 · **기능 F3·F6** · **데이터** region89 · 관광빅데이터 · policy · **구현** #22(MVP)

**쿼리** `?remainingLeave=13`

- 추천 지역은 **랭킹 top-N**(덜 붐비는 로컬 우선). `remainingLeave` 는 게스트 보유값을 그대로 담아준다(없으면 `null`).
- `imageUrl`·`summary`·`categories`·무드(`category`) 필터는 후속(#61). 관광빅데이터 실패 시 502.

**응답 `data`**

```json
{
  "user": { "name": "게스트", "remainingLeaveDays": 13 },
  "filters": [
    { "key": "ALL", "label": "전체", "regionCount": 89 }, { "key": "SIGHT", "label": "관광지", "regionCount": 61 },
    { "key": "STAY", "label": "숙박", "regionCount": 34 }, { "key": "EXPERIENCE", "label": "체험", "regionCount": 12 },
    { "key": "FOOD", "label": "맛집", "regionCount": 47 }
  ],
  "recommendedRegions": [
    {
      "regionId": 42, "name": "완도군 · 전라남도",
      "crowdLevel": "LOW",
      "benefit": { "text": "여행경비 50% 환급", "policyType": "REGIONAL_VOUCHER", "policyId": 1 }
    }
  ]
}
```

> 💡 `benefit`=null이면 뱃지 없음. `imageUrl`·`summary`·`categories` 는 #61에서 추가.

---

### 4️⃣ 후보 지역 추천 · `POST /api/v1/regions/recommendations`

> 🎯 도달 가능 후보 지역 추천 · **기능 F3** · **데이터** region89 · 관광빅데이터 · policy · **구현** #23(MVP)

- **요청은 `maxReachMinutes`** 를 받는다 — `/leaves/available-time` 응답의 값을 그대로 넘긴다(availableHours/travelDays 는 결정 #38로 폐기).
- 좌표 범위 초과·이동수단 누락·도달 한계가 양수 아님 → 400. 관광빅데이터 실패 → 502.

**요청**

```json
{ "originLat": 37.49, "originLng": 127.02, "transport": "CAR", "maxReachMinutes": 420 }
```

**응답 `data`** (랭킹 내림차순)

```json
{ "regions": [
  {
    "regionId": 42, "name": "완도군 · 전라남도",
    "reachMinutes": 160,
    "crowdLevel": "LOW",
    "benefits": [ { "policyId": 1, "text": "여행경비 50% 환급" } ]
  }
] }
```

> 📊 **로직(MVP)** 도달시간 ≤ maxReach 필터 → 방문자 랭킹(덜 붐비는 로컬 우선, 베이지안 보정) → 한산도·혜택 뱃지
>
> 🔜 **후속(#61)** `imageUrl`·`summary`·`contentCount`·`categories`(TourAPI 지역별 콘텐츠) + `mood` 무드 필터 + 콘텐츠 충분성 50km 확장

---

### 5️⃣ 코스 생성 · `POST /courses/generate`

> 🎯 선택 지역 + 조건 → 날짜별 타임라인 코스 · **기능 F4·F5** · **데이터** TourAPI(위치기반·소개정보) · TMAP(경유지) · TAGO · policy

**요청**

```json
{ "regionId": 42, "travelDays": 3, "density": "PACKED",
  "transport": "CAR", "mood": ["SIGHT", "FOOD"],
  "originLat": 37.49, "originLng": 127.02 }
```

**응답 `data`**

```json
{
  "region": { "regionId": 42, "name": "완도 · 전남" },
  "travelDays": 3,
  "days": [
    { "day": 1, "items": [
      {
        "order": 1, "poiId": "2615324", "name": "가사동백숲해변",
        "type": "SIGHT", "category": "SIGHT",
        "imageUrl": "https://cdn.offway.app/.../xxx.jpg",
        "lat": 34.3698, "lng": 126.9277,
        "stayMinutes": 90,
        "openStatus": "OPEN_WEEKDAY",
        "moveToNext": { "minutes": 12, "transport": "CAR" }
      }
    ] }
  ],
  "benefits": [ { "text": "여행경비 50% 환급", "policyId": 3 } ],
  "estimatedCost": { "original": 120000, "discounted": 60000 }
}
```

> 📊 **로직** 지역 볼거리 → 밀도만큼 하루 배치 → 평일오픈 검증 → TMAP 순서·이동시간 → 슬롯(오전/점심/오후/저녁)
> 🔖 `openStatus`: `OPEN_WEEKDAY` / `CLOSED` / `ALWAYS`

---

### 6️⃣ 정책 상세 · `GET /api/v1/policies/{policyId}`

> 🎯 정책 정보 + **이 혜택 되는 여행지**(정책→지역 역방향) · **기능 F5** · **데이터** policy · region_tag · TourAPI · **구현** #13

- **미검증(verified=false)·없는 정책은 404**(`POLICY-001`) — 노출 대상만 조회.
- `regions` = 정책 `PolicyType.targetTag` 가 붙은 지역(MVP 는 `POPULATION_DECLINE` → 89). 참여지역 리스트 확보 시 좁혀짐.
- `imageUrl` 은 TourAPI 연동 전이라 현재 `null`. `period` 는 시작·종료가 모두 없으면 `null`(상시).

**응답 `data`**

```json
{
  "id": 1, "type": "REGIONAL_VOUCHER",
  "name": "지역사랑 휴가지원(반값여행)",
  "badgeText": "여행경비 50% 환급",
  "benefitDetail": "여행경비의 50%를 지역화폐로 환급 · 1인 최대 10만원(청년 70%)",
  "period": { "start": "2026-04-01", "end": "2026-08-31" },
  "target": "전 국민(거주지와 다른 지역 여행 시)",
  "applyUrl": null,
  "regions": [
    { "regionId": 42, "name": "완도군 · 전라남도", "imageUrl": null }
  ]
}
```

---

### 7️⃣ 카테고리(필터칩) · `GET /categories`

> 🎯 필터칩 목록 · **기능 F6** · 서버 내부에서 `SIGHT`→lclsSystm(NA+HS+VE+LS+EV) 등 매핑

- `regionCount` = **그 칩으로 좁혔을 때 나오는 지역 수**(#266). `GET /regions?category={key}` 의 `pageResponse.totalElements` 와 같은 값이고, `ALL` 은 전체 지역 수다. 화면이 개수를 지어내거나("전부 1건") 빈 칩을 그리지 않게 하려는 것.

**응답 `data`**

```json
{ "categories": [
  { "key": "ALL", "label": "전체", "regionCount": 89 }, { "key": "SIGHT", "label": "관광지", "regionCount": 61 },
  { "key": "STAY", "label": "숙박", "regionCount": 34 }, { "key": "EXPERIENCE", "label": "체험", "regionCount": 12 },
  { "key": "FOOD", "label": "맛집", "regionCount": 47 }
] }
```

---

### 8️⃣ 장소(POI) 상세 · `GET /pois/{poiId}`

> 🎯 코스/카드에서 장소 탭 시 · **데이터** TourAPI(detailCommon·detailIntro) · CDN

**응답 `data`**

```json
{
  "poiId": "2615324", "name": "가사동백숲해변",
  "type": "SIGHT", "category": "SIGHT",
  "summary": "동백꽃과 삼림욕을 함께 즐기는 아담한 해변",
  "images": ["https://cdn.offway.app/.../xxx.jpg"],
  "lat": 34.3698, "lng": 126.9277,
  "address": "전남 완도군 약산면 해동리",
  "openInfo": { "usetime": "상시 개방", "restdate": "", "parking": "가능" },
  "tel": "061-550-6401", "homepage": "https://www.wando.go.kr"
}
```

---

### 9️⃣ 내 코스 저장/조회 · `/courses`

> 🎯 코스 저장·목록·상세 · 게스트 토큰(헤더) 기준

| Method | Path | 설명 | 응답 |
| --- | --- | --- | --- |
| `POST` | `/courses` | 코스 저장 | `{ "courseId": 101 }` |
| `GET` | `/courses` | 내 코스 목록 | 코스 요약 배열 |
| `GET` | `/courses/{id}` | 저장 코스 상세 | 5번 응답과 동일 구조 |

---

### 🔟 지역 목록(더보기) · `GET /api/v1/regions`

> 🎯 "이번달 추천 여행지 더보기" · **기능 F3·F6** · **데이터** region89 · 관광빅데이터 · 지역 콘텐츠 · 관광사진 · **구현** #266

**쿼리** `?category=SIGHT&page=0&size=20`

- 홈은 랭킹 상위 **6곳**만 준다. 이 엔드포인트가 89곳 전부를 페이지로 끊어 준다.
- 정렬은 **방문자 랭킹 내림차순 하나뿐**이라 `sort` 파라미터가 없다. 도달시간 순은 출발지 좌표가 있어야 정의되고, 그건 `POST /regions/recommendations` 가 소유한다.
- `page`(기본 0) · `size`(기본 20, 최대 100). **잘못된 값은 거절하지 않고 자른다** — 음수 page 는 0, 상한 초과 size 는 100.
- 페이지 메타는 `data` 가 아니라 **공통 래퍼의 `pageResponse`** 에 실린다.
- **외부 API 호출이 없다.** 재료가 전부 적재된 값이라 관광 API 한도가 소진돼도 목록은 나간다.

**응답**

```json
{
  "status": 200, "code": "OK", "detail": "요청이 정상 처리되었습니다.",
  "data": { "regions": [
    {
      "regionId": 51, "name": "정선군 · 강원특별자치도",
      "crowdLevel": "LOW",
      "imageUrl": "http://tong.visitkorea.or.kr/cms/resource/83/1234583_image2_1.jpg",
      "contentCount": 128,
      "categories": [ { "key": "SIGHT", "label": "관광지" } ],
      "neighborIncluded": false
    }
  ] },
  "pageResponse": { "page": 0, "size": 20, "totalElements": 89, "totalPages": 5 }
}
```

---

## 🖥️ 화면 ↔ API 매핑

| 화면 | 엔드포인트 | 기능 |
| --- | --- | --- |
| 연차 입력 | `POST /leave/available-time` | F1 |
| 홈 | `GET /home` · `GET /categories` | F3·F6 |
| 홈 → 추천 여행지 더보기 | `GET /regions` · `GET /categories` | F3·F6 |
| 샌드위치 | `GET /leave/sandwich` | F2 |
| 추천 플로우 → 후보지역 | `POST /regions/recommend` | F3 |
| 코스 확정 | `POST /courses/generate` | F4·F5 |
| 정책 상세 | `GET /policies/{id}` | F5 |
| 장소 상세 | `GET /pois/{id}` | — |
| 내 코스 | `POST/GET /courses` | — |

---

## 🚧 미정 (논의 필요)

> ⚠️ 아래 3개는 확정 후 스키마 고정

- **출발지 입력 위치** — 연차 입력 화면에서 날짜와 함께(권장)
- **가용시간 전달** — 클라가 `available-time` 결과를 이후 요청에 그대로 전달(무상태)
- **추천 캐싱** — 홈·지역 랭킹은 하루 1배치 캐시
