package com.offway.core.trip.infrastructure.festival;

import com.offway.core.trip.infrastructure.festival.dto.StandardFestivalResult;
import java.time.Duration;

/**
 * 전국문화축제표준데이터 조회 port(#433).
 *
 * <p>지자체 229곳이 같은 스키마로 올린 것을 공공데이터포털이 병합해 전국 단위로 낸다. 그래서 지역별
 * 어댑터 89개를 만들 필요가 없다.
 *
 * <h2>오픈API 가 아니라 파일이다</h2>
 *
 * <p>처음에는 {@code api.data.go.kr/openapi/tn_pubr_public_cltur_fstvl_api} 를 불렀다. 그 주소는
 * 실재하지만(없는 API 는 {@code NO_OPENAPI_SERVICE_ERROR} 를 준다) <b>활용신청할 데이터셋 페이지가
 * 없어</b> 인증키를 등록할 방법이 없었다 — 포털의 오픈API 목록에는 개별 지자체 것만 있다. 그래서
 * 운영에서 계속 {@code SERVICE_KEY_IS_NOT_REGISTERED_ERROR}(403) 였고 축제가 0건이었다.
 *
 * <p>표준데이터 페이지가 내려주는 <b>파일 주소</b>로 옮겼다. <b>인증키가 필요 없고</b> 전량이 한 번에
 * 온다. 원본이 분기 갱신이라 월 1회 배치와도 맞는다.
 *
 * <p>키가 필요 없어졌으므로 로컬에서도 그대로 돈다(로컬 실행성 불변식).
 */
public interface FestivalStandardClient {

    /**
     * 전국 축제 <b>전량</b>.
     *
     * <p>페이지 인자가 없다. 파일이라 한 번에 다 오고, 실측 1,305건이 약 900KB 다.
     *
     * @param maxWait 이 호출을 기다릴 상한 — 호출자에게 남은 시간 예산이다. 구현은 자체 timeout 과
     *     이 값 중 <b>짧은 쪽</b>만 기다린다
     */
    StandardFestivalResult findAll(Duration maxWait);
}
