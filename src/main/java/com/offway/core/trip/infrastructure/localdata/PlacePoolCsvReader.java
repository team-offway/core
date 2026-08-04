package com.offway.core.trip.infrastructure.localdata;

import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.PlaceCategory;
import com.offway.core.trip.domain.PlaceKind;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 장소 풀 CSV(gzip)를 읽어 도메인으로 옮긴다(#144).
 *
 * <p>파일은 {@code scripts/build_place_pool.py} 가 인허가 ZIP 16개(870MB)에서 뽑아 만든다. 좌표 변환과 폐업 필터는
 * 그 단계에서 끝나 있으므로 여기서는 형식만 다룬다.
 *
 * <p>CSV 라이브러리를 들이지 않는다 — 우리가 만든 파일이라 방언이 고정돼 있고, 필요한 건 따옴표 필드 처리 하나뿐이다.
 */
@Slf4j
@Component
public class PlacePoolCsvReader {

    /** 컬럼 순서 계약. 어긋나면 값이 밀려 실리므로 읽기 전에 막는다. */
    private static final List<String> EXPECTED_HEADER =
            List.of("region_id", "kind", "category", "name", "address", "tel", "lat", "lng");

    private static final int COL_REGION_ID = 0;
    private static final int COL_KIND = 1;
    private static final int COL_CATEGORY = 2;
    private static final int COL_NAME = 3;
    private static final int COL_ADDRESS = 4;
    private static final int COL_TEL = 5;
    private static final int COL_LAT = 6;
    private static final int COL_LNG = 7;

    /** 깨진 행 로그 상한 — 형식이 통째로 틀어지면 수만 줄이 쏟아진다. */
    private static final int MAX_LOGGED_SKIPS = 20;

    /**
     * gzip CSV 스트림을 장소 목록으로 읽는다. 스트림은 읽은 뒤 닫는다.
     *
     * <p>깨진 행은 건너뛴다 — 분기마다 갱신되는 원본의 흠 하나로 풀 전체가 비면 안 된다. 다만 몇 건을 왜 버렸는지는
     * 반드시 남긴다(조용한 실패 금지).
     */
    public List<LicensedPlace> read(InputStream source) {
        List<LicensedPlace> places = new ArrayList<>();
        int skipped = 0;

        // source 를 첫 리소스로 둔다 — gzip 이 아닌 입력이면 GZIPInputStream 생성자가 던지는데,
        // 그때 reader 는 아직 할당 전이라 source 가 열린 채 남는다(Javadoc 의 "읽은 뒤 닫는다" 계약 위반).
        try (InputStream raw = source;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8))) {
            requireHeader(reader.readLine());

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                LicensedPlace place = parse(line, skipped);
                if (place == null) {
                    skipped++;
                    continue;
                }
                places.add(place);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("장소 풀 CSV 를 읽지 못했습니다", e);
        }

        if (skipped > 0) {
            log.warn("장소 풀 CSV 에서 {}건을 건너뛰었습니다 (읽은 건수={})", skipped, places.size());
        }
        return places;
    }

    private static void requireHeader(String header) {
        List<String> columns = header == null ? List.of() : splitCsv(header);
        if (!EXPECTED_HEADER.equals(columns)) {
            throw new IllegalStateException(
                    "장소 풀 CSV 헤더가 다릅니다. 기대=" + EXPECTED_HEADER + " 실제=" + columns);
        }
    }

    private static LicensedPlace parse(String line, int skippedSoFar) {
        try {
            List<String> cells = splitCsv(line);
            if (cells.size() != EXPECTED_HEADER.size()) {
                throw new IllegalArgumentException("컬럼 수가 " + cells.size() + "개입니다");
            }
            return LicensedPlace.builder()
                    .regionId(Long.parseLong(cells.get(COL_REGION_ID).trim()))
                    .kind(PlaceKind.valueOf(cells.get(COL_KIND).trim()))
                    .category(PlaceCategory.valueOf(cells.get(COL_CATEGORY).trim()))
                    .name(cells.get(COL_NAME))
                    .address(cells.get(COL_ADDRESS))
                    .tel(cells.get(COL_TEL))
                    .lat(Double.parseDouble(cells.get(COL_LAT).trim()))
                    .lng(Double.parseDouble(cells.get(COL_LNG).trim()))
                    .build();
        } catch (RuntimeException e) {
            if (skippedSoFar < MAX_LOGGED_SKIPS) {
                // 원본 줄을 그대로 남기지 않는다 — 상호·주소가 로그에 흘러든다.
                log.warn("장소 풀 CSV 행을 건너뜁니다: {}", e.getMessage());
            }
            return null;
        }
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
