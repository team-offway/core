# 🧩 코스 생성 로직 (Course Generation)

> 파라미터가 들어오면 백엔드가 여행 코스를 **자동으로 정해서** 내려주는 전체 로직. 트리플식 자동 일정 생성을 OffWay(국내·연차·정부혜택)에 맞게 변형.

- API: `POST /api/v1/courses/generate` · 도메인: `itinerary`
- 관련: [feature-spec.md](feature-spec.md) F4 · [api-spec.md](api-spec.md) #5

---

## 0. 한 줄 요약

**입력 6개**(지역·일수·밀도·이동수단·무드·출발지) → **9단계 파이프라인** → **완성 코스**(날짜별 타임라인 + 혜택 + 비용).
`itinerary`가 조율만 하고, 실제 데이터는 `trip`·`transport`·`policy`가 제공한다.

---

## 1. 입력

```jsonc
{ "regionId": 42, "travelDays": 3, "density": "PACKED",
  "transport": "CAR", "mood": ["SIGHT","FOOD"], "originLat": 37.49, "originLng": 127.02 }
```

---

## 2. 파이프라인 (9단계)

| 단계 | 하는 일 | 데이터/도메인 |
|---|---|---|
| **① 후보 POI 수집** | 지역의 관광지/맛집/숙박 목록 조회 · 무드칩→lclsSystm 필터 → [볼거리풀·맛집풀·숙박풀] | `trip` → TourAPI(areaBasedList2) |
| **② 랭킹** | 방문자수·집중률로 점수 → 베이지안 보정(로컬 안 묻히게) + 인구감소 가점 → 정렬 | `trip` → 관광빅데이터 |
| **③ 평일 오픈 필터** | 휴무일 파싱 → 여행 요일에 여는 곳 우선/배제 (자연물=상시) | `trip` → TourAPI(detailIntro2) |
| **④ 필요 개수 산정** | 볼거리 = 일수 × 밀도(널널3/빡빡6) · 맛집 = 일수×2 · 숙박 = 박수 | (계산) |
| **⑤ 지리 클러스터링** | 볼거리 좌표 군집 → 하루 = 1~2 클러스터 ("한 지역 몰아서", 이동 낭비 차단) | (좌표 계산) |
| **⑥ 슬롯 배치** | 하루 = 오전/점심/오후/저녁 → 관광·식사 번갈아, 밀도만큼 | `itinerary` domain |
| **⑦ 동선 최적화** | 하루 내 POI 순서·이동시간 확정 (자차=TMAP 경유지최적화 / 대중교통=TAGO) | `transport` → TMAP·TAGO |
| **⑧ 혜택·비용** | 지역 태그로 정책 매칭 → 혜택 뱃지 + 비용 재계산 | `policy` |
| **⑨ 조립** | days[{day, items[…]}] + benefits + estimatedCost 로 응답 | `itinerary` |

---

## 3. 의사코드 (핵심 흐름)

```text
generateCourse(input):
  # ① 수집 (trip 도메인)
  pois   = tripService.findPois(regionId, mood)        # 볼거리/맛집/숙박
  if pois.sights < 필요볼거리(input): 
      pois += tripService.findNearby(regionId, 50km)   # 콘텐츠 부족 → 인접 확장

  # ② 랭킹 + ③ 평일오픈 (trip 도메인)
  sights = rank(pois.sights) filtered by openOnWeekday(travelDate)

  # ④ 필요 개수
  perDay = density == PACKED ? 5~6 : 2~3
  need   = travelDays * perDay

  # ⑤ 클러스터링
  clusters = geoCluster(sights[0:need])                # 하루당 1~2 군집

  days = []
  for d in 1..travelDays:
     dayPois = clusters[d].pick(perDay)                # ⑥ 슬롯 배치
     slots   = arrange(dayPois, meals=pickFood(pois))  #   오전/점심/오후/저녁
     ordered = transportService.optimize(slots, transport)  # ⑦ TMAP 순서·이동시간
     days.add(DaySchedule(d, ordered))

  # ⑧ 혜택·비용 (policy 도메인)
  benefits = policyService.match(region, travelDate, transport, user)
  cost     = estimate(days) - discount(benefits)

  return Course(region, days, benefits, cost)          # ⑨ 조립
```

---

## 4. DDD 관점 — 누가 무엇을 하나

```
itinerary/service/CourseGenerationService   ← 조율(유스케이스). 순서만 지휘
itinerary/domain/                           ← 조립 규칙(rich): Course · DaySchedule · Slot
    ├─ trip.service       :  POI + 랭킹(방문자수) + 평일오픈
    ├─ transport.service  :  TMAP 경유지 최적화(동선·이동시간)
    └─ policy.service     :  혜택 매칭 · 비용
```
- **itinerary는 TourAPI/TMAP을 직접 호출하지 않는다.** 소유 도메인의 service를 통해서만.
- 슬롯 배치·클러스터 판정 같은 **규칙은 domain(rich)**, 외부 호출 조율은 **service(application)**.

---

## 5. 트리플 대비 (무엇을 차용/변형)

| | 트리플 | OffWay |
|---|---|---|
| 동선 | 순서 재배열 유도(반자동) | 클러스터+슬롯+TMAP **완전 자동** |
| 랭킹 신호 | scrapsCount(저장수)·평점 | **방문자수·집중률** + 인구감소 가점 (콜드스타트 극복) |
| 상한 | 3~5일 해외 | **최대 2박3일** 국내 |
| 고유 | — | **평일오픈 · 정부혜택 · 도달시간** (트리플에 없음) |

---

## 6. 파라미터별 영향 (요약)

| 파라미터 | 영향 단계 |
|---|---|
| `regionId` | ① 후보 수집 대상 |
| `travelDays` | ④ 필요 개수 · ⑤ 클러스터 수 |
| `density` | ④ 하루 개수(널널3/빡빡6) |
| `transport` | ⑦ 동선(TMAP/TAGO) · 도달시간 |
| `mood` | ① lclsSystm 필터 |
| `originLat/Lng` | 지역 도달시간(후보 단계 전제) |

> 🔄 이 문서는 구현하면서 계속 갱신한다 (알고리즘 파라미터·임계값 확정 시).
