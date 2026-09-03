package com.offway.core.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 혜택의 구체 인스턴스. 뱃지 문구·매칭 대상 지역은 {@link PolicyType} 이 소유하고, 여기서는 정책명·상세·기간·대상 등 인스턴스 데이터를
 * 갖는다.
 *
 * <p>{@code verified=false} 는 존재는 확인됐으나 상세·기간이 미확정인 정책 — 노출 여부는 서비스가 판단한다.
 */
@Entity
@Table(name = "policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Policy {

    /** 신청 주소가 받는 유일한 스킴. 앱이 웹뷰로 여는 값이라 평문 http 를 받지 않는다. */
    private static final String HTTPS_SCHEME = "https";

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

    /**
     * 기간 보충 문구 — 날짜 두 개로 다 말할 수 없을 때(#217). 없으면 null.
     *
     * <p>반값여행처럼 <b>지자체별로 신청·여행 기간이 다른</b> 정책이 있다. 그렇다고 시작·종료일을 비우면 안 된다 —
     * {@link #isActiveOn} 에서 null 은 "모른다" 가 아니라 "상시" 라, 사업이 끝나도 뱃지가 남는다. 날짜에는
     * 사업 전체의 바깥 경계를 넣어 만료가 걸리게 하고, 그 안의 사정은 이 문구로 말한다.
     *
     * <p>사용자에게 그대로 나가는 값이다.
     */
    @Column(name = "period_note", length = 200)
    private String periodNote;

    @Column(name = "apply_url", length = 500)
    private String applyUrl;

    @Column(nullable = false)
    private boolean verified;

    /**
     * 사람이 마지막으로 출처를 확인한 날(#220).
     *
     * <p>정책은 수동 seed 라 <b>낡는 것이 유일한 실패 모드</b>인데, 언제 확인한 값인지 모르면 낡았는지도
     * 알 수 없다. 이 값이 낡음 감지의 기준이다.
     *
     * <p>이 컬럼이 생기기 전 행은 null 이고 그건 "확인한 적 없음" 이 아니라 "모른다" 다 — 알림은 모르는
     * 것을 낡았다고 단정하지 않는다.
     */
    @Column(name = "checked_on")
    private LocalDate checkedOn;

    /**
     * 마지막으로 고친 어드민(#344).
     *
     * <p><b>seed SQL 시절에는 git blame 이 이 역할을 했다.</b> 배포 없이 값을 고칠 수 있게 되면서 그
     * 기록이 사라지므로, 누가 언제 바꿨는지가 여기 남아야 한다.
     *
     * <p>seed 로 들어온 행은 null 이다 — 사람이 손댄 적이 없다는 뜻이고, 그것도 정보다.
     */
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * 정책 하나를 조립한다 — <b>이름으로만</b>.
     *
     * <p>지금까지 생성 수단이 없었다. seed SQL 이 넣고 JPA 가 읽는 것이 전부라 코드에서 만들 일이 없었고,
     * 그래서 이 값을 다루는 규칙을 검증할 자리도 없었다(#220). 열 칸 중 여섯이 nullable {@code String}·
     * {@code LocalDate} 라 위치로 넘기면 맞바꿔도 컴파일이 통과한다.
     */
    @Builder
    private Policy(
            PolicyType type,
            String name,
            String benefitDetail,
            String targetAudience,
            LocalDate periodStart,
            LocalDate periodEnd,
            String periodNote,
            String applyUrl,
            boolean verified,
            LocalDate checkedOn,
            String updatedBy) {
        apply(type, name, benefitDetail, targetAudience, periodStart, periodEnd,
                periodNote, applyUrl, verified, checkedOn, updatedBy);
    }

    /**
     * 어드민이 고친 값으로 갈아 끼운다(#344) — <b>부분 수정이 아니라 전체 교체</b>다.
     *
     * <p>{@code CuratedLink} 와 같은 판단이다. 기간처럼 <b>여러 필드가 함께 봐야 성립하는 불변식</b>이
     * 있어, 한 필드만 바꾸면 나머지와 어긋난 상태가 만들어진다. 화면도 폼 전체를 들고 있다.
     *
     * <p><b>{@code type} 도 바꿀 수 있다.</b> 잘못 고른 분류를 고치려면 필요하다 — 지우고 다시 만들게
     * 하면 그 사이 뱃지가 사라지고 감사 흔적도 끊긴다. 다만 분류를 바꾸면 <b>대상 지역과 뱃지 문구가
     * 통째로 달라진다</b>({@link PolicyType} 이 소유한다). 화면이 그 사실을 알려야 한다.
     */
    public void update(
            PolicyType type,
            String name,
            String benefitDetail,
            String targetAudience,
            LocalDate periodStart,
            LocalDate periodEnd,
            String periodNote,
            String applyUrl,
            boolean verified,
            LocalDate checkedOn,
            String updatedBy) {
        apply(type, name, benefitDetail, targetAudience, periodStart, periodEnd,
                periodNote, applyUrl, verified, checkedOn, updatedBy);
    }

    /**
     * 만들 때와 고칠 때가 <b>같은 코드를 탄다.</b> 두 벌로 두면 한쪽에만 규칙을 더하게 되고, 그러면
     * 어드민이 저장 한 번으로 불변식을 우회한다.
     */
    private void apply(
            PolicyType type,
            String name,
            String benefitDetail,
            String targetAudience,
            LocalDate periodStart,
            LocalDate periodEnd,
            String periodNote,
            String applyUrl,
            boolean verified,
            LocalDate checkedOn,
            String updatedBy) {
        this.type = Objects.requireNonNull(type, "정책 분류는 null 일 수 없습니다.");
        this.name = requireText(name, "정책명");
        this.benefitDetail = blankToNull(benefitDetail);
        this.targetAudience = blankToNull(targetAudience);
        requirePeriod(periodStart, periodEnd);
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.periodNote = blankToNull(periodNote);
        this.applyUrl = requireHttpsOrNull(applyUrl);
        this.verified = verified;
        this.checkedOn = checkedOn;
        this.updatedBy = blankToNull(updatedBy);
    }

    /**
     * 주어진 날짜에 이 정책이 유효한가(4축 중 기간 매칭). 시작·종료일이 없으면 상시 유효로 본다.
     */
    public boolean isActiveOn(LocalDate date) {
        if (periodStart != null && date.isBefore(periodStart)) {
            return false;
        }
        return periodEnd == null || !date.isAfter(periodEnd);
    }

    /**
     * 이 정책의 노출 기간이 다른 정책과 겹치는가(#344).
     *
     * <p>같은 분류의 정책이 둘 다 유효하면 <b>앱에 같은 뱃지가 두 개 뜬다</b> — 뱃지 문구는
     * {@link PolicyType} 이 소유해서 두 행의 문구가 글자까지 같다. 그래서 겹침 판정에 분류는 보지
     * 않는다(호출자가 같은 분류끼리만 묻는다). 여기가 답하는 것은 <b>기간</b>뿐이다.
     *
     * <p>날짜가 없는 쪽은 <b>상시</b>라 무엇과도 겹친다 — {@link #isActiveOn} 이 그렇게 읽기 때문이다.
     */
    public boolean periodOverlaps(LocalDate otherStart, LocalDate otherEnd) {
        boolean endsBeforeOther = periodEnd != null && otherStart != null && periodEnd.isBefore(otherStart);
        boolean startsAfterOther = periodStart != null && otherEnd != null && periodStart.isAfter(otherEnd);
        return !endsBeforeOther && !startsAfterOther;
    }

    /** 이 정책의 뱃지 문구 (분류가 소유). */
    public String badgeText() {
        return type.badgeText();
    }

    /**
     * 시작이 종료보다 늦을 수 없다.
     *
     * <p>거꾸로 넣으면 {@link #isActiveOn} 이 <b>어떤 날짜에도 참이 아니어서</b> 뱃지가 영영 안 뜬다.
     * 저장은 성공하고 화면에도 값이 그대로 보이므로, 막지 않으면 "등록했는데 왜 안 나오지" 가 된다.
     *
     * <p>한쪽만 있는 것은 정상이다 — 시작만 있으면 "그날부터 상시", 종료만 있으면 "그날까지" 다.
     */
    private static void requirePeriod(LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart != null && periodEnd != null && periodStart.isAfter(periodEnd)) {
            throw PolicyException.invalidPeriod();
        }
    }

    /**
     * 신청 주소는 <b>{@code https} 만</b> 받는다 — 앱이 웹뷰로 여는 값이다(#345).
     *
     * <p>{@code CuratedLink.requireHttps} 와 같은 이유로 <b>접두사 비교가 아니라 파싱</b>이다. 앞자리만
     * 보면 호스트 없는 {@code https://} 가 통과하고, 대문자 {@code HTTPS://} 는 거절된다 — 스킴은
     * 대소문자를 가리지 않는다.
     *
     * <p>비어 있어도 된다. 신청 페이지가 없는 정책이 있고, 그때 뱃지는 눌리지 않는 안내로 남는다.
     */
    private static String requireHttpsOrNull(String applyUrl) {
        String value = blankToNull(applyUrl);
        if (value == null) {
            return null;
        }
        URI uri = parse(value);
        if (!HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
            throw PolicyException.insecureApplyUrl();
        }
        return value;
    }

    private static URI parse(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException e) {
            throw PolicyException.insecureApplyUrl();
        }
    }

    private static String requireText(String value, String name) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(name + "은(는) 비어 있을 수 없습니다.");
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
