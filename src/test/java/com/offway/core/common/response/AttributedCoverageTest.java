package com.offway.core.common.response;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * <b>공사 유래 값을 내리는 응답 DTO 는 출처를 선언한다</b>(#399).
 *
 * <p>필드를 더하면서 {@code sources()} 갱신을 깜빡할 수 있다. 훅으로는 못 막는다 — "이 필드가 공사에서
 * 오나" 는 정규식이 아니라 판단이라, 정규식으로 넣으면 오탐이 나고 그러면 그 규칙 자체가 무시당한다.
 *
 * <p>그래서 <b>범위를 좁혀</b> 잡는다. 아래 표지 필드는 출처가 사실상 하나로 정해져 있어 오탐 여지가
 * 작다 — 이 값이 응답에 있는데 {@link Attributed} 가 없으면 표기가 빠진 것이다.
 *
 * <p>표기 누락은 규정 위반이라 "가끔 빠진다" 가 허용되지 않는다. 그래서 커버리지를 테스트로 든다.
 */
class AttributedCoverageTest {

    private static final Path DTO_ROOT = Path.of("src", "main", "java", "com", "offway", "core");

    /**
     * 이 값이 실리면 공사·기상청·천문연구원 중 하나를 반드시 지난다.
     *
     * <p>고른 기준은 <b>다른 출처로는 설명이 안 되는 것</b>이다. {@code imageUrl} 처럼 여러 출처에서
     *오는 값은 넣지 않는다 — 오탐이 나면 이 테스트가 무시당한다.
     */
    private static final List<String> KTO_MARKERS =
            List.of("poiContentId", "catchphrase", "crowdLevel", "contentTypeId");

    /**
     * 검사 대상이 이보다 적으면 <b>테스트가 헛돈다</b>.
     *
     * <p>지금 여섯이다(홈·지역 상세·지역 목록·지역 추천·장소 상세·코스). 여유를 두고 그보다 낮게 잡되,
     * 0 이 아니게 둔다 — 표지 필드명이 바뀌면 조용히 초록이 되는 것을 막는 최소선이다.
     */
    private static final int MIN_SCANNED = 4;

    /** 요청 DTO 는 대상이 아니다 — 우리가 받는 값이라 출처가 없다. */
    private static final String REQUEST_SUFFIX = "Request.java";

    @Test
    void 공사_유래_필드를_가진_응답_DTO는_출처를_선언한다() throws IOException {
        List<String> missing = new ArrayList<>();
        List<String> scanned = new ArrayList<>();
        try (Stream<Path> files = Files.walk(DTO_ROOT)) {
            for (Path file : files.filter(AttributedCoverageTest::isResponseDto).toList()) {
                String source = Files.readString(file);
                if (!hasKtoMarker(source)) {
                    continue;
                }
                scanned.add(file.getFileName().toString());
                if (!declaresSources(source)) {
                    missing.add(file.getFileName().toString());
                }
            }
        }

        // 표지를 하나도 못 찾았다면 이 테스트는 아무것도 검사하지 않은 것이다 — 필드명이 바뀌었거나
        // 경로 판정이 깨졌다는 뜻이라, 초록이 아니라 빨강이어야 한다.
        assertTrue(scanned.size() >= MIN_SCANNED,
                "표지 필드를 가진 응답 DTO 를 " + scanned.size() + "개밖에 못 찾았다 — 이 테스트가 헛돌고 있다: " + scanned);
        assertTrue(missing.isEmpty(),
                "공사 유래 값을 싣는데 sources() 를 선언하지 않았다 — 화면에 출처 표기가 빠진다: " + missing);
    }

    private static boolean isResponseDto(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".java")
                && !name.endsWith(REQUEST_SUFFIX)
                && file.toString().replace('\\', '/').contains("/controller/dto/");
    }

    /**
     * 표지 필드가 <b>선언</b>으로 들어 있는지 본다.
     *
     * <p>주석·문서에 이름만 나온 것과 가르려고 소문자로 낮춰 비교하지 않는다 — 필드명 그대로 찾는다.
     */
    private static boolean hasKtoMarker(String source) {
        return KTO_MARKERS.stream().anyMatch(marker -> source.contains(" " + marker) || source.contains("(" + marker));
    }

    private static boolean declaresSources(String source) {
        return source.contains("implements Attributed")
                || source.contains(", Attributed")
                || source.toLowerCase(Locale.ROOT).contains("set<datasource> sources()");
    }
}
