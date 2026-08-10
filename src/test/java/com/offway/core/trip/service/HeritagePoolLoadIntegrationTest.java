package com.offway.core.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.trip.domain.HeritageGroup;
import com.offway.core.trip.domain.HeritagePlace;
import com.offway.core.trip.repository.HeritagePlaceRepository;
import com.offway.core.trip.repository.HeritagePoolSourceRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 국가유산 부팅 적재(#160) — <b>무엇을 싣고 무엇을 버리는가</b>.
 *
 * <p>여기서 읽는 파일은 {@code src/test/resources/data/heritage-pool.csv.gz} 의 소량 풀이다(테스트 classpath 가
 * main 보다 앞선다). 다른 통합 테스트들이 stub 으로 "후보 없음" 시나리오를 만들기 때문에, 배포용 3,735건을
 * 전량 실으면 그 통제가 무너진다. 장소 풀(#144)과 같은 구성이다.
 */
@SpringBootTest
class HeritagePoolLoadIntegrationTest {

    /** 테스트 풀이 채운 지역 — 경상북도 의성군. */
    private static final long UISEONG = 76L;

    private static final int CANDIDATE_LIMIT = 100;

    @Autowired
    private HeritagePlaceRepository heritagePlaceRepository;

    @Autowired
    private HeritagePoolLoader heritagePoolLoader;

    @Autowired
    private HeritagePoolSourceRepository heritagePoolSourceRepository;

    /**
     * DB 를 바꾼 테스트가 원래 적재 상태로 되돌린다.
     *
     * <p>클래스 레벨 {@code @Transactional} 이 없다 — 검증 대상인 적재가 부팅 이벤트에서 이미 커밋된 것이라
     * 롤백에 기댈 수 없다. 대신 체크섬을 무효화해 로더가 파일로 다시 채우게 한다.
     */
    private void restorePool() {
        heritagePoolSourceRepository.record("restore-for-test", 0);
        heritagePoolLoader.load();
    }

    @Test
    void 부팅하면_국가유산이_적재된다() {
        assertTrue(heritagePlaceRepository.count() > 0, "국가유산 풀이 적재되지 않았습니다");
    }

    @Test
    void 방문할_수_있는_것만_싣는다() {
        // 파일에는 소장 유물·무형유산까지 전부 들어 있다. 그대로 실으면 그림 한 점이 코스 목적지가 된다 —
        // 우리 89곳의 국보·보물 표본 12건이 실제로 전부 소장 유물이었다.
        List<HeritagePlace> found = heritagePlaceRepository.findVisitableCandidates(UISEONG, CANDIDATE_LIMIT);

        assertEquals(3, found.size());
        assertTrue(found.stream().allMatch(HeritagePlace::isVisitable));
        assertTrue(found.stream().noneMatch(place -> place.getName().equals("자수 초충도 병풍")));
        assertTrue(found.stream().noneMatch(place -> place.getName().equals("의성 어느장")));
    }

    @Test
    void 좌표가_없으면_싣지_않는다() {
        // 지오코딩으로도 못 채운 것. 좌표 없이는 동선에 못 올린다.
        List<HeritagePlace> found = heritagePlaceRepository.findVisitableCandidates(UISEONG, CANDIDATE_LIMIT);

        assertTrue(found.stream().noneMatch(place -> place.getName().equals("의성 어느 옛터")));
    }

    @Test
    void 모르는_대분류는_싣지_않는다() {
        // 제공기관이 분류를 늘려도 조용히 섞여 들어가지 않는다.
        List<HeritagePlace> found = heritagePlaceRepository.findVisitableCandidates(UISEONG, CANDIDATE_LIMIT);

        assertTrue(found.stream().noneMatch(place -> place.getName().equals("의성 어느 것")));
    }

    @Test
    void 사진은_https_로_실린다() {
        // 원본은 http 로 주는데 그대로 부르면 302 로 튕겨 앱에서 한 장도 안 뜬다.
        HeritagePlace pagoda = heritagePlaceRepository.findVisitableCandidates(UISEONG, CANDIDATE_LIMIT).stream()
                .filter(place -> place.getName().equals("의성 탑리리 오층석탑"))
                .findFirst()
                .orElseThrow();

        assertTrue(pagoda.getImageUrl().startsWith("https://"), "이미지가 http 그대로 실렸습니다");
        assertEquals(HeritageGroup.HISTORIC_STRUCTURE, pagoda.getGroup());
        assertEquals("보물", pagoda.getKind());
        assertFalse(pagoda.getDescription().isBlank());
    }

    @Test
    void 같은_파일이면_다시_채우지_않는다() {
        // 파일이 그대로면 부팅마다 수천 건을 지웠다 넣지 않는다.
        long before = heritagePlaceRepository.count();

        heritagePoolLoader.load();

        assertEquals(before, heritagePlaceRepository.count());
    }

    @Test
    void 체크섬이_다르면_다시_채운다() {
        heritagePlaceRepository.deleteAll();
        heritagePoolSourceRepository.record("stale-checksum", 0);

        heritagePoolLoader.load();

        assertTrue(heritagePlaceRepository.count() > 0, "파일이 바뀌었는데 다시 채우지 않았습니다");
        restorePool();
    }
}
