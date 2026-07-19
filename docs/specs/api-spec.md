# 🧭 OffWay API 명세 (v0.1 · MVP)

> 📌 **버전** v0.1 (초안) · **Base URL** `/api/v1` · **인증** 게스트(로그인 후순위) · **연동** 백엔드 ↔ 프론트
>
> 🔄 **Living doc** — 개발하면서 계속 업데이트한다. 스키마·필드·엔드포인트는 구현 중 확정되며, 변경 시 이 문서를 함께 갱신한다.

---

## 📚 엔드포인트 한눈에

| # | Method | Path | 화면 | 기능 |
| --- | --- | --- | --- | --- |
| 1 | `POST` | `/leave/available-time` | 연차 입력 | 가용시간(LNT) 산출 |
| 2 | `GET` | `/leave/sandwich` | (샌드위치) | 황금 연차 추천 |
| 3 | `GET` | `/home` | 홈 | 남은연차·추천지역·혜택 |
| 4 | `POST` | `/regions/recommend` | 후보 지역 | 도달 가능 지역 추천 |
| 5 | `POST` | `/courses/generate` | 코스 확정 | **코스 타임라인 생성** |
| 6 | `GET` | `/policies/{id}` | 정책 상세 | 혜택 + 여행지 매핑 |
| 7 | `GET` | `/categories` | 필터칩 | 카테고리 목록 |
| 8 | `GET` | `/pois/{id}` | 장소 상세 | 장소 정보 |
| 9 | `POST/GET` | `/courses` | 내 코스 | 저장·조회 |

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

### 1️⃣ 가용시간(LNT) 산출 · `POST /leave/available-time`

> 🎯 연차·날짜·기간스타일 → 실제 여행 가능 시간·일수·이동한계 · **기능 F1** · **데이터** 특일정보

**요청**

```json
{ "startDate": "2026-05-01", "endDate": "2026-05-03",
  "leaveDays": 1, "periodStyle": "CONNECTED", "transport": "CAR" }
```

**응답 `data`**

```json
{
  "availableHours": 72,
  "travelDays": 3,
  "maxReachMinutes": 180,
  "window": { "start": "2026-05-01T18:00", "end": "2026-05-03T23:00" }
}
```

---

### 2️⃣ 샌드위치 연휴 추천 · `GET /leave/sandwich`

> 🎯 향후 N개월 황금 연차일 추천 · **기능 F2** · **데이터** 특일정보

**쿼리** `?fromDate=2026-05-01&months=6&remainingLeave=8`

**응답 `data`**

```json
{ "items": [
  { "leaveDates": ["2026-05-02"], "totalRestDays": 5, "efficiency": "1일=5일",
    "window": { "start": "2026-05-01", "end": "2026-05-05" } }
] }
```

---

### 3️⃣ 홈 · `GET /home`

> 🎯 남은 연차 + 이번주 추천 지역(혜택·한산도) + 필터칩 · **기능 F3·F6** · **데이터** region89 · 관광빅데이터 · TourAPI · policy

**쿼리** `?category=ALL&originLat=37.49&originLng=127.02&remainingLeave=13`

**응답 `data`**

```json
{
  "user": { "name": "게스트", "remainingLeaveDays": 13 },
  "filters": [
    { "key": "ALL", "label": "전체" }, { "key": "SIGHT", "label": "관광지" },
    { "key": "STAY", "label": "숙박" }, { "key": "EXPERIENCE", "label": "체험" },
    { "key": "FOOD", "label": "맛집" }
  ],
  "recommendedRegions": [
    {
      "regionId": 42, "name": "완도 · 전남",
      "imageUrl": "https://cdn.offway.app/.../w_512/xxx.jpg",
      "summary": "동백숲과 바다가 감싸는 청정 섬",
      "crowdLevel": "LOW",
      "categories": ["SIGHT", "FOOD"],
      "benefit": { "text": "여행경비 50% 환급", "policyType": "REGIONAL_VOUCHER", "policyId": 3 }
    }
  ]
}
```

> 💡 `benefit`=null이면 뱃지 없음.

---

### 4️⃣ 후보 지역 추천 · `POST /regions/recommend`

> 🎯 추천 플로우 입력 → 도달 가능 후보 지역 · **기능 F3** · **데이터** region89 · TMAP · TourAPI · 관광빅데이터 · policy

**요청**

```json
{ "originLat": 37.49, "originLng": 127.02,
  "transport": "CAR", "availableHours": 72, "travelDays": 3,
  "mood": ["SIGHT", "FOOD"] }
```

**응답 `data`**

```json
{ "regions": [
  {
    "regionId": 42, "name": "완도 · 전남",
    "imageUrl": "https://cdn.offway.app/.../xxx.jpg",
    "summary": "청정 섬 여행",
    "reachMinutes": 160,
    "crowdLevel": "LOW",
    "contentCount": 38,
    "categories": ["SIGHT", "FOOD"],
    "benefits": [ { "text": "여행경비 50% 환급", "policyId": 3 } ]
  }
] }
```

> 📊 **로직** 도달시간 ≤ maxReach 필터 → 콘텐츠 충분성(부족 시 인접 50km) → 방문자수/집중률 랭킹 → 무드 필터

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

### 6️⃣ 정책 상세 · `GET /policies/{policyId}`

> 🎯 정책 정보 + **이 혜택 되는 여행지**(정책→지역 역방향) · **기능 F5** · **데이터** policy · region_tag · TourAPI

**응답 `data`**

```json
{
  "id": 3, "type": "REGIONAL_VOUCHER",
  "name": "지역사랑 휴가지원(반값여행)",
  "badgeText": "여행경비 50% 환급",
  "benefitDetail": "여행경비의 50%를 지역화폐로 환급 · 1인 최대 10만원(청년 70%)",
  "period": { "start": "2026-04-01", "end": "2026-08-31" },
  "target": "전 국민(거주지 비인접)",
  "applyUrl": "https://...",
  "regions": [
    { "regionId": 42, "name": "완도 · 전남", "imageUrl": "https://cdn.offway.app/.../xxx.jpg" }
  ]
}
```

---

### 7️⃣ 카테고리(필터칩) · `GET /categories`

> 🎯 필터칩 목록 · **기능 F6** · 서버 내부에서 `SIGHT`→lclsSystm(NA+HS+VE+LS+EV) 등 매핑

**응답 `data`**

```json
{ "categories": [
  { "key": "ALL", "label": "전체" }, { "key": "SIGHT", "label": "관광지" },
  { "key": "STAY", "label": "숙박" }, { "key": "EXPERIENCE", "label": "체험" },
  { "key": "FOOD", "label": "맛집" }
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

## 🖥️ 화면 ↔ API 매핑

| 화면 | 엔드포인트 | 기능 |
| --- | --- | --- |
| 연차 입력 | `POST /leave/available-time` | F1 |
| 홈 | `GET /home` · `GET /categories` | F3·F6 |
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
