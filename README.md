# OffWay `core`

> **연차로 떠나는 로컬 여행 플래너** — "며칠 쉴 수 있나"라는 질문 하나로, 직장인의 연차를 최대로 살려 일정·인구감소지역 로컬 여행지·정부 지원 정책이 결합된 국내 여행 계획을 자동 생성한다. (2026 관광데이터 활용 공모전 / 팀 엔차)

OffWay 백엔드(Spring Boot 4.1 · Java 25). 공휴일·관광·교통·정책 공공데이터를 조합해 "연차 자원 최적화 + 로컬 여행 추천 + 정책 큐레이션"을 하나의 플로우로 제공한다.

## 빠른 시작 (로컬)

로컬은 **H2 인메모리**로 뜨며, **외부 API 키·시크릿 없이도 부팅**된다(외부 실제 호출만 비활성). 자세한 규약은 [로컬 실행성](CLAUDE.md#로컬-실행성-불변식) 참고.

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

외부 API를 실제로 호출하려면 키를 넣는다(선택):

```bash
cp application-secret.properties.example application-secret.properties
# application-secret.properties 에 발급받은 serviceKey / appKey 를 채운다 (git 제외됨)
```

## 아키텍처

package-by-feature. 도메인별로 `com.offway.core.<domain>` 아래 `controller/service/domain/repository/dto/exception` 를 둔다. 외부 API는 `external` 패키지에 port 인터페이스로 격리한다.

```
leave  trip  policy  transport  itinerary  external  user  common
```

전체 구조·설계 원칙은 [CLAUDE.md](CLAUDE.md) 참고.

## 문서

| 문서 | 내용 |
|---|---|
| [CLAUDE.md](CLAUDE.md) | 프로젝트 개발 규약 (아키텍처·객체지향/상수화 스타일·로컬 실행성·용어집) |
| [.claude/rules/](.claude/rules/) | 주제별 세부 규약 (예외/응답·영속성·API·테스트) |
| [docs/external-api-inventory.md](docs/external-api-inventory.md) | 외부 공공데이터 인벤토리·발급 체크리스트 (특일정보·TourAPI·TAGO·TMAP 등) |

## 기술 스택

Spring Boot 4.1 · Java 25 · Lombok · JPA · Flyway · H2(local)/MySQL(prod) · Redis · Spring Security + OAuth2 · WebFlux(WebClient)

> 버전의 단일 진실 원천은 [`build.gradle.kts`](build.gradle.kts).
