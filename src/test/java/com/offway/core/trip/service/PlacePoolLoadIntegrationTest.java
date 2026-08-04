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
import com.offway.core.trip.repository.PlacePoolSourceRepository;
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

    @Autowired
    private PlacePoolSourceRepository placePoolSourceRepository;

    /**
     * DB 를 바꾼 테스트가 원래 적재 상태로 되돌린다.
     *
     * <p>이 클래스에는 클래스 레벨 {@code @Transactional} 이 없다 — 검증 대상인 적재가 부팅 이벤트에서
     * 이미 커밋된 것이라 롤백에 기댈 수 없다. 대신 체크섬을 무효화해 로더가 파일로 다시 채우게 한다.
     */
    private void restorePool() {
        placePoolSourceRepository.record("restore-for-test", 0);
        placePoolLoader.load();
    }

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

    /** 같은 파일이면 다시 넣지 않는다 — 재기동마다 16만 건을 다시 쓰면 부팅이 길어진다. */
    @Test
    void 같은_파일이면_건너뛴다() {
        long before = licensedPlaceRepository.count();

        placePoolLoader.load();

        assertEquals(before, licensedPlaceRepository.count());
    }

    /**
     * 파일이 바뀌면 통째로 다시 채운다.
     *
     * <p>건수로만 판정하면 갱신된 파일의 건수가 우연히 같을 때 그대로 건너뛰어, 낡은 장소 정보가
     * 조회용으로 남는다. 그래서 파일 내용의 체크섬으로 가른다 — 여기서는 체크섬을 다른 값으로 바꿔
     * "파일이 바뀐" 상황을 만든다.
     */
    @Test
    void 파일이_바뀌면_전량_다시_채운다() {
        long full = licensedPlaceRepository.count();
        assertTrue(full > 1, "테스트 풀이 2건 이상이어야 복구를 검증할 수 있다");

        // 내용을 흐트러뜨리고, 적재 출처도 다른 파일에서 온 것처럼 바꾼다
        licensedPlaceRepository.deleteAll();
        licensedPlaceRepository.saveAll(List.of(LicensedPlace.builder()
                .regionId(UISEONG)
                .kind(PlaceKind.STAY)
                .category(PlaceCategory.LODGING)
                .name("낡은 적재의 흔적")
                .address("경상북도 의성군 의성읍 어딘가 1")
                .lat(36.35)
                .lng(128.69)
                .build()));
        placePoolSourceRepository.record("0".repeat(64), 1);

        placePoolLoader.load();

        assertEquals(full, licensedPlaceRepository.count(), "파일 전량으로 교체돼야 한다");
        assertEquals(0, licensedPlaceRepository.findCandidates(UISEONG, PlaceKind.STAY, 100).stream()
                        .filter(place -> place.getName().equals("낡은 적재의 흔적"))
                        .count(),
                "낡은 행이 남으면 안 된다");
    }

    @org.junit.jupiter.api.AfterEach
    void 적재_상태를_되돌린다() {
        restorePool();
    }
}
