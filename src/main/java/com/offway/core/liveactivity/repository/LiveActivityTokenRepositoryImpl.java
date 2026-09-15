package com.offway.core.liveactivity.repository;

import com.offway.core.liveactivity.domain.LiveActivityToken;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * port 구현(adapter) — Spring Data 에 위임.
 *
 * <p><b>쓰기는 어댑터가 트랜잭션을 연다.</b> {@code @Modifying} 질의는 트랜잭션 없이는 못 돈다.
 * 갱신 배치는 <b>트랜잭션 밖</b>(외부 호출)에서 돌며 죽은 토큰을 지우므로, 호출자가 트랜잭션을 들고
 * 있으리라 기대할 수 없다. 이미 트랜잭션 안이면 그대로 참여하므로 등록 경로의 동작은 달라지지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class LiveActivityTokenRepositoryImpl implements LiveActivityTokenRepository {

    private final LiveActivityTokenJpaRepository liveActivityTokenJpaRepository;

    @Override
    @Transactional
    public void register(LiveActivityToken token) {
        liveActivityTokenJpaRepository.upsert(
                token.getUserId().toString(), token.getCourseId(), token.getToken(), token.getUpdatedAt());
    }

    @Override
    @Transactional
    public int deleteByUserAndCourse(UUID userId, long courseId) {
        return liveActivityTokenJpaRepository.deleteByUserIdAndCourseId(userId, courseId);
    }

    @Override
    @Transactional
    public int deleteByUserId(UUID userId) {
        return liveActivityTokenJpaRepository.deleteByUserId(userId);
    }

    @Override
    @Transactional
    public int deleteById(long id) {
        return liveActivityTokenJpaRepository.deleteRow(id);
    }

    @Override
    public List<LiveActivityToken> findAll() {
        return liveActivityTokenJpaRepository.findAllByOrderByIdAsc();
    }
}
