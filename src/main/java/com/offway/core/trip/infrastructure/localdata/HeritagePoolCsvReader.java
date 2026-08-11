package com.offway.core.trip.infrastructure.localdata;

import com.offway.core.trip.domain.HeritageGroup;
import com.offway.core.trip.domain.HeritagePlace;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 국가유산 풀 CSV(gzip)를 읽어 도메인으로 옮긴다(#160).
 *
 * <p>파일은 {@code scripts/build_heritage_pool.py} 가 국가유산청 오픈API 에서 만든다. 지오코딩과 자연키 중복
 * 제거는 그 단계에서 끝나 있으므로 여기서는 형식과 <b>쓸 수 있는 것만 남기는 판정</b>만 다룬다.
 *
 * <p><b>여기서 거른다.</b> 파일에는 유물·기록유산·무형유산까지 전부 들어 있다. 규칙을 자바에 두면 판정이 바뀔 때
 * 40분짜리 수집을 다시 돌리지 않아도 되고, 그 규칙이 테스트로 고정된다.
 */
@Slf4j
@Component
public class HeritagePoolCsvReader {

    /** 컬럼 순서 계약. 어긋나면 값이 밀려 실리므로 읽기 전에 막는다. */
    private static final List<String> EXPECTED_HEADER = List.of(
            "region_id", "kind", "group", "subgroup", "name", "address", "lat", "lng", "image_url", "content");

    private static final int COL_REGION_ID = 0;
    private static final int COL_KIND = 1;
    private static final int COL_GROUP = 2;
    private static final int COL_SUBGROUP = 3;
    private static final int COL_NAME = 4;
    private static final int COL_ADDRESS = 5;
    private static final int COL_LAT = 6;
    private static final int COL_LNG = 7;
    private static final int COL_IMAGE_URL = 8;
    private static final int COL_CONTENT = 9;

    /** 깨진 행 로그 상한 — 형식이 통째로 틀어지면 수천 줄이 쏟아진다. */
    private static final int MAX_LOGGED_SKIPS = 20;

    /**
     * gzip CSV 스트림을 국가유산 목록으로 읽는다. 스트림은 읽은 뒤 닫는다.
     *
     * <p>버린 건수를 <b>사유별로</b> 남긴다. "방문 대상이 아님"·"좌표 없음"·"형식 깨짐" 은 전혀 다른 신호인데
     * 한 숫자로 합치면 원본이 망가진 것을 못 알아챈다.
     */
    public List<HeritagePlace> read(InputStream source) {
        List<HeritagePlace> places = new ArrayList<>();
        int notVisitable = 0;
        int withoutCoordinate = 0;
        int broken = 0;

        try (InputStream raw = source;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8))) {
            requireHeader(reader.readLine());

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> cells;
                try {
                    cells = requireColumns(line);
                } catch (RuntimeException e) {
                    broken = logAndCount(broken, e.getMessage());
                    continue;
                }
                // 대분류가 방문 대상이 아니면 코스에도 화면에도 쓸 데가 없다 — 담지 않는다.
                // 대분류가 통과해도 중분류에서 한 번 더 거른다(유물산포지·무덤).
                Optional<HeritageGroup> group = HeritageGroup.from(cells.get(COL_GROUP));
                if (group.filter(HeritageGroup::isVisitable).isEmpty()
                        || !HeritageGroup.isVisitableSubgroup(cells.get(COL_SUBGROUP))) {
                    notVisitable++;
                    continue;
                }
                if (cells.get(COL_LAT).isBlank() || cells.get(COL_LNG).isBlank()) {
                    // 지오코딩으로도 못 채운 것. 좌표 없이는 동선에 못 올린다.
                    withoutCoordinate++;
                    continue;
                }
                try {
                    places.add(toPlace(cells, group.get()));
                } catch (RuntimeException e) {
                    broken = logAndCount(broken, e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("국가유산 풀 CSV 를 읽지 못했습니다", e);
        }

        log.info("국가유산 풀 CSV 읽기 완료. 실을 것={} 방문대상아님={} 좌표없음={} 형식오류={}",
                places.size(), notVisitable, withoutCoordinate, broken);
        return places;
    }

    private static HeritagePlace toPlace(List<String> cells, HeritageGroup group) {
        return HeritagePlace.builder()
                .regionId(Long.parseLong(cells.get(COL_REGION_ID).trim()))
                .kind(cells.get(COL_KIND))
                .group(group)
                .name(cells.get(COL_NAME))
                .address(cells.get(COL_ADDRESS))
                .lat(Double.parseDouble(cells.get(COL_LAT).trim()))
                .lng(Double.parseDouble(cells.get(COL_LNG).trim()))
                .imageUrl(cells.get(COL_IMAGE_URL))
                .description(cells.get(COL_CONTENT))
                .build();
    }

    private int logAndCount(int broken, String reason) {
        if (broken < MAX_LOGGED_SKIPS) {
            // 원본 줄을 그대로 남기지 않는다 — 이름·소재지가 로그에 흘러든다.
            log.warn("국가유산 풀 CSV 행을 건너뜁니다: {}", reason);
        }
        return broken + 1;
    }

    private static void requireHeader(String header) {
        List<String> columns = header == null ? List.of() : splitCsv(header);
        if (!EXPECTED_HEADER.equals(columns)) {
            throw new IllegalStateException(
                    "국가유산 풀 CSV 헤더가 다릅니다. 기대=" + EXPECTED_HEADER + " 실제=" + columns);
        }
    }

    private static List<String> requireColumns(String line) {
        List<String> cells = splitCsv(line);
        if (cells.size() != EXPECTED_HEADER.size()) {
            throw new IllegalArgumentException("컬럼 수가 " + cells.size() + "개입니다");
        }
        return cells;
    }

    /** 따옴표로 감싼 필드(쉼표 포함)와 이스케이프된 따옴표("")를 다룬다. */
    private static List<String> splitCsv(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c != '"') {
                    cell.append(c);
                } else if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = false;
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }
}
