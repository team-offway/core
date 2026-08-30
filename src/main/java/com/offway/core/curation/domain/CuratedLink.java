package com.offway.core.curation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 앱에서 외부 페이지로 나가는 창구(#341) — 도 관광포털·국토교통부·지역 축제처럼 우리가 데이터를 다 갖지 못하는 것들.
 *
 * <p>칩을 누르면 웹뷰로 그 사이트가 열린다. {@code policy} 에 넣지 않는 이유는 그쪽이 <b>"직접 신청해 받는
 * 혜택"</b> 만 담기로 한 자리이기 때문이다(#217·#340).
 *
 * <h2>불변식 셋을 생성자에서 지킨다</h2>
 *
 * <p>이 값들은 어드민이 손으로 넣는다. <b>입력 실수가 앱 화면에 그대로 굳는 자리</b>라, 서비스가 아니라
 * 도메인이 막는다 — 나중에 백오피스(#342)든 seed SQL 이든 어느 길로 들어와도 같은 규칙을 받는다.
 */
@Entity
@Table(name = "curated_link")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CuratedLink {

    /** 칩에 들어가는 문구라 길면 잘린다. 화면이 감당하는 길이를 도메인이 안다. */
    public static final int MAX_CHIP_TEXT_LENGTH = 30;

    /** RFC 3986 의 scheme 은 대소문자를 구분하지 않는다 — 비교도 그렇게 한다. */
    private static final String HTTPS_SCHEME = "https";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    /** 칩 문구 — 사용자가 목록에서 처음 읽는 한 줄이다. */
    @Column(name = "chip_text", nullable = false, length = MAX_CHIP_TEXT_LENGTH)
    private String chipText;

    @Column(length = 500)
    private String description;

    /** 웹뷰로 열 주소. {@code https} 만 받는다 — 아래 {@link #requireHttps} 참고. */
    @Column(name = "link_url", nullable = false, length = 1000)
    private String linkUrl;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @Column(name = "starts_on")
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    /**
     * 상시 노출인가 — <b>날짜를 비운 것과 구분하려고 따로 둔다.</b>
     *
     * <p>{@code policy} 가 이미 덴 자리다(#217). 거기서는 기간을 NULL 로 두면 {@code isActiveOn} 이
     * <b>"상시"</b> 로 읽어, 사업이 끝나도 뱃지가 영영 남았다. NULL 이 "모른다" 인지 "상시" 인지 값만 보고
     * 알 수 없는 것이 원인이었다.
     *
     * <p>그래서 상시를 <b>명시적 플래그</b>로 받고, 끄면 종료일을 반드시 요구한다.
     */
    @Column(name = "always_on", nullable = false)
    private boolean alwaysOn;

    /** 어느 화면에 내릴지. {@code "HOME,REGION"} 처럼 쉼표로 잇는다({@link Surface}). */
    @Column(nullable = false, length = 100)
    private String surfaces;

    /** 같은 면 안에서의 정렬 — 작을수록 앞이다. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /**
     * 앱에 내릴지 — <b>기본은 안 내린다.</b>
     *
     * <p>어드민이 만들다 만 항목이 곧바로 사용자에게 보이면 안 된다. 켜는 것은 명시적 행위여야 한다.
     */
    @Column(nullable = false)
    private boolean published;

    /**
     * 마지막으로 고친 어드민의 label(#342).
     *
     * <p>배포 없이 값을 고칠 수 있게 되면 <b>누가 언제 바꿨는지가 유일한 추적 수단</b>이 된다. seed SQL
     * 시절에는 git blame 이 그 역할을 했다.
     *
     * <p>seed 로 들어온 행은 null 이다 — 사람이 고친 적이 없다는 뜻이고, 그것도 정보다.
     */
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    /**
     * <b>빌더로 연다.</b> 처음에는 검증이 본체라는 이유로 static 팩토리를 뒀는데, 그건 빌더와 배타적이지
     * 않다 — Lombok 빌더는 이 생성자를 그대로 호출하므로 검증이 똑같이 돈다. 반면 위치 인수는 실제로
     * 위험하다: 인자 열하나 중 문자열이 다섯이고, <b>{@code alwaysOn} 과 {@code published} 가 붙어 있는
     * boolean 둘</b>이다. 이 둘이 뒤바뀌면 만들다 만 항목이 앱에 나가거나 기간이 있는 항목이 영구 노출로
     * 굳는데, 컴파일은 통과한다.
     */
    @Builder
    private CuratedLink(
            String title,
            String chipText,
            String description,
            String linkUrl,
            String thumbnailUrl,
            LocalDate startsOn,
            LocalDate endsOn,
            boolean alwaysOn,
            Set<Surface> surfaces,
            int displayOrder,
            boolean published,
            String updatedBy) {
        apply(title, chipText, description, linkUrl, thumbnailUrl,
                startsOn, endsOn, alwaysOn, surfaces, displayOrder, published, updatedBy);
    }

    /**
     * 어드민이 고친 값으로 갈아 끼운다(#342) — <b>부분 수정이 아니라 전체 교체</b>다.
     *
     * <p>필드별로 "보낸 것만 바꾸는" 방식을 쓰지 않는다. 기간 규칙(상시가 아니면 종료일 필수)처럼
     * <b>여러 필드가 함께 봐야 성립하는 불변식</b>이 있어, 한 필드만 바꾸면 나머지와 어긋난 상태가
     * 만들어진다. 화면도 폼 전체를 들고 있으므로 전부 받는 편이 자연스럽다.
     *
     * <p>검증은 생성자와 <b>같은 코드</b>를 탄다({@code apply}). 만들 때는 막고 고칠 때는 통과하는 값이
     * 생기면, 어드민이 저장 한 번으로 불변식을 우회하게 된다.
     */
    public void update(
            String title,
            String chipText,
            String description,
            String linkUrl,
            String thumbnailUrl,
            LocalDate startsOn,
            LocalDate endsOn,
            boolean alwaysOn,
            Set<Surface> surfaces,
            int displayOrder,
            boolean published,
            String updatedBy) {
        apply(title, chipText, description, linkUrl, thumbnailUrl,
                startsOn, endsOn, alwaysOn, surfaces, displayOrder, published, updatedBy);
    }

    /**
     * 만들 때와 고칠 때가 <b>같은 코드를 탄다.</b> 두 벌로 두면 한쪽에만 규칙을 더하게 되고, 그러면
     * 어드민이 저장 한 번으로 불변식을 우회한다.
     */
    private void apply(
            String title,
            String chipText,
            String description,
            String linkUrl,
            String thumbnailUrl,
            LocalDate startsOn,
            LocalDate endsOn,
            boolean alwaysOn,
            Set<Surface> surfaces,
            int displayOrder,
            boolean published,
            String updatedBy) {
        this.title = requireText(title, "제목");
        this.chipText = requireChipText(chipText);
        this.description = blankToNull(description);
        this.linkUrl = requireHttps(linkUrl);
        this.thumbnailUrl = requireHttpsOrNull(thumbnailUrl);
        requirePeriod(alwaysOn, startsOn, endsOn);
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.alwaysOn = alwaysOn;
        this.surfaces = requireSurfaces(surfaces);
        this.displayOrder = displayOrder;
        this.published = published;
        this.updatedBy = blankToNull(updatedBy);
    }

    /** 이 링크가 오늘 그 면에 나가는가 — 게시됐고, 기간 안이고, 그 면이 켜져 있어야 한다. */
    public boolean visibleOn(Surface surface, LocalDate today) {
        return published && activeOn(today) && surfacesOf().contains(surface);
    }

    /**
     * 오늘이 노출 기간 안인가.
     *
     * <p>{@code alwaysOn} 이면 날짜를 보지 않는다. 아니면 시작일 이상·종료일 이하이고, 시작일이 비어 있으면
     * "언제 시작했는지 모르지만 이미 시작했다" 로 읽는다 — 종료일은 생성자가 이미 요구했다.
     */
    public boolean activeOn(LocalDate today) {
        if (alwaysOn) {
            return true;
        }
        if (startsOn != null && today.isBefore(startsOn)) {
            return false;
        }
        return endsOn == null || !today.isAfter(endsOn);
    }

    public Set<Surface> surfacesOf() {
        return Surface.parse(surfaces);
    }

    /**
     * 웹뷰가 임의 주소를 여는 통로가 된다. 등록자가 개발진뿐이라 위험은 낮지만, <b>오타 하나가 앱에서
     * 엉뚱한 페이지를 여는 것</b>은 막아야 한다. {@code http}·{@code javascript:} 등을 스킴에서 끊는다.
     *
     * <p><b>접두어 비교가 아니라 {@link URI} 로 판정한다.</b> {@code startsWith("https://")} 는 두 방향으로
     * 틀렸다 — 호스트가 없는 {@code https://} 나 {@code https:///path} 를 통과시키고(앱이 눌러도 아무 데도
     * 못 간다), 반대로 스킴 대소문자를 구분해 <b>정상 주소인</b> {@code HTTPS://…} 를 거절했다. scheme 은
     * RFC 3986 에서 대소문자를 구분하지 않는다.
     *
     * <p>파싱 자체가 안 되는 값도 거절한다. 공백이나 인코딩 안 된 문자가 섞인 주소는 웹뷰에서 어차피
     * 안 열리므로, 등록 시점에 돌려보내는 편이 낫다.
     */
    private static String requireHttps(String url) {
        String value = requireText(url, "링크 주소");
        URI uri = parse(value);
        if (!HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme()) || isBlank(uri.getHost())) {
            throw CurationException.insecureLinkUrl();
        }
        return value;
    }

    private static URI parse(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            throw CurationException.insecureLinkUrl();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String requireHttpsOrNull(String url) {
        String value = blankToNull(url);
        return value == null ? null : requireHttps(value);
    }

    /**
     * 상시가 아니면 <b>종료일을 반드시 받는다.</b> 없으면 저장을 거절한다 — 어드민이 깜빡한 것이 영구 노출로
     * 굳는 것을 막는다(#217 의 교훈). 시작일은 없어도 된다(이미 시작한 것).
     */
    private static void requirePeriod(boolean alwaysOn, LocalDate startsOn, LocalDate endsOn) {
        if (!alwaysOn && endsOn == null) {
            throw CurationException.endDateRequired();
        }
        if (startsOn != null && endsOn != null && endsOn.isBefore(startsOn)) {
            throw CurationException.periodReversed();
        }
    }

    private static String requireChipText(String chipText) {
        String value = requireText(chipText, "칩 문구");
        if (value.length() > MAX_CHIP_TEXT_LENGTH) {
            throw CurationException.chipTextTooLong();
        }
        return value;
    }

    private static String requireSurfaces(Set<Surface> surfaces) {
        if (surfaces == null || surfaces.isEmpty()) {
            throw CurationException.surfaceRequired();
        }
        return Surface.join(surfaces);
    }

    /** 서비스가 보장해야 할 값이 비면 코드 버그다 — 계약 위반과 달리 500 으로 드러나야 한다. */
    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + "은(는) 필수입니다");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + "은(는) 비어 있을 수 없습니다");
        }
        return value.strip();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.strip();
    }
}
