plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.offway"
version = "0.0.1-SNAPSHOT"
description = "core"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// Testcontainers 버전은 Spring Boot BOM 이 관리하지 않아 자체 BOM 을 들여온다.
// 버전 단일 진실 원천은 이 파일이다(CLAUDE.md §의존성 관리).
dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:2.0.5")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    // TourAPI 텍스트의 HTML 태그·엔티티 정제(#174). 정규식으로 하면 속성에 '>' 가 든 태그에서 텍스트가
    // 잘리는데, 깨진 문구가 사용자에게 나가면 되돌리기 어렵다.
    implementation("org.jsoup:jsoup:1.23.1")
    // FCM 발송(#270). 서비스 계정 키가 없으면 초기화하지 않고 발송만 비활성으로 뜬다 — 키 없이도
    // 부팅되어야 한다는 로컬 실행성 불변식(CLAUDE.md) 때문이다.
    implementation("com.google.firebase:firebase-admin:9.9.0")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-webservices")
    implementation("org.flywaydb:flyway-mysql")
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("com.mysql:mysql-connector-j")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    // 테스트도 운영과 같은 MySQL 로 돈다 — H2 는 MODE=MySQL 이어도 문법·타입이 달라
    // 마이그레이션이 로컬에서 초록인 채 MySQL 에서 깨졌다(#175).
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-client-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webservices-test")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
    useJUnitPlatform()

    // 테스트 워커 최대 힙 — 기본값(512m)에 기대지 않고 명시한다.
    //
    // **기본값으로는 CI 가 OutOfMemoryError 로 간헐 실패했다**(#216). 테스트 단언이 아니라 Spring 컨텍스트
    // 로딩이 죽는 형태라, 재실행하면 통과해 원인을 찾기 어렵다.
    //
    // 이 스위트는 통합 테스트 35개 중 20개가 stub 구성이 달라 컨텍스트가 갈리고, Spring 은 그것들을
    // 캐시해 실행 내내 살려둔다(기본 상한 32개). 각 컨텍스트가 bean factory·Hibernate SessionFactory·
    // Hikari 풀을 따로 든다.
    //
    // 실측(2026-08-10, `-Xlog:gc+heap`)이 숫자를 정해 줬다.
    //
    //   512m  Old 피크 492MB (힙의 96%)  GC 417회  1m21s   ← CI 가 여기서 터졌다
    //   1g    Old 피크 559MB (55%)       GC  85회  1m02s
    //   2g    Old 피크 557MB (27%)       GC  74회  1m05s
    //
    // 살아남는 양은 약 557MB 로 고정이다. 512m 에서 492 로 보인 것은 GC 가 417번 돌며 억지로 눌러
    // 담은 결과이고, CI 에서는 그마저 실패했다. 2g 는 1g 대비 GC 열 번 남짓만 줄일 뿐 얻는 게 없다.
    //
    // 1g 는 live set 의 약 1.8배다. 상한을 과하게 잡으면 누수가 더 오래 자라다 죽어 피드백이 늦어지므로,
    // 여유는 두되 필요 이상으로 벌리지 않는다. 통합 테스트가 늘어 이 숫자가 부족해지면 다시 재고 올린다.
    maxHeapSize = "1g"

    // 테스트는 실제 외부 API 를 때리지 않는다.
    //
    // application.properties 가 `spring.config.import=optional:file:./application-secret.properties` 로
    // 실키를 읽고, Gradle 테스트 워커의 작업 디렉터리가 프로젝트 루트라 테스트에도 그대로 주입됐다.
    // 그 결과 stub 을 빠뜨린 클라이언트(TMAP 경유지 최적화)가 매 실행마다 실호출을 날려 일일 허용량을 갉아먹었다.
    //
    // 환경변수는 config data(파일)보다 우선순위가 높으므로 여기서 비우면 파일 값이 덮인다. 모든 외부 클라이언트가
    // hasKey() 가드를 갖고 있어, 키가 없으면 stub 을 빠뜨려도 실호출 대신 폴백으로 떨어진다 — stub 누락을
    // 일일이 쫓지 않아도 되는 마지막 방어선이다.
    //
    // E2E 는 실호출이 목적이라 `-Pe2e` 로 명시적으로 열어준다. 기본값을 "막힘" 으로 두는 이유는, 실호출은
    // 켜는 걸 잊으면 테스트가 하나 안 돌 뿐이지만 끄는 걸 잊으면 허용량이 조용히 새기 때문이다.
    val allowRealExternalCalls = providers.gradleProperty("e2e").isPresent
    if (allowRealExternalCalls) {
        systemProperty("offway.e2e", "true")
    } else {
        environment("DATA_GO_KR_SERVICE_KEY", "")
        environment("TMAP_APP_KEY", "")
    }

    // provider 갱신 토큰 암호화 키(#301) — 테스트 전용 고정값이다.
    //
    // **비워 두면 안 된다.** 키가 없으면 그 토큰을 아예 저장하지 않는 것이 설계라(평문으로 흘려 넣지
    // 않으려는 것), Apple 연결 해제 통합 테스트가 "해제 시도 0건" 으로 죽는다. 그건 버그가 아니라
    // 설정이 없다는 뜻인데, 단언만 보면 기능이 깨진 것처럼 읽힌다.
    //
    // 여기 값을 주면 저장→암호화→복호화→해제까지 실제 경로가 통째로 돈다. 운영 키와 무관한 32바이트다.
    environment("PROVIDER_TOKEN_KEY_BASE64", "b2Zmd2F5LXRlc3Qtb25seS1rZXktMzJiLXh4eHh4eHg=")
}
