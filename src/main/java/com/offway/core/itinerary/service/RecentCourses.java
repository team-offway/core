package com.offway.core.itinerary.service;

import com.offway.core.itinerary.service.dto.GenerateCourse;
import com.offway.core.itinerary.service.dto.GeneratedCourse;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * <b>방금 만든 코스를 잠깐 들고 있다가 그대로 다시 준다</b>(#584).
 *
 * <h2>왜 필요했나</h2>
 *
 * <p>추천 목록에서 태안을 고르고 → 뒤로 가고 → 다시 태안을 고르면, 화면에 나오는 코스는 <b>똑같은데</b>
 * 외부 호출은 매번 새로 나갔다. 반복 횟수에 상한이 없다.
 *
 * <p>그 비용이 코스 하나에 이만큼이다.
 *
 * <pre>
 *   TourAPI 후보 수집      3콜   (regionPoiService.collect 는 캐시가 없다)
 *   TMAP 경유지최적화   날짜 수   <b>일일 한도 50</b> — 2박3일이면 17번 만에 마른다
 *   TMAP 경로          인접 구간   일일 한도 1,000
 * </pre>
 *
 * <p>한도가 마르면 직선거리로 폴백하는데 <b>응답은 200 이다</b> — 순서와 소요시간이 틀린 줄 사용자가
 * 알 방법이 없다.
 *
 * <h2>왜 캐시가 거짓말을 안 하나</h2>
 *
 * <p>{@code CourseGenerationService} 에는 {@code Random} 도 {@code shuffle} 도 없다 — <b>생성이
 * 결정적이다.</b> 같은 커맨드면 후보도 순서도 글자 하나까지 같다. 지금은 그 똑같은 입력으로 밖을
 * 다시 부르고 있었다.
 *
 * <h2>왜 1분인가</h2>
 *
 * <p>막으려는 것이 <b>한 뭉치의 반복</b>이다. 뒤로가기 왕복은 몇 초 단위라 1분이면 덮이고, 그 사이에
 * 날씨·혼잡·운영시간이 의미 있게 바뀌지 않는다. 길게 잡을수록 "어제 값을 오늘 보여주는" 쪽으로
 * 기울 뿐 막아야 할 것은 이미 다 막힌다.
 *
 * <p><b>이것이 근본 해결은 아니다.</b> 같은 요청을 다시 한 사람만 돕는다 — 처음 만드는 사람, 다른
 * 옵션으로 만드는 사람에게는 아무 일도 안 일어난다. 그쪽은 구간·순서를 표로 남겨야 하고 그건 #584 의
 * 남은 절반이다.
 *
 * <h2>왜 {@code ExternalDataCache} 를 안 쓰나</h2>
 *
 * <p>그쪽은 <b>loader 의 예외를 삼켜 폴백으로 degrade 하는 것이 계약</b>이다("요청 경로로 올리지
 * 않는다"). 외부 조회에는 맞는 태도지만 여기서는 정반대다 — 코스를 못 만드는 것은
 * {@code ItineraryException} 으로 <b>사용자에게 닿아야 하는 계약 실패</b>이고, 삼키면 빈 코스가
 * 200 으로 나간다. 규약이 막는 '조용한 실패' 그 자체다.
 *
 * <p>그래서 필요한 것만 갖춘 작은 것을 따로 둔다 — TTL · 개수 상한 · <b>예외는 그대로 올린다</b>.
 */
@Slf4j
@Component
public class RecentCourses {

    /**
     * 들고 있는 시간.
     *
     * <p>위 주석의 "한 뭉치의 반복" 이 이 값의 근거다. 늘리려면 그 안에 무엇이 낡는지를 먼저 답해야
     * 한다 — 코스에는 날씨·혼잡·운영시간처럼 하루 안에 변하는 값이 섞여 있다.
     */
    static final Duration TTL = Duration.ofMinutes(1);

    /**
     * 들고 있을 개수.
     *
     * <p><b>키 공간이 무한하다.</b> 커맨드에 출발지 좌표가 들어 있어 상한을 안 두면 계속 쌓인다 —
     * TTL 은 값의 신선도만 관리하고 <b>엔트리를 지우지 않는다</b>(성능 규약).
     *
     * <p>1분 안에 살아 있는 서로 다른 요청 수가 모수다. 운영 DB 와 앱이 <b>EC2 한 대의 800MB 컨테이너</b>
     * 안에 같이 사는 형편이라(CLAUDE.md) 넉넉히 잡을 자리가 아니다. 넘치면 가장 오래 안 쓴 것부터 나간다.
     */
    static final int MAX_ENTRIES = 200;

    /**
     * 접근 순서(LRU)로 도는 맵. {@code removeEldestEntry} 가 상한을 강제한다.
     *
     * <p>{@code synchronizedMap} 으로 감싼다 — 요청 스레드 여럿이 동시에 들어온다.
     * {@code ConcurrentHashMap} 은 LRU 를 못 준다.
     */
    private final Map<GenerateCourse, Entry> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<GenerateCourse, Entry> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });

    /**
     * 이 캐시를 쓸지.
     *
     * <p><b>왜 끄는 길이 필요한가.</b> 이 캐시는 "같은 커맨드면 같은 코스" 를 전제하는데, 그 전제는
     * <b>바탕 데이터가 그 1분 사이에 안 바뀐다</b>는 것에 기대고 있다. 운영에서는 참이다 — 장소 풀은
     * 부팅과 새벽 배치가 채우고, 날씨·혼잡은 시간 단위다.
     *
     * <p><b>통합 테스트에서는 거짓이다.</b> 테스트는 같은 커맨드로 stub 만 갈아 가며 생성한다 —
     * 밀리초 사이에 바탕이 바뀐다. 그대로 두면 앞 시나리오의 코스가 다음 시나리오로 새어 들어
     * <b>테스트가 조용히 엉뚱한 것을 단언한다.</b> 그래서 테스트 프로파일에서 끈다
     * ({@code src/test/resources/application-local.properties}).
     *
     * <p>운영에서도 끌 수 있게 열어 둔다 — 캐시가 예상 못한 모양으로 굴 때 <b>배포 없이</b> 되돌릴
     * 자리가 있어야 한다.
     */
    private final boolean enabled;

    public RecentCourses(
            @Value("${offway.itinerary.recent-course-cache.enabled:true}") boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            log.info("방금 만든 코스 재사용이 꺼져 있습니다 — 매 요청이 새로 만듭니다");
        }
    }

    /**
     * 방금 만든 것이 있으면 그것을, 없으면 만들어서 준다.
     *
     * <p><b>실패는 캐시하지 않고 그대로 올린다.</b> 코스를 못 만드는 것은 사용자에게 닿아야 하는
     * 결과이고, 그 실패를 1분간 굳히면 그사이 후보가 채워져도 계속 같은 오류가 나간다.
     *
     * <p><b>같은 요청이 동시에 둘 들어오면 둘 다 만든다.</b> single-flight 를 두지 않았다 — 막으려는
     * 것이 <b>차례로</b> 오는 반복(뒤로가기 왕복)이라 동시 도착은 이 문제의 모양이 아니고, 그것까지
     * 다루려면 "먼저 시작한 쪽이 터졌을 때 기다리던 쪽에 무엇을 주나" 를 정해야 하는데 그 답이 바로
     * 위에서 {@code ExternalDataCache} 를 못 쓰게 만든 이유다.
     */
    public GeneratedCourse get(GenerateCourse command, Supplier<GeneratedCourse> generator) {
        if (!enabled) {
            return generator.get();
        }
        Entry cached = cache.get(command);
        Instant now = Instant.now();
        if (cached != null && cached.isFresh(now)) {
            log.debug("코스 재사용 — 방금 만든 것을 그대로 줍니다 regionId={}", command.regionId());
            return cached.course();
        }
        GeneratedCourse generated = generator.get();
        cache.put(command, new Entry(generated, now.plus(TTL)));
        return generated;
    }

    /** 지금 들고 있는 개수 — 상한이 실제로 걸리는지 보는 자리다. */
    int size() {
        return cache.size();
    }

    private record Entry(GeneratedCourse course, Instant expiresAt) {

        boolean isFresh(Instant now) {
            return now.isBefore(expiresAt);
        }
    }
}
