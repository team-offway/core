package com.offway.core.common.external;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * <b>외부를 부르는 스케줄 작업은 자기 이름을 심는다</b>(#594).
 *
 * <h2>왜 테스트로 드나</h2>
 *
 * <p>{@link CallerContext} 를 감싸는 것을 빠뜨려도 <b>아무것도 안 깨진다.</b> 배치는 그대로 돌고
 * 데이터도 정상으로 쌓인다 — 집계에만 {@code 미상} 이 늘어난다. 컴파일도 테스트도 초록이라, 알아채는
 * 유일한 계기가 "디스코드 알림이 계속 미상이네" 라는 사람의 눈이다.
 *
 * <p>실제로 그렇게 지나갔다. #285 가 주체별 집계를 만든 뒤 들어온 배치 둘이 이 한 줄을 빠뜨렸고,
 * {@code TransitDurationRefreshService} 는 <b>TAGO 고속버스 한도의 36%</b> 를 이름 없이 태웠다.
 *
 * <p>훅으로는 못 막는다 — "이 클래스가 외부 한도를 쓰나" 는 정규식이 아니라 판단이다. 그래서
 * {@code AttributedCoverageTest} 와 같은 방식으로, <b>오탐이 거의 없는 좁은 표지</b>만 본다.
 *
 * <h2>이 그물이 놓치는 것</h2>
 *
 * <p><b>그물이지 체가 아니다.</b> 클라이언트를 직접 들지 않고 다른 서비스를 거쳐 외부에 닿는 배치는
 * 안 걸린다. 그런 배치를 만들 때는 여전히 <b>"이 작업이 외부 한도를 쓰나" 를 직접 묻는 것</b>이
 * 먼저다. 이 테스트는 가장 흔한 모양(배치가 클라이언트를 직접 주입받는 것)을 잠글 뿐이다.
 */
class BatchCallerCoverageTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java", "com", "offway", "core");

    /** 스케줄로 도는 작업의 표지. */
    private static final Pattern SCHEDULED = Pattern.compile("@Scheduled\\b");

    /**
     * 외부를 직접 부르는 표지 — 필드로 주입받은 클라이언트다.
     *
     * <p>주석이나 javadoc 에 이름만 적힌 경우를 세지 않으려고 <b>필드 선언 모양</b>으로 좁힌다.
     * 넓게 잡으면 "{@code ...Client} 를 타므로" 같은 설명문에 걸려 오탐이 나고, 오탐이 나면 이
     * 테스트가 무시당한다.
     */
    private static final Pattern CLIENT_FIELD =
            Pattern.compile("private final \\w*(Client|WebClient) \\w+;");

    /** 이름을 심었다는 표지. 어느 형태든({@code run}·{@code wrap}) 이 타입을 지난다. */
    private static final Pattern PLANTS_CALLER = Pattern.compile("CallerContext\\b");

    /**
     * 검사 대상이 이보다 적으면 <b>테스트가 헛돈다</b>.
     *
     * <p>지금 15 개 안팎이다(지역·축제·갤러리·반려동반·구간소요시간 등). 표지 정규식이 낡아 0 건을
     * 훑으면서 초록이 되는 것을 막는 최소선이라 여유 있게 낮춰 잡는다.
     */
    private static final int MIN_SCANNED = 8;

    @Test
    void 외부를_부르는_스케줄_작업은_주체_이름을_심는다() throws IOException {
        List<String> missing = new ArrayList<>();
        List<String> scanned = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(BatchCallerCoverageTest::isJava).toList()) {
                String source = Files.readString(file);
                if (!SCHEDULED.matcher(source).find() || !CLIENT_FIELD.matcher(source).find()) {
                    continue;
                }
                scanned.add(file.getFileName().toString());
                if (!PLANTS_CALLER.matcher(source).find()) {
                    missing.add(file.getFileName().toString());
                }
            }
        }

        assertTrue(scanned.size() >= MIN_SCANNED,
                "훑은 스케줄 작업이 " + scanned.size() + "개뿐이다 — 표지 정규식이 낡았다: " + scanned);
        assertTrue(missing.isEmpty(),
                "외부를 부르면서 주체 이름을 안 심는 스케줄 작업이 있다(집계에 미상으로 쌓인다): " + missing);
    }

    private static boolean isJava(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".java");
    }
}
