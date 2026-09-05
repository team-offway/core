package com.offway.core.common.external;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
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

    /**
     * 이 호출을 <b>누가</b> 일으켰는지 한 건 더한다(#285).
     *
     * <p>총량과 테이블을 나눈 이유는 마이그레이션 주석에 있다 — {@code external_api_call} 의 PK 를 넓히면
     * {@code notified_step} 이 주체별로 쪼개져 같은 10% 단계를 주체 수만큼 알린다.
     *
     * <p>PK 가 (날짜, API, 주체)라 같은 주체가 몇 번을 불러도 <b>행은 하나</b>고 카운트만 오른다. 하루 최대
     * 11 API × 주체 25 로 유한하다.
     */
    public void recordCaller(ExternalApi api, LocalDate date, Caller caller) {
        jdbcTemplate.update(
                "INSERT INTO external_api_call_caller (call_date, api, caller, call_count) VALUES (?, ?, ?, 1)"
                        + " ON DUPLICATE KEY UPDATE call_count = call_count + 1",
                date, api.name(), caller.name());
    }

    /** 그날 <b>그 API</b> 를 주체별로 얼마나 썼나. 많이 쓴 순이라 호출자가 다시 정렬하지 않는다. */
    public Map<String, Long> callerCountsOn(ExternalApi api, LocalDate date) {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT caller, call_count FROM external_api_call_caller"
                        + " WHERE call_date = ? AND api = ? ORDER BY call_count DESC, caller",
                // 블록으로 둔다 — 식 람다는 put 의 반환값 탓에 ResultSetExtractor 와 겹쳐 컴파일이 안 된다.
                rs -> {
                    counts.put(rs.getString("caller"), rs.getLong("call_count"));
                },
                date, api.name());
        return counts;
    }

    /** 그날 전체를 API → (주체 → 호출 수)로. 한 번도 안 부른 API 는 키가 없다. */
    public Map<ExternalApi, Map<String, Long>> callerCountsOn(LocalDate date) {
        Map<ExternalApi, Map<String, Long>> counts = new EnumMap<>(ExternalApi.class);
        jdbcTemplate.query(
                "SELECT api, caller, call_count FROM external_api_call_caller"
                        + " WHERE call_date = ? ORDER BY call_count DESC, caller",
                rs -> {
                    try {
                        counts.computeIfAbsent(ExternalApi.valueOf(rs.getString("api")), key -> new LinkedHashMap<>())
                                .put(rs.getString("caller"), rs.getLong("call_count"));
                    } catch (IllegalArgumentException ignored) {
                        // 코드에서 없어진 API 의 옛 기록. countsOn 과 같은 이유로 무시한다.
                    }
                }, date);
        return counts;
    }

    /**
     * 기간의 <b>날짜별 × API별</b> 호출 수(#398). 날짜 오름차순이라 화면이 그대로 그린다.
     *
     * <p>하루치만으로는 판단이 안 된다 — 9/1 은 매월 1일이라 월배치가 겹쳐 700콜이었는데, 그 숫자를
     * 평상시로 알고 계산했다가 틀렸다. <b>월배치가 튀는 날이 보여야 그래프가 읽힌다.</b>
     *
     * <p>한 번도 안 부른 (날짜, API) 는 키가 없다. 0 을 채우는 것은 화면의 일이다 — 여기서 채우면
     * "안 불렀다" 와 "그날 기록 자체가 없다" 가 구분되지 않는다.
     */
    public Map<LocalDate, Map<ExternalApi, Long>> countsBetween(LocalDate from, LocalDate to) {
        Map<LocalDate, Map<ExternalApi, Long>> counts = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT call_date, api, call_count FROM external_api_call"
                        + " WHERE call_date BETWEEN ? AND ? ORDER BY call_date, api",
                rs -> {
                    try {
                        counts.computeIfAbsent(rs.getObject("call_date", LocalDate.class),
                                        key -> new EnumMap<>(ExternalApi.class))
                                .put(ExternalApi.valueOf(rs.getString("api")), rs.getLong("call_count"));
                    } catch (IllegalArgumentException ignored) {
                        // 코드에서 없어진 API 의 옛 기록. countsOn 과 같은 이유로 무시한다.
                    }
                }, from, to);
        return counts;
    }

    /**
     * 기간 <b>합계</b>를 API → (주체 → 호출 수)로(#398).
     *
     * <p>이 축이 <b>배치와 사용자 요청을 가른다.</b> 총량만 보면 "우리가 API 를 쓴다" 는 것밖에 모르는데,
     * 정작 보여야 하는 것은 <b>서비스가 요청마다 실제로 부른다</b>는 쪽이다.
     */
    public Map<ExternalApi, Map<String, Long>> callerCountsBetween(LocalDate from, LocalDate to) {
        Map<ExternalApi, Map<String, Long>> counts = new EnumMap<>(ExternalApi.class);
        jdbcTemplate.query(
                "SELECT api, caller, SUM(call_count) AS total FROM external_api_call_caller"
                        + " WHERE call_date BETWEEN ? AND ? GROUP BY api, caller"
                        + " ORDER BY total DESC, caller",
                rs -> {
                    try {
                        counts.computeIfAbsent(ExternalApi.valueOf(rs.getString("api")),
                                        key -> new LinkedHashMap<>())
                                .put(rs.getString("caller"), rs.getLong("total"));
                    } catch (IllegalArgumentException ignored) {
                        // 코드에서 없어진 API 의 옛 기록.
                    }
                }, from, to);
        return counts;
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
