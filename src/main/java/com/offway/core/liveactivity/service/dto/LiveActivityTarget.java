package com.offway.core.liveactivity.service.dto;

import com.offway.core.liveactivity.domain.TripProgress;
import com.offway.core.liveactivity.infrastructure.apns.LiveActivityPush;
import java.time.LocalDate;
import java.util.Objects;
import lombok.Builder;

/**
 * 오늘 갱신할 잠금화면 카드 하나(#575).
 *
 * <p>엔티티를 그대로 넘기지 않는다. 발송은 <b>트랜잭션 밖</b>에서 일어나므로 그때 엔티티는 이미
 * 준영속이고, 무심코 연관을 건드리면 그 자리에서 터진다. 발송에 실제로 필요한 것만 담는다.
 *
 * @param rowId 등록 행의 id. <b>토큰이 아니라 이 값으로 지운다</b> — 토큰에는 유니크 제약이 없어,
 *     토큰으로 지우면 값이 어쩌다 겹친 다른 행까지 함께 사라진다
 * @param token 보낼 주소. 로그에 남기지 않는다
 * @param courseId 어느 코스인가 — 로그에서 사람 대신 가리킬 수 있는 유일한 값이다
 * @param progress 오늘 기준 진행 상태. <b>끝났으면 갱신이 아니라 종료</b>다
 * @param regionName 여행지 이름. <b>못 찾으면 null</b> 이고, 그때는 앱이 이름 없이 그린다
 * @param startDate 출발일. 여행이 끝난 카드에서는 null 일 수 있다
 * @param endDate 종료일. 여행이 끝난 카드에서는 null 일 수 있다
 */
@Builder
public record LiveActivityTarget(
        long rowId,
        String token,
        Long courseId,
        TripProgress progress,
        String regionName,
        LocalDate startDate,
        LocalDate endDate) {

    public LiveActivityTarget {
        Objects.requireNonNull(token, "토큰은 null 일 수 없습니다.");
        Objects.requireNonNull(progress, "진행 상태는 null 일 수 없습니다.");
    }

    /**
     * 실어 보낼 내용 — 매핑은 이 dto 자신이 한다(별도 Mapper 를 두지 않는다).
     *
     * <p>끝난 여행은 <b>종료</b>다. 여기서 가르지 않고 갱신을 보내면 끝난 D-day 가 잠금화면에 그대로
     * 남는다.
     */
    public LiveActivityPush toPush() {
        if (progress.ended()) {
            return LiveActivityPush.END;
        }
        return new LiveActivityPush.Update(
                regionName, progress.daysLeft(), progress.dayNth(), startDate, endDate);
    }

    /** 보내고 나면 이 행을 지워야 하는가 — 끝난 여행의 등록은 남겨 둘 이유가 없다. */
    public boolean doneAfterSend() {
        return progress.ended();
    }
}
