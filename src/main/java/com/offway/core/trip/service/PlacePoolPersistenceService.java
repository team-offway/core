package com.offway.core.trip.service;

import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.repository.LicensedPlaceRepository;
import com.offway.core.trip.repository.PlacePoolSourceRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소 풀 적재의 트랜잭션 경계(#144).
 *
 * <p><b>왜 빈을 나누는가.</b> {@link PlacePoolLoader} 는 적재가 실패해도 서버를 죽이지 않으려고 예외를 잡는다.
 * 그런데 트랜잭션 경계가 그 안에 있으면, 예외를 잡는 순간 Spring 은 정상 종료로 보고 <b>그때까지 넣은 것을
 * 커밋한다.</b> 실제로 그렇게 됐다 — 유니크 위반 하나로 89곳 중 42곳만 실린 채 배포가 성공으로 끝났고,
 * 목록은 200 을 주면서 내용이 비어 있었다.
 *
 * <p>그래서 경계를 밖으로 뺀다. 여기서 던진 예외는 프록시를 지나며 <b>전량 롤백</b>되고, 호출자가 그 예외를
 * 받아 기동을 이어간다. "전부 들어가거나, 하나도 안 들어가거나" 만 남는다 — 절반만 실린 상태가 가장 나쁘다.
 *
 * <p>같은 빈 안에서 부르면 프록시를 안 거쳐 이 분리가 무의미해진다(self-invocation).
 */
@Service
@RequiredArgsConstructor
public class PlacePoolPersistenceService {

    private final LicensedPlaceRepository licensedPlaceRepository;
    private final PlacePoolSourceRepository placePoolSourceRepository;

    /**
     * 기존 장소를 비우고 전량 다시 적재한다. 실패하면 예외를 던져 <b>비우기까지 함께</b> 롤백시킨다 —
     * 지우기만 하고 못 채우면 풀이 통째로 빈다.
     *
     * <p>분기 1회 갱신이라 부분 갱신(diff)을 하지 않는다. 통째로 바꾸는 편이 단순하고, 원본에서 사라진
     * 업소가 남는 문제도 함께 해결된다.
     *
     * @return 실제로 삽입된 건수
     */
    @Transactional
    public int replaceAll(List<LicensedPlace> places, String checksum) {
        licensedPlaceRepository.deleteAll();
        int inserted = licensedPlaceRepository.saveAll(places);
        if (inserted != places.size()) {
            // 던져야 DELETE 까지 함께 말린다. 여기서 그냥 돌려주면 트랜잭션이 커밋되고,
            // 호출자가 로그를 남기든 말든 부분 적재가 DB 에 남는다.
            throw new IllegalStateException(
                    "장소 풀이 일부만 적재됐습니다. 기대=" + places.size() + " 실제=" + inserted);
        }
        // 출처 기록도 같은 트랜잭션이다. 따로 커밋되면 "적재는 실패했는데 체크섬은 남은" 상태가 되어
        // 다음 기동이 이미 적재된 줄 알고 건너뛴다.
        placePoolSourceRepository.record(checksum, inserted);
        return inserted;
    }

    /** 마지막으로 적재한 파일의 체크섬. 적재된 적이 없으면 비어 있음. */
    @Transactional(readOnly = true)
    public Optional<String> loadedChecksum() {
        return placePoolSourceRepository.findChecksum();
    }

    /** 이미 적재돼 있는지. */
    @Transactional(readOnly = true)
    public long count() {
        return licensedPlaceRepository.count();
    }
}
