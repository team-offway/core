package com.offway.core.common.external;

import java.util.Objects;

/**
 * 연동 하나를 어떻게 굴릴지(#403).
 *
 * <p><b>기본값이 지금 동작이다.</b> 설정 행이 없으면 {@link #defaultFor} 가 돌아오고, 그건 캐시를 쓰고
 * 배치 상한이 없는 상태 — 이 기능이 붙기 전과 같다. 아무것도 안 건드리면 아무것도 안 바뀐다.
 *
 * @param api 대상 연동
 * @param cacheEnabled 인메모리 캐시를 쓸지. 끄면 매번 실호출한다
 * @param batchLimit 배치가 하루에 쓸 수 있는 상한. null 이면 무제한
 */
public record ExternalApiSetting(ExternalApi api, boolean cacheEnabled, Integer batchLimit) {

    public ExternalApiSetting {
        Objects.requireNonNull(api, "api");
        requireValidBatchLimit(api, batchLimit);
    }

    /** 아무것도 안 건드린 상태 — 지금 동작 그대로. */
    public static ExternalApiSetting defaultFor(ExternalApi api) {
        return new ExternalApiSetting(api, true, null);
    }

    /**
     * 배치가 지금 한 번 더 불러도 되나.
     *
     * <p>상한은 <b>그 API 의 오늘 총 사용량</b>과 견준다. 배치만 따로 세지 않는 것은, 우리가 막으려는
     * 것이 "배치가 사용자 몫을 먹는 것" 이기 때문이다 — 사용자가 이미 많이 썼으면 배치는 더 일찍
     * 물러나야 한다.
     */
    public boolean allowsBatch(long usedToday) {
        return batchLimit == null || usedToday < batchLimit;
    }

    /** 기본값에서 벗어났나 — 화면이 "손댄 것" 을 따로 보여줄 수 있게. */
    public boolean isDefault() {
        return cacheEnabled && batchLimit == null;
    }

    /**
     * 상한은 <b>0 이상, 일일 한도 이하</b>다.
     *
     * <p>한도보다 큰 값은 상한이 아니라 무제한과 같은데, 화면에는 제한이 걸린 것처럼 보인다 —
     * 그렇게 조용히 뜻이 다른 값은 안 받는다. 0 은 "배치를 아예 안 돌린다" 라 정당하다.
     */
    private static void requireValidBatchLimit(ExternalApi api, Integer batchLimit) {
        if (batchLimit == null) {
            return;
        }
        if (batchLimit < 0) {
            throw ExternalApiSettingException.invalidBatchLimit();
        }
        if (batchLimit > api.dailyLimit()) {
            throw ExternalApiSettingException.batchLimitOverDailyLimit();
        }
    }
}
