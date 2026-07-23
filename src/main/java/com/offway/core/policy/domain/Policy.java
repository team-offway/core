package com.offway.core.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 7대 혜택의 구체 인스턴스. 뱃지 문구·매칭 대상 지역은 {@link PolicyType} 이 소유하고, 여기서는 정책명·상세·기간·대상 등 인스턴스 데이터를
 * 갖는다.
 *
 * <p>{@code verified=false} 는 존재는 확인됐으나 상세·기간이 미확정인 정책 — 노출 여부는 서비스가 판단한다.
 */
@Entity
@Table(name = "policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PolicyType type;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "benefit_detail", length = 500)
    private String benefitDetail;

    @Column(name = "target_audience", length = 200)
    private String targetAudience;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "apply_url", length = 500)
    private String applyUrl;

    @Column(nullable = false)
    private boolean verified;

    /**
     * 주어진 날짜에 이 정책이 유효한가(4축 중 기간 매칭). 시작·종료일이 없으면 상시 유효로 본다.
     */
    public boolean isActiveOn(LocalDate date) {
        if (periodStart != null && date.isBefore(periodStart)) {
            return false;
        }
        return periodEnd == null || !date.isAfter(periodEnd);
    }

    /** 이 정책의 뱃지 문구 (분류가 소유). */
    public String badgeText() {
        return type.badgeText();
    }
}
