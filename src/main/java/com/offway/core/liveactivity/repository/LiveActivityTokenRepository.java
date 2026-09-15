package com.offway.core.liveactivity.repository;

import com.offway.core.liveactivity.domain.LiveActivityToken;
import java.util.List;
import java.util.UUID;

/** Live Activity 토큰 저장소 port(#575). 구현은 {@link LiveActivityTokenRepositoryImpl}. */
public interface LiveActivityTokenRepository {

    /**
     * 등록하거나 이미 있으면 토큰을 갈아 끼운다 — <b>몇 번을 불러도 결과가 같다</b>.
     *
     * <p>앱은 네트워크가 끊기면 성공 여부를 모른 채 재시도한다. 그때 행이 늘면 같은 잠금화면에 갱신이
     * 두 번 나간다.
     */
    void register(LiveActivityToken token);

    /** 이 사람의 이 코스 등록을 지운다 — 앱이 카드를 내렸을 때. */
    int deleteByUserAndCourse(UUID userId, long courseId);

    /** 탈퇴 정리 — 이 사람의 등록 전부. */
    int deleteByUserId(UUID userId);

    /**
     * 행 하나를 지운다 — 발송이 {@code 410 Gone} 을 받았거나 여행이 끝났을 때.
     *
     * <p><b>토큰이 아니라 행 id 로 지운다.</b> 토큰에는 유니크 제약이 없어, 토큰으로 지우면 같은 값이
     * 어쩌다 겹친 다른 행까지 함께 사라진다.
     */
    int deleteById(long id);

    /**
     * 갱신 대상 전부.
     *
     * <p><b>상한을 두지 않는다.</b> 이 표에는 <b>지금 잠금화면에 떠 있는 카드</b>만 남는다 — 여행이
     * 끝나거나 토큰이 죽으면 같은 배치가 그 행을 지우므로 스스로 줄어든다. 전체 사용자가 아니라
     * "오늘 여행이 걸려 있는 사람" 이 모수라, 한 번에 읽어도 되는 크기다.
     */
    List<LiveActivityToken> findAll();
}
