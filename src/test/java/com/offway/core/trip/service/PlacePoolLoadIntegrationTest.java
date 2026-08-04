package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.domain.PlaceCategory;
import com.offway.core.trip.domain.PlaceKind;
import com.offway.core.trip.repository.LicensedPlaceRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 부팅 적재가 실제로 도는지 확인한다(#144).
 *
 * <p>여기서 읽는 파일은 {@code src/test/resources/data/place-pool.csv.gz} 의 소량 풀이다(테스트 classpath 가
 * main 보다 앞선다). 다른 통합 테스트들이 stub 으로 "후보 없음" 시나리오를 만들기 때문에, 전량을 실으면 그 통제가
 * 무너진다. 배포에 실리는 16만 건 파일 자체는 {@code PlacePoolFileTest} 가 따로 검증한다.
 */
@SpringBootTest
class PlacePoolLoadIntegrationTest {

    /** 테스트 풀이 채운 지역 — 경상북도 의성군. */
    private static final long UISEONG = 76L;

    /** 조회 상한. 테스트 풀은 이보다 훨씬 작아 전량이 돌아온다. */
    private static final int CANDIDATE_LIMIT = 100;

    @Autowired
    private LicensedPlaceRepository licensedPlaceRepository;

    @Autowired
    private PlacePoolLoader placePoolLoader;

    @Test
    void 부팅하면_장소_풀이_적재된다() {
        assertTrue(licensedPlaceRepository.count() > 0, "장소 풀이 적재되지 않았습니다");
    }

    @Test
    void 지역과_종류로_후보를_찾는다() {
        List<LicensedPlace> stays = licensedPlaceRepository.findCandidates(UISEONG, PlaceKind.STAY, CANDIDATE_LIMIT);

        assertFalse(stays.isEmpty());
        assertTrue(stays.stream().allMatch(place -> place.getKind() == PlaceKind.STAY));
        assertTrue(stays.stream().anyMatch(place -> place.getName().equals("올인모텔")));
    }

    @Test
    void 좌표와_전화번호가_그대로_실린다() {
        LicensedPlace place = licensedPlaceRepository.findCandidates(UISEONG, PlaceKind.STAY, CANDIDATE_LIMIT).stream()
                .filter(candidate -> candidate.getName().equals("올인모텔"))
                .findFirst()
                .orElseThrow();

        assertEquals(36.3527, place.coordinate().lat(), 0.0001);
        assertEquals(128.6971, place.coordinate().lng(), 0.0001);
        assertEquals("0548341089", place.getTel());
    }

    /** 전화번호는 인허가 데이터에서 29% 만 채워진다 — 없는 게 정상이다. */
    @Test
    void 전화번호가_없는_장소도_실린다() {
        LicensedPlace place = licensedPlaceRepository.findCandidates(UISEONG, PlaceKind.STAY, CANDIDATE_LIMIT).stream()
                .filter(candidate -> candidate.getName().equals("산수유민박"))
                .findFirst()
                .orElseThrow();

        assertNotNull(place.getAddress());
        assertNull(place.getTel());
    }

    /** 재기동마다 다시 넣으면 부팅이 느려지고 중복이 쌓인다. */
    @Test
    void 이미_적재됐으면_로더를_다시_불러도_늘지_않는다() {
        long before = licensedPlaceRepository.count();

        placePoolLoader.load();

        assertEquals(before, licensedPlaceRepository.count());
    }

    /**
     * 절반만 실린 DB 를 정상으로 여기면 영영 고치지 못한다. 실제로 유니크 위반 하나로 89곳 중 42곳만
     * 실린 채 배포됐고, 재배포해도 "이미 있음" 으로 건너뛰어 그대로였다.
     */
    @Test
    void 적재가_파일과_어긋나면_다시_채운다() {
        long full = licensedPlaceRepository.count();
        assertTrue(full > 0);

        // 일부만 실린 상태를 만든다 — 비우기만 하면 "처음 적재" 경로를 타서 자가 치유를 검증하지 못한다.
        licensedPlaceRepository.deleteAll();
        licensedPlaceRepository.saveAll(List.of(LicensedPlace.builder()
                .regionId(UISEONG)
                .kind(PlaceKind.STAY)
                .category(PlaceCategory.LODGING)
                .name("절반만 실린 흔적")
                .address("경상북도 의성군 의성읍 어딘가 1")
                .lat(36.35)
                .lng(128.69)
                .build()));
        assertEquals(1, licensedPlaceRepository.count(), "일부만 남은 상태");

        placePoolLoader.load();

        assertEquals(full, licensedPlaceRepository.count(), "파일 전량으로 복구돼야 한다");
    }
}
