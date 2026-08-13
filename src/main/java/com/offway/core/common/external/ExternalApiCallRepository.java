package com.offway.core.common.external;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 오늘자 외부 API 호출 수(#123).
 *
 * <p>엔티티를 두지 않는다 — 행이 (날짜, API) 조합뿐이고 도메인 규칙이 없다. upsert 한 줄이 JPA 엔티티 한 벌보다
 * 읽기 쉽고, 동시 호출에서도 DB 가 원자적으로 더해준다.
 */
@Repository
@RequiredArgsConstructor
public class ExternalApiCallRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 한 건 기록하고 <b>오늘 누적</b>을 돌려준다.
     *
     * <p>더한 뒤 값을 다시 읽는다. 애플리케이션에서 세면 인스턴스가 여럿일 때 각자 다른 숫자를 보게 되는데,
     * 그러면 "70% 넘었다" 경고가 실제보다 늦게 뜬다.
     */
    public long recordAndCount(ExternalApi api, LocalDate date) {
        jdbcTemplate.update(
                "INSERT INTO external_api_call (call_date, api, call_count) VALUES (?, ?, 1)"
                        + " ON DUPLICATE KEY UPDATE call_count = call_count + 1",
                date, api.name());
        Long count = jdbcTemplate.queryForObject(
                "SELECT call_count FROM external_api_call WHERE call_date = ? AND api = ?",
                Long.class, date, api.name());
        return count == null ? 0 : count;
    }

    /**
     * 알림 단계를 <b>선점</b>한다 — 성공한 쪽만 알림을 보낸다(#257).
     *
     * <p>조건부 UPDATE 한 문장이 판정과 기록을 함께 한다. 읽고 나서 쓰면 그 사이에 다른 인스턴스가 같은
     * 단계를 읽어 <b>둘 다 보낸다</b>. DB 가 행 잠금으로 갈라주게 두면 인스턴스가 몇이든 한 번만 나간다.
     *
     * @return 이번 호출이 그 단계를 처음 넘겼으면 {@code true}. 이미 알린 단계면 {@code false}
     */
    public boolean claimNotifyStep(ExternalApi api, LocalDate date, int step) {
        int updated = jdbcTemplate.update(
                "UPDATE external_api_call SET notified_step = ?"
                        + " WHERE call_date = ? AND api = ? AND notified_step < ?",
                step, date, api.name(), step);
        return updated > 0;
    }

    /** 그날의 API 별 호출 수. 한 번도 안 부른 API 는 키가 없다. */
    public Map<ExternalApi, Long> countsOn(LocalDate date) {
        Map<ExternalApi, Long> counts = new EnumMap<>(ExternalApi.class);
        jdbcTemplate.query("SELECT api, call_count FROM external_api_call WHERE call_date = ?",
                rs -> {
                    try {
                        counts.put(ExternalApi.valueOf(rs.getString("api")), rs.getLong("call_count"));
                    } catch (IllegalArgumentException ignored) {
                        // 코드에서 없어진 API 의 옛 기록. 지우지 않고 무시한다 — 과거 집계는 남겨 둔다.
                    }
                }, date);
        return counts;
    }
}
