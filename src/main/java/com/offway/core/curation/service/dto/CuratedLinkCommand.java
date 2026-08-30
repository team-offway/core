package com.offway.core.curation.service.dto;

import com.offway.core.curation.domain.CuratedLink;
import com.offway.core.curation.domain.Surface;
import java.time.LocalDate;
import java.util.Set;
import lombok.Builder;

/**
 * 어드민이 만들거나 고치는 큐레이션 링크 한 건(#342) — 내부 command.
 *
 * <p>생성과 수정이 <b>같은 command</b> 다. 수정이 부분 갱신이 아니라 전체 교체이기 때문이다 — 기간 규칙처럼
 * 여러 필드가 함께 봐야 성립하는 불변식이 있어, 한 필드만 바꾸면 나머지와 어긋난 상태가 만들어진다.
 *
 * <p>값 검증은 여기서 하지 않는다. 도메인 생성자가 최후의 보루이고, 여기서 한 번 더 하면 규칙이 두 곳이 된다.
 *
 * <p><b>빌더로 조립한다.</b> 필드가 열하나이고 그중 문자열이 다섯, 그리고 {@code alwaysOn}·{@code published}
 * 가 붙어 있는 boolean 둘이다 — 뒤바뀌면 만들다 만 항목이 앱에 나가거나 기간이 있는 항목이 영구 노출로
 * 굳는데 컴파일은 통과한다. {@code CuratedLink} 와 같은 이유다.
 */
@Builder
public record CuratedLinkCommand(
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
        boolean published) {

    /** 새 링크로 만든다. 만든 사람도 {@code updatedBy} 에 적는다 — 마지막으로 손댄 사람이 곧 그 사람이다. */
    public CuratedLink toCuratedLink(String updatedBy) {
        return CuratedLink.builder()
                .title(title)
                .chipText(chipText)
                .description(description)
                .linkUrl(linkUrl)
                .thumbnailUrl(thumbnailUrl)
                .startsOn(startsOn)
                .endsOn(endsOn)
                .alwaysOn(alwaysOn)
                .surfaces(surfaces)
                .displayOrder(displayOrder)
                .published(published)
                .updatedBy(updatedBy)
                .build();
    }

    /** 있는 링크에 덮어쓴다. */
    public void applyTo(CuratedLink link, String updatedBy) {
        link.update(title, chipText, description, linkUrl, thumbnailUrl,
                startsOn, endsOn, alwaysOn, surfaces, displayOrder, published, updatedBy);
    }
}
