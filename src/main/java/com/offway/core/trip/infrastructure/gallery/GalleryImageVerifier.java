package com.offway.core.trip.infrastructure.gallery;

import java.util.List;
import java.util.Set;

/**
 * 갤러리 이미지 URL이 실제로 살아 있는지 확인하는 port(#196).
 *
 * <p><b>왜 필요한가.</b> 갤러리 API 가 주는 {@code galWebImageUrl} 중 상당수가 죽어 있다 — 실측
 * (2026-08-09) 기준 우리 89곳에 붙은 1,790장 가운데 <b>345장(19.3%)이 404</b> 다. 그대로 두면 지역 카드
 * 15곳에 깨진 이미지가 나갔다.
 *
 * <p>죽은 URL 은 응답 코드로만 알 수 있다. 경로 패턴으로는 못 가른다 — 같은 {@code cms2/website/} 경로에
 * 살아 있는 것과 죽은 것이 섞여 있다.
 */
public interface GalleryImageVerifier {

    /**
     * 살아 있는 URL 만 골라낸다.
     *
     * <p>적재(배경 배치)에서만 부른다 — 요청 경로에서 부르면 카드마다 외부 왕복이 붙는다.
     *
     * @return 살아 있는 것으로 확인된 URL 집합. 확인하지 못한 것은 <b>포함하지 않는다</b>
     */
    Set<String> aliveUrls(List<String> urls);
}
