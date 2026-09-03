package com.offway.core.common.external;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 연동 설정 저장소(#403).
 *
 * <p>같은 패키지의 {@code ExternalApiCallRepository} 와 같은 이유로 엔티티를 두지 않는다 — 행이 키
 * 하나에 값 몇 개뿐이고, upsert 한 줄이 JPA 엔티티 한 벌보다 읽기 쉽다.
 *
 * <p><b>행이 없으면 기본값이다.</b> 안 건드린 연동은 행을 만들지 않는다 — 그래야 "손댄 것" 이
 * 표에서 그대로 드러난다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ExternalApiSettingRepository {

    private final JdbcTemplate jdbcTemplate;

    /** 손댄 연동만. 나머지는 호출자가 {@link ExternalApiSetting#defaultFor} 로 채운다. */
    public Map<ExternalApi, ExternalApiSetting> findApiSettings() {
        Map<ExternalApi, ExternalApiSetting> settings = new EnumMap<>(ExternalApi.class);
        jdbcTemplate.query(
                "SELECT api, cache_enabled, batch_limit FROM external_api_setting",
                rs -> {
                    try {
                        ExternalApi api = ExternalApi.valueOf(rs.getString("api"));
                        int limit = rs.getInt("batch_limit");
                        settings.put(api, new ExternalApiSetting(
                                api, rs.getBoolean("cache_enabled"), rs.wasNull() ? null : limit));
                    } catch (IllegalArgumentException e) {
                        // 코드에서 없어진 API 의 옛 설정. 호출 기록과 같은 이유로 지우지 않고 무시한다.
                        log.warn("모르는 외부 API 설정을 건너뜁니다 api={}", rs.getString("api"));
                    }
                });
        return settings;
    }

    public void save(ExternalApiSetting setting, String updatedBy) {
        jdbcTemplate.update(
                "INSERT INTO external_api_setting (api, cache_enabled, batch_limit, updated_at, updated_by)"
                        + " VALUES (?, ?, ?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE cache_enabled = VALUES(cache_enabled),"
                        + " batch_limit = VALUES(batch_limit), updated_at = VALUES(updated_at),"
                        + " updated_by = VALUES(updated_by)",
                setting.api().name(), setting.cacheEnabled(), setting.batchLimit(),
                LocalDateTime.now(), updatedBy);
    }

    /** 배치 이름 → 켜져 있나. 행이 없는 배치는 켜져 있다. */
    public Map<String, Boolean> findBatchSettings() {
        Map<String, Boolean> settings = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT name, enabled FROM batch_setting ORDER BY name",
                rs -> {
                    settings.put(rs.getString("name"), rs.getBoolean("enabled"));
                });
        return settings;
    }

    public void saveBatch(String name, boolean enabled, String updatedBy) {
        jdbcTemplate.update(
                "INSERT INTO batch_setting (name, enabled, updated_at, updated_by) VALUES (?, ?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE enabled = VALUES(enabled),"
                        + " updated_at = VALUES(updated_at), updated_by = VALUES(updated_by)",
                name, enabled, LocalDateTime.now(), updatedBy);
    }
}
