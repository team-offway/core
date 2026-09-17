package com.offway.core.liveactivity.repository;

import com.offway.core.liveactivity.domain.PushToStartToken;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * port 구현(adapter) — Spring Data 에 위임.
 *
 * <p><b>쓰기는 어댑터가 트랜잭션을 연다.</b> {@code @Modifying} 질의는 트랜잭션 없이는 못 돈다.
 * 띄우기 배치는 <b>트랜잭션 밖</b>(외부 호출)에서 돌며 죽은 토큰을 지우므로, 호출자가 트랜잭션을
 * 들고 있으리라 기대할 수 없다. 이미 트랜잭션 안이면 그대로 참여한다.
 */
@Repository
@RequiredArgsConstructor
public class PushToStartTokenRepositoryImpl implements PushToStartTokenRepository {

    private final PushToStartTokenJpaRepository pushToStartTokenJpaRepository;

    @Override
    @Transactional
    public void register(PushToStartToken token) {
        pushToStartTokenJpaRepository.upsert(
                token.getUserId().toString(), token.getToken(), token.getUpdatedAt());
    }

    @Override
    @Transactional
    public int deleteByUserId(UUID userId) {
        return pushToStartTokenJpaRepository.deleteByUserId(userId);
    }

    @Override
    @Transactional
    public int deleteByUserAndToken(UUID userId, String token) {
        return pushToStartTokenJpaRepository.deleteByUserIdAndToken(userId, token);
    }

    @Override
    @Transactional
    public int deleteOthersWithToken(UUID userId, String token) {
        return pushToStartTokenJpaRepository.deleteOthersWithToken(userId, token);
    }

    @Override
    @Transactional
    public int deleteById(long id) {
        return pushToStartTokenJpaRepository.deleteRow(id);
    }

    @Override
    public List<PushToStartToken> findAll() {
        return pushToStartTokenJpaRepository.findAllByOrderByIdAsc();
    }
}
