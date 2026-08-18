package com.offway.core.user.event;

import java.util.Optional;
import java.util.UUID;

/**
 * 사용자가 탈퇴했다. 각 도메인이 <b>자기 테이블의 그 사람 데이터를 스스로 지운다.</b>
 *
 * <p>이벤트로 만든 이유는 의존 방향이다. {@code user} 가 {@code itinerary}·{@code leave} 의 서비스를 직접 부르면
 * 사용자 도메인이 "지울 것이 무엇무엇인지" 를 전부 알아야 하고, 도메인이 하나 늘 때마다 이 목록을 고쳐야 한다.
 * 각 도메인이 자기 데이터를 지우는 편이 그 도메인의 스키마를 아는 쪽과 지우는 쪽을 일치시킨다.
 *
 * <p><b>리스너는 동기(같은 트랜잭션)로 돈다.</b> 커밋 뒤에 비동기로 지우면 사용자 행은 사라졌는데 코스·연차가
 * 남는 상태가 생기고, 그건 소유자가 없어 <b>다시는 지울 수 없는</b> 데이터가 된다. 개인정보를 지우는 작업이라
 * 부분 성공을 허용하지 않는다 — 하나라도 실패하면 탈퇴 전체를 롤백하고 사용자에게 실패를 알린다.
 *
 * @param userId 탈퇴한 사용자
 * @param guestId 이 사용자가 써 온 게스트 식별자. <b>없을 수 있다.</b> 코스·연차는 아직 {@code guest_id} 로
 *     묶여 있어(소유 전환 미이행) 이 값이 없으면 그 데이터에 닿지 못한다
 */
public record UserWithdrawn(UUID userId, String guestId) {

    /** 게스트 키로 묶인 데이터를 지울 수 있는 경우에만 값이 있다. */
    public Optional<String> guestIdIfPresent() {
        return Optional.ofNullable(guestId).filter(value -> !value.isBlank());
    }
}
