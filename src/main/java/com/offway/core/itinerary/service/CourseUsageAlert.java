package com.offway.core.itinerary.service;

import com.offway.core.common.external.ExternalApi;
import com.offway.core.common.external.RequestUsage;
import com.offway.core.common.notification.Notifier;
import com.offway.core.itinerary.service.dto.GenerateCourse;
import com.offway.core.region.domain.Region;
import com.offway.core.region.repository.RegionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 코스 하나가 태운 외부 호출을 알린다(#421).
 *
 * <h2>10% 알림과 무엇이 다른가</h2>
 *
 * <p>성격이 다르다. {@code ExternalApiCallRecorder} 의 단계 알림은 <b>한도가 마르는 것을 막고</b>,
 * 이쪽은 <b>원가를 잰다.</b> 그래서 둘 다 있어야 한다.
 *
 * <p>이 숫자가 없으면 못 하는 판단이 셋이다 — 한도가 몇 명분인가, 어떤 코스가 비싼가, 캐시가 실제로
 * 듣는가. 하루치 뭉텅이로는 나눗셈조차 못 한다.
 *
 * <h2>실패해도 보낸다</h2>
 *
 * <p>한도는 이미 깎였고, 오히려 <b>"쓰고도 결과가 없는"</b> 쪽이 더 봐야 하는 숫자다.
 *
 * <h2>싣지 않는 것</h2>
 *
 * <p><b>좌표를 싣지 않는다.</b> 디스코드 전송은 제3자 제공이자 국외 이전이라, 좌표를 실으면 "국외로
 * 위치정보를 제공한다" 가 되어 무게가 완전히 달라진다(#400 · #401 과 반대 방향이다).
 *
 * <p>출발지명도 없다 — 생성 요청이 좌표만 받고 이름은 저장할 때만 실어 보낸다(#382). 지어내지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseUsageAlert {

    private static final String SUCCESS_TITLE = "🧭 코스 생성";
    private static final String FAILURE_TITLE = "🧭 코스 생성 실패";
    private static final String REGENERATE_SUFFIX = " · 재생성";

    /** 내역 구분자 — 디스코드 한 줄 안에서 API 를 가른다. */
    private static final String BREAKDOWN_SEPARATOR = " · ";

    /** 사용자를 모를 때(로그인 없이 열리면). 지금은 인증 뒤라 닿지 않는다. */
    private static final String UNKNOWN_USER = "게스트";

    /** 지역명을 못 찾았을 때 — 지역이 지워졌거나 시드가 안 들어온 환경이다. */
    private static final String UNKNOWN_REGION = "지역미상";

    private final Notifier notifier;
    private final RegionRepository regionRepository;

    /**
     * 한 건 보낸다. <b>알림이 코스 생성을 막지 않는다</b> — 관측이 기능을 막으면 안 된다.
     *
     * @param succeeded 코스가 나왔나. 실패도 보낸다(위 참고)
     * @param regenerated 재생성인가 — 같은 사용자가 연달아 만들 때 두 번째가 싼지를 여기서 본다
     */
    public void send(UUID userId, GenerateCourse command, RequestUsage usage,
            boolean succeeded, boolean regenerated) {
        try {
            notifier.send(message(userId, command, usage, succeeded, regenerated));
        } catch (RuntimeException e) {
            log.warn("코스 생성 사용량 알림 실패 cause={}", e.getClass().getSimpleName());
        }
    }

    private String message(UUID userId, GenerateCourse command, RequestUsage usage,
            boolean succeeded, boolean regenerated) {
        String title = (succeeded ? SUCCESS_TITLE : FAILURE_TITLE) + (regenerated ? REGENERATE_SUFFIX : "");
        return """
                %s · %s %d일 (%s)
                %s
                외부 호출 %d건%s"""
                .formatted(
                        title,
                        regionNameOf(command.regionId()),
                        command.travelDays(),
                        command.transport().label(),
                        // 전문으로 싣는다 — 복붙으로 바로 조회된다.
                        userId == null ? UNKNOWN_USER : userId.toString(),
                        usage.total(),
                        breakdown(usage));
    }

    /**
     * {@code — 국문관광정보 12 · TMAP 경로 4}. 0건이면 빈 문자열이다.
     *
     * <p><b>0건도 정상이다</b> — 전부 캐시·DB 로 끝난 요청이라는 뜻이고, 그게 보이는 것이 이 알림의
     * 쓸모 중 하나다(캐시가 실제로 듣는가).
     */
    private static String breakdown(RequestUsage usage) {
        Map<ExternalApi, Long> used = usage.snapshot();
        if (used.isEmpty()) {
            return "";
        }
        return " — " + used.entrySet().stream()
                .map(entry -> "%s %d".formatted(entry.getKey().label(), entry.getValue()))
                .collect(Collectors.joining(BREAKDOWN_SEPARATOR));
    }

    private String regionNameOf(long regionId) {
        return regionRepository.findByIds(List.of(regionId)).stream()
                .findFirst()
                .map(Region::shortName)
                .orElse(UNKNOWN_REGION);
    }
}
