package com.offway.core.common.batch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배치가 마지막으로 <b>시작한</b> 시각(#226).
 *
 * <p><b>결과가 아니라 실행을 기록한다.</b> 적재 결과로 "오늘 돌았나" 를 판정하면, 전부 실패한 날에는 아무것도
 * 안 써져서 다음 부팅이 또 전량을 다시 쏜다 — 중심 관광지가 정확히 그랬다(0/89 뒤 재부팅마다 89회).
 * 실행 시각을 따로 남겨야 그 폭주가 멎는다.
 *
 * <p>대신 그날 실패한 것은 그날 안에 다시 시도하지 않는다. 이전 값이 남아 화면은 유지되고, 처음부터 빈
 * 지역만 하루를 기다린다 — 한도를 태워 <b>모두</b>가 실패하는 것보다 낫다고 봤다.
 */
@Entity
@Table(name = "batch_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "last_run_at", nullable = false)
    private LocalDateTime lastRunAt;

    private BatchRun(String name, LocalDateTime lastRunAt) {
        this.name = Objects.requireNonNull(name, "배치 이름이 필요합니다");
        this.lastRunAt = Objects.requireNonNull(lastRunAt, "실행 시각이 필요합니다");
    }

    public static BatchRun startedAt(String name, LocalDateTime lastRunAt) {
        return new BatchRun(name, lastRunAt);
    }

    /** 그 날짜에 시작했는가 — 시각이 아니라 날짜로 본다(하루 한 번이 판정 단위다). */
    public boolean ranOn(LocalDate date) {
        return lastRunAt.toLocalDate().equals(date);
    }

    /** 같은 행을 다시 쓰지 않고 시각만 갱신한다 — 배치당 한 행이 유니크 제약으로 강제된다. */
    public void markStartedAt(LocalDateTime at) {
        this.lastRunAt = Objects.requireNonNull(at, "실행 시각이 필요합니다");
    }
}
