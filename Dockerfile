# 실행 전용 이미지 — jar 는 CI 가 이미 만든 것을 받는다.
# 컨테이너 안에서 gradle build 를 다시 돌리지 않는 이유: CI 러너에 의존성 캐시가 이미 있고,
# 이미지 안에서 빌드하면 캐시가 없어 매번 전체 의존성을 내려받아 배포가 느려진다.
# 패치 버전까지 고정한다 — 25-jre 같은 이동 태그는 코드 변경 없이도 배포마다 다른 JRE 를 받아와,
# "어제는 됐는데" 를 추적 불가능하게 만든다. 업그레이드는 이 줄을 고치는 의도적 행위로 둔다.
FROM eclipse-temurin:25.0.3_9-jre

WORKDIR /app

# root 로 돌리지 않는다 — 컨테이너가 뚫려도 호스트로 번지는 폭을 줄인다.
RUN useradd --system --create-home --shell /usr/sbin/nologin offway
USER offway

# CI 가 build/libs/*.jar 를 이 경로로 넘긴다.
COPY --chown=offway:offway build/libs/*.jar app.jar

EXPOSE 8080

# 컨테이너 메모리 한도를 JVM 이 인식하게 둔다(기본 동작) — -Xmx 를 박으면 인스턴스를 바꿀 때마다 고쳐야 한다.
# 타임존은 KST 고정. 연차·D-day·"오늘자" 판정이 전부 KST 기준이라 컨테이너가 UTC 면 하루가 어긋난다.
ENV TZ=Asia/Seoul

# 이 이미지는 배포 전용이므로 prod 가 기본이다. 애플리케이션 기본값은 local 이라, 프로파일이 빠지면
# H2 인메모리와 **알려진 계정 dev/dev** 로 조용히 뜬다 — 외부에 열린 8080 에서는 그게 곧 무방비다.
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
