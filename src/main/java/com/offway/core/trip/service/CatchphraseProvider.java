package com.offway.core.trip.service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 관광지 캐치프레이즈 조회 — 한국관광공사 "대한민국 구석구석" 캐치프레이즈(contentId→감성 한 줄)를 부팅 시 인메모리로 든다.
 * TourAPI 콘텐츠가 얇거나 실패해도 코스·장소에 감성 문구를 붙여 화면을 살린다.
 *
 * <p>정적 참조 데이터(약 4.5만 건·수 MB)라 외부 호출·DB 없이 클래스패스 CSV 로 시드한다. 파일이 없거나 깨져도 부팅을 막지 않는다
 * (캐치프레이즈는 부가 정보 — 없으면 null).
 */
@Slf4j
@Component
public class CatchphraseProvider {

    private static final String RESOURCE = "data/catchphrase.csv";

    private Map<String, String> byContentId = Map.of();

    @PostConstruct
    void load() {
        Map<String, String> map = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine(); // 헤더
            String line;
            while ((line = reader.readLine()) != null) {
                int comma = line.indexOf(',');
                if (comma <= 0) {
                    continue;
                }
                String contentId = line.substring(0, comma).trim();
                String phrase = unquote(line.substring(comma + 1).trim());
                if (!contentId.isBlank() && !phrase.isBlank()) {
                    map.put(contentId, phrase);
                }
            }
            // 끝까지 읽은 뒤에만 발행한다 — 중간에 실패하면 일부 POI 만 캐치프레이즈가 붙는 부분 공개가 되므로 all-or-nothing.
            byContentId = Map.copyOf(map);
            log.debug("캐치프레이즈 로딩 {}건", byContentId.size());
        } catch (IOException e) {
            byContentId = Map.of(); // 부분 데이터 발행 금지 — 실패하면 전부 없음(캐치프레이즈는 부가 정보라 null 로 진행).
            log.warn("캐치프레이즈 CSV 로딩 실패 — 캐치프레이즈 없이 진행", e);
        }
    }

    /** contentId 의 캐치프레이즈. 없으면 빈 Optional. */
    public Optional<String> forContentId(String contentId) {
        return Optional.ofNullable(byContentId.get(contentId));
    }

    /** CSV 표준 인용(콤마 포함 필드는 큰따옴표로 감싸고 내부 큰따옴표는 이중화) 해제. */
    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\"\"", "\"");
        }
        return value;
    }
}
