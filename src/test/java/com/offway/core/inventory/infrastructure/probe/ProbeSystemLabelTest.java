package com.offway.core.inventory.infrastructure.probe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 프로브가 자기 시스템을 어떤 이름으로 부르는가(#479).
 *
 * <p>이 이름으로 장애 알림이 나가고 어드민 표가 그려진다. <b>이름이 뭉개지면 "어느 API 가 죽었나" 에
 * 답할 수 없다</b> — 그게 이 관측의 존재 이유인데도.
 *
 * <p>Spring 없이 만든다. {@code system()} 은 상수 URL 에서 라벨을 도출할 뿐이라 협력자가 필요 없고,
 * 그 사실 자체가 이 값이 호출 결과에 좌우되지 않는다는 뜻이다.
 */
class ProbeSystemLabelTest {

    /** 라벨이 이 값으로 떨어지면 경로 매핑이 빠진 것이다 — 그러면 아래 API 들이 한 덩어리가 된다. */
    private static final String DATA_GO_KR_HOST = "apis.data.go.kr";

    private static List<ExternalApiProbe> probes() {
        // 생성자는 필드 대입만 한다. system() 은 외부를 부르지 않으므로 협력자가 없어도 된다.
        return Stream.<ExternalApiProbe>of(
                        new TourApiProbe(null, null),
                        new TourDataLabProbe(null, null),
                        new HolidayProbe(null, null),
                        new TagoProbe(null, null),
                        new KorailProbe(null, null),
                        new TmapProbe(null, null))
                .toList();
    }

    /**
     * <b>프로브마다 다른 이름이어야 한다.</b>
     *
     * <p>여섯 중 다섯이 같은 호스트({@code apis.data.go.kr})를 쓴다. 경로로 가르지 못하면 전부 한
     * 이름이 되고, 관광정보가 죽었는데 "공공데이터포털 장애" 라고만 알리게 된다 — 그 말로는 무엇을
     * 해야 할지 알 수 없다.
     */
    @Test
    void 프로브마다_시스템_이름이_갈린다() {
        List<String> labels = probes().stream().map(ExternalApiProbe::system).toList();

        Set<String> distinct = new HashSet<>(labels);
        assertEquals(labels.size(), distinct.size(),
                "이름이 겹치면 어느 API 가 죽었는지 못 가른다: " + labels);
    }

    /**
     * <b>호스트로 떨어지지 않는다.</b>
     *
     * <p>경로 매핑에 없는 URL 이면 라벨이 호스트가 된다. 그건 조용한 회귀다 — 알림은 계속 나가는데
     * 이름만 뭉개져서, 새 API 를 붙인 사람도 한동안 모른다.
     */
    @Test
    void 호스트로_뭉개지지_않는다() {
        for (ExternalApiProbe probe : probes()) {
            String label = probe.system();

            assertNotNull(label);
            assertFalse(label.contains(DATA_GO_KR_HOST),
                    probe.getClass().getSimpleName() + " 의 경로 매핑이 빠졌다 — 라벨이 " + label);
        }
    }
}
