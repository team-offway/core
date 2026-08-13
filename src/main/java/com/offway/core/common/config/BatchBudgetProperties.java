package com.offway.core.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 적재 배치가 한 회차에 만질 지역 수 — <b>환경마다 달라야 하는 값</b>이라 상수가 아니라 설정으로 둔다(#254).
 *
 * <p><b>왜 필요했나.</b> 로컬과 운영이 같은 data.go.kr 키를 쓰는데, 배치 건너뛰기({@code hasRunSince})는
 * <b>자기 DB 안에서만</b> 중복을 막는다. DB 가 둘이면 각자 "오늘 처음" 이라고 답해 소비가 두 배가 된다.
 * 배치만으로 하루 1,000 콜(개발계정 한도 전부)이 차서, 정작 코스 생성에 쓸 몫이 남지 않았다.
 *
 * <pre>
 *   운영  500 콜/일 (중심관광지 89 + 지역콘텐츠 130 + …)
 *   로컬  500 콜/일 (같은 배치, DB 가 달라 따로 셈)
 *   ───────────────────────────────────────
 *   합계 1,000 콜  =  한도 전부
 * </pre>
 *
 * <p><b>로컬을 끄지 않고 줄인다.</b> {@code @Profile("prod")} 로 아예 막으면 로컬에서 그 기능을 확인할 수
 * 없다 — 이 레포는 지금까지 반대로 택해 왔다({@code HolidayRefreshService} 가 {@code local | prod}).
 * 10곳이면 화면·동선 확인에는 충분하고 소비는 1/9 로 준다.
 *
 * <p>근본 해법은 <b>키 분리와 운영계정</b>이다. 그것이 되면 이 값은 로컬에서도 전체로 되돌리면 된다.
 *
 * @param regionsPerRun 한 회차에 갱신할 지역 수. {@code 0} 이하면 제한 없음(전 지역)
 */
@ConfigurationProperties(prefix = "offway.batch")
public record BatchBudgetProperties(int regionsPerRun) {

    /** 제한 없음을 뜻하는 값 — 운영 기본값이다. */
    private static final int UNLIMITED = 0;

    /**
     * 이 회차에 만질 지역 목록으로 잘라 준다.
     *
     * <p>앞에서부터 자른다. 무작위로 고르면 회차마다 다른 지역이 걸려 로컬에서 "어제 보이던 데이터가
     * 오늘은 없다" 가 된다 — 확인용으로는 같은 지역이 계속 채워지는 편이 낫다.
     */
    public <T> List<T> limit(List<T> regions) {
        if (regionsPerRun <= UNLIMITED || regions.size() <= regionsPerRun) {
            return regions;
        }
        return regions.subList(0, regionsPerRun);
    }

    /** 잘렸는지 — 로그에 남겨 "왜 89곳이 아닌가" 를 묻지 않게 한다. */
    public boolean limits(int total) {
        return regionsPerRun > UNLIMITED && total > regionsPerRun;
    }
}
