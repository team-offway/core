package com.offway.core.liveactivity.service.dto;

import com.offway.core.liveactivity.domain.TripProgress;
import com.offway.core.liveactivity.infrastructure.apns.LiveActivityPush;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;

/**
 * 오늘 카드를 띄울(또는 이미 떠 있으면 갱신할) 기기 하나(#583).
 *
 * <p><b>토큰을 둘 들고 있다.</b> 그게 이 dto 의 요점이다 — 같은 기기·같은 코스인데 카드가 이미
 * 떠 있으면 갱신 토큰으로 <b>update</b> 를, 없으면 띄우기 토큰으로 <b>start</b> 를 보낸다. 무엇을
 * 보낼지가 이 둘 중 무엇이 있느냐로 갈린다.
 *
 * @param pushToStartRowId 띄우기 토큰 행 id. {@code 410} 을 받으면 이 행을 지운다
 * @param pushToStartToken 그 기기에 카드를 <b>만들</b> 수 있는 주소
 * @param updateRowId 이미 떠 있는 카드의 갱신 토큰 행 id. 없으면 {@code null}
 * @param updateToken 이미 떠 있는 카드의 갱신 주소. 없으면 {@code null}
 * @param courseId 어느 코스인가 — 카드의 attributes 에 실리고, 로그에서 사람 대신 가리키는 값이다
 * @param progress 오늘 기준 진행 상태
 * @param regionName 여행지 이름. <b>못 찾으면 null</b> 이고, 그때는 앱이 이름 없이 그린다
 * @param startDate 출발일
 * @param endDate 종료일
 */
@Builder
public record LiveActivityStartTarget(
        long pushToStartRowId,
        String pushToStartToken,
        Long updateRowId,
        String updateToken,
        long courseId,
        TripProgress progress,
        String regionName,
        LocalDate startDate,
        LocalDate endDate) {

    public LiveActivityStartTarget {
        Objects.requireNonNull(pushToStartToken, "띄우기 토큰은 null 일 수 없습니다.");
        Objects.requireNonNull(progress, "진행 상태는 null 일 수 없습니다.");
    }

    /** 이미 떠 있는 카드가 있나 — 있으면 띄우지 않고 갱신한다. */
    public boolean alreadyShowing() {
        return updateToken != null;
    }

    public Optional<Long> updateRow() {
        return Optional.ofNullable(updateRowId);
    }

    /** 카드를 새로 만드는 내용. */
    public LiveActivityPush start() {
        return new LiveActivityPush.Start(courseId, state());
    }

    /** 이미 떠 있는 카드를 고치는 내용 — {@link #start()} 와 <b>같은 다섯 칸</b>이다. */
    public LiveActivityPush update() {
        return state();
    }

    private LiveActivityPush.Update state() {
        return new LiveActivityPush.Update(
                regionName, progress.daysLeft(), progress.dayNth(), startDate, endDate);
    }
}
