package com.offway.core.trip.infrastructure.pet;

import com.offway.core.trip.infrastructure.pet.dto.PetTourDetail;
import com.offway.core.trip.infrastructure.pet.dto.PetTourResult;
import java.time.Duration;
import java.util.Optional;

/**
 * 반려동물 동반여행 조회 port(#566). 도메인·서비스는 이 인터페이스에만 의존한다.
 */
public interface PetTourClient {

    /**
     * 전국 반려동반 장소를 <b>한 번에</b> 받는다.
     *
     * <p>실측 9,679건 · 6.3MB · 2.8초다(2026-09-13). 페이지를 나누지 않는 이유는
     * {@link PetTourResult} 에 적었다.
     *
     * @param maxWait 이 조회 전체의 시간 상한
     * @return 매칭할 수 있는 장소(콘텐츠 ID·이름·법정동 코드를 아는 것만). 키가 없으면 빈 결과
     */
    PetTourResult findAll(Duration maxWait);

    /**
     * 장소 하나의 동반 조건·유의사항·이용 가능 시설.
     *
     * <p><b>장소마다 한 콜이다.</b> 목록과 달리 묶어 받을 수 없어, 호출자가 동시성 상한을 둔 병렬로
     * 부른다 — 442건을 순차로 돌면 지연이 442배가 된다.
     *
     * @return 못 받았으면 empty. <b>빈 상세와 구분된다</b> — 조회 실패는 다음 회차에 다시 받아야 하고,
     *     조건이 비어 온 것은 원본이 그렇게 준 것이다
     */
    Optional<PetTourDetail> findDetail(String contentId, Duration maxWait);
}
