package com.offway.core.trip.infrastructure.localdata;

import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.PlaceCategory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        // **자연키로 접으면서 읽는다**(#516). 원본에 한 업소가 야영장업과 숙박업으로 각각 신고된
        // 경우가 있어(실측 36건), 종류를 분류에서 얻기 시작하면 (지역·종류·상호·주소)가 겹친다.
        // 그대로 두면 DB 의 ux_licensed_place_natural 에 걸려 적재가 통째로 실패한다.
        Map<String, LicensedPlace> byNaturalKey = new LinkedHashMap<>();
        int skipped = 0;
        int folded = 0;

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
                String key = naturalKey(place);
                LicensedPlace kept = byNaturalKey.get(key);
                if (kept == null) {
                    byNaturalKey.put(key, place);
                    continue;
                }
                folded++;
                // 겹치면 **야영장이 아닌 쪽을 남긴다.** 그쪽 분류가 더 구체적인 업종(숙박업·펜션)이라
                // 화면에 나갈 뱃지로도 낫다. 보정 마이그레이션도 같은 규칙으로 정리한다.
                if (kept.getCategory() == PlaceCategory.CAMPGROUND
                        && place.getCategory() != PlaceCategory.CAMPGROUND) {
                    byNaturalKey.put(key, place);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("장소 풀 CSV 를 읽지 못했습니다", e);
        }

        List<LicensedPlace> places = List.copyOf(byNaturalKey.values());
        if (skipped > 0) {
            log.warn("장소 풀 CSV 에서 {}건을 건너뛰었습니다 (읽은 건수={})", skipped, places.size());
        }
        if (folded > 0) {
            // 조용히 접지 않는다 — 이 수가 갑자기 늘면 원본의 중복 신고가 늘었다는 신호다.
            log.info("장소 풀 CSV 에서 자연키가 겹치는 {}건을 접었습니다 (남은 건수={})", folded, places.size());
        }
        return places;
    }

    /** DB 의 {@code ux_licensed_place_natural} 과 <b>같은 열쇠</b>여야 한다 — 다르면 여기서 통과한 것이 거기서 걸린다. */
    private static String naturalKey(LicensedPlace place) {
        return place.getRegionId() + "|" + place.getKind() + "|" + place.getName() + "|" + place.getAddress();
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
            // **종류는 CSV 가 아니라 분류에서 얻는다**(#516). 두 칸이 따로 실리면 어긋날 수 있고,
            // 실제로 어긋났다 — 야영장이 볼거리로 분류돼 관광 슬롯에 캠핑장이 떴다. 분류를 고쳐도
            // 파일에 박힌 kind 가 그대로면 재생성 전까지 반영되지 않는다.
            //
            // 파일의 kind 칸은 그대로 둔다. 형식이 바뀌면 헤더 검사가 먼저 깨지는데, 그 신호를
            // 없앨 이유가 없다.
            PlaceCategory category = PlaceCategory.valueOf(cells.get(COL_CATEGORY).trim());
            return LicensedPlace.builder()
                    .regionId(Long.parseLong(cells.get(COL_REGION_ID).trim()))
                    .kind(category.kind())
                    .category(category)
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
