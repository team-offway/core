package com.offway.core.trip.infrastructure.localdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.PlaceCategory;
import com.offway.core.trip.domain.PlaceKind;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class PlacePoolCsvReaderTest {

    private static final String HEADER = "region_id,kind,category,name,address,tel,lat,lng\n";

    private static InputStream gzip(String csv) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
                gz.write(csv.getBytes(StandardCharsets.UTF_8));
            }
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void 정상_행을_장소로_읽는다() {
        String csv = HEADER + "16,STAY,LODGING,올인모텔,경상북도 의성군 의성읍 북부길 5-4,0548341089,36.3527,128.6971\n";

        List<LicensedPlace> places = new PlacePoolCsvReader().read(gzip(csv));

        assertEquals(1, places.size());
        LicensedPlace place = places.getFirst();
        assertEquals(16L, place.getRegionId());
        assertEquals(PlaceKind.STAY, place.getKind());
        assertEquals(PlaceCategory.LODGING, place.getCategory());
        assertEquals("올인모텔", place.getName());
        assertEquals("0548341089", place.getTel());
        assertEquals(36.3527, place.getLat());
    }

    @Test
    void 쉼표가_든_필드는_따옴표로_감싸도_읽는다() {
        String csv = HEADER + "16,STAY,HANOK,\"우경고택, 별채\",\"경북 의성군 금성면, 1층\",,36.3,128.6\n";

        List<LicensedPlace> places = new PlacePoolCsvReader().read(gzip(csv));

        assertEquals("우경고택, 별채", places.getFirst().getName());
        assertEquals("경북 의성군 금성면, 1층", places.getFirst().getAddress());
    }

    @Test
    void 전화번호가_비면_null_이다() {
        String csv = HEADER + "16,FOOD,RESTAURANT,대박집,의성군 어딘가,,36.3,128.6\n";

        assertNull(new PlacePoolCsvReader().read(gzip(csv)).getFirst().getTel());
    }

    @Test
    void 빈_파일이면_빈_목록이다() {
        assertTrue(new PlacePoolCsvReader().read(gzip(HEADER)).isEmpty());
    }

    /**
     * 한 행이 깨졌다고 전량 적재를 포기하면, 분기마다 갱신되는 원본의 사소한 흠 하나로 풀 전체가 빈다.
     * 깨진 행만 버리고 나머지를 살린다.
     */
    @Test
    void 깨진_행은_건너뛰고_나머지를_읽는다() {
        String csv = HEADER
                + "16,STAY,LODGING,정상1,주소,,36.3,128.6\n"
                + "16,STAY,없는분류,이상한행,주소,,36.3,128.6\n"
                + "16,STAY,LODGING,좌표깨짐,주소,,999,128.6\n"
                + "notanumber,STAY,LODGING,지역깨짐,주소,,36.3,128.6\n"
                + "16,STAY,LODGING,정상2,주소,,36.4,128.7\n";

        List<LicensedPlace> places = new PlacePoolCsvReader().read(gzip(csv));

        assertEquals(List.of("정상1", "정상2"), places.stream().map(LicensedPlace::getName).toList());
    }

    /** 헤더가 다르면 컬럼 순서가 바뀐 것이다 — 조용히 엉뚱한 값을 싣지 않고 즉시 멈춘다. */
    @Test
    void 헤더가_어긋나면_거부한다() {
        String csv = "region_id,kind,name\n16,STAY,올인모텔\n";

        assertThrows(IllegalStateException.class, () -> new PlacePoolCsvReader().read(gzip(csv)));
    }
}
