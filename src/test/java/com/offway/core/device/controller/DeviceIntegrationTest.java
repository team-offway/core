package com.offway.core.device.controller;

import static com.offway.core.user.config.TestLogins.loginAs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.device.domain.DevicePlatform;
import com.offway.core.device.domain.DevicePushToken;
import com.offway.core.device.repository.DevicePushTokenRepository;
import com.offway.core.user.config.WithLoginUser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 푸시 토큰 등록·해제의 HTTP 계약(#264).
 *
 * <p><b>이 도메인은 소유 키를 옮기지 않았다</b>(#280 범위 밖). 코스·연차·알림이 {@code user_id}(UUID)로
 * 간 뒤에도 {@code device_push_token} 의 소유 칸은 {@code guest_id}(문자열)이고, 컨트롤러는 여전히
 * {@code X-Guest-Id} 헤더로 주인을 정한다 — 여기서 대상은 사람이 아니라 <b>기기</b>이기 때문이다.
 *
 * <p>다만 <b>인증은 요구된다.</b> {@code SecurityConfig} 가 {@code /api/v1/devices/**} 를 로그인 뒤로
 * 옮겼으므로 요청마다 로그인 사용자가 필요하다({@link WithLoginUser}). 그 사용자와 헤더의 게스트 키가
 * 서로 무관하다는 점은 {@link #인증한_사용자와_무관하게_헤더의_게스트_키로_저장된다()} 가 잠근다.
 *
 * <p><b>소유자·토큰을 테스트마다 다르게 쓴다.</b> 이 클래스는 DB 를 롤백하지 않아(컨텍스트를 공유하는 다른
 * 컨트롤러 통합 테스트와 같다) 같은 값을 쓰면 앞 테스트의 잔여 상태가 다음 시나리오로 새어 든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithLoginUser
class DeviceIntegrationTest {

    private static final String URL = "/api/v1/devices";
    private static final String GUEST_HEADER = "X-Guest-Id";

    /** 로그인 사용자와 기기 소유 키가 무관함을 보이는 시나리오의 고정 사용자. */
    private static final String ACTOR = "00000264-0000-4000-8000-000000000001";

    /** {@code SecurityConfig} 가 요구하는 권한 — {@code WithLoginUserSecurityContextFactory} 와 같은 값이다. */
    private static final String APP_USER_AUTHORITY = "ROLE_USER";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DevicePushTokenRepository devicePushTokenRepository;

    private static String body(String token, String platform) {
        return "{\"token\": \"%s\", \"platform\": \"%s\"}".formatted(token, platform);
    }

    private static String uniqueToken() {
        return "fcm-" + UUID.randomUUID();
    }


    @Test
    void 등록하면_200과_빈_data를_준다() throws Exception {
        String guest = guest("device-register");

        mockMvc.perform(post(URL)
                        .header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueToken(), "IOS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.detail").value("요청이 정상 처리되었습니다."))
                // 201 이 아니다 — 새로 만드는지 고쳐 쓰는지가 요청마다 달라 만들었다고 단정할 수 없다.
                .andExpect(jsonPath("$.data").doesNotExist());

        List<DevicePushToken> stored = devicePushTokenRepository.findByOwner(guest);
        assertEquals(1, stored.size());
        assertEquals(DevicePlatform.IOS, stored.getFirst().getPlatform());
    }

    /**
     * 기기의 주인은 <b>헤더의 게스트 키</b>다 — 로그인한 사용자가 아니다.
     *
     * <p>#280 이 코스·연차·알림의 소유를 {@code user_id} 로 옮겼지만 이 도메인은 범위 밖이라 그대로다.
     * 그래서 <b>인증은 통과해야 하지만 저장되는 주인은 헤더 값</b>이라는, 한 요청 안에 두 소유 개념이
     * 공존하는 상태가 됐다.
     *
     * <p>이 어긋남에는 대가가 있다. 푸시 발송({@code PushDispatcher})은 알림 소유자
     * {@code UUID.toString()} 으로 기기를 찾는데 여기 저장되는 값은 그것이 아니라 앱의 게스트 키다 —
     * <b>실제로는 한 대도 찾지 못한다.</b> 그 사실은 {@code PushDispatcherIntegrationTest} 가 발송 쪽에서
     * 잠그고, 여기서는 등록 쪽이 그 원인 절반을 갖고 있음을 못 박는다. 기기 소유 키를 무엇으로 둘지는
     * 별도 결정이라 지금 코드의 동작을 있는 그대로 남긴다.
     */
    @Test
    @WithLoginUser(ACTOR)
    void 인증한_사용자와_무관하게_헤더의_게스트_키로_저장된다() throws Exception {
        String guest = guest("device-owner-key");

        mockMvc.perform(post(URL)
                        .header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueToken(), "IOS")))
                .andExpect(status().isOk());

        assertEquals(1, devicePushTokenRepository.findByOwner(guest).size());
        assertTrue(
                devicePushTokenRepository.findByOwner(ACTOR).isEmpty(),
                "로그인 사용자 UUID 로는 기기가 등록되지 않는다 — 푸시 발송이 이 값으로 찾는다");
    }

    @Test
    void 같은_토큰을_다시_보내도_행이_늘지_않고_갱신된다() throws Exception {
        // 토큰은 갱신되고 같은 기기가 여러 번 등록한다 — 그때마다 행이 생기면 같은 알림이 여러 번 간다.
        String guest = guest("device-reregister");
        String token = uniqueToken();

        mockMvc.perform(post(URL)
                        .header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(token, "IOS")))
                .andExpect(status().isOk());
        mockMvc.perform(post(URL)
                        .header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(token, "ANDROID")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        List<DevicePushToken> stored = devicePushTokenRepository.findByOwner(guest);
        assertEquals(1, stored.size());
        // 플랫폼·갱신 시각은 새 값으로, 처음 등록 시각은 그대로.
        assertEquals(DevicePlatform.ANDROID, stored.getFirst().getPlatform());
    }

    @Test
    void 남의_토큰을_등록해도_원래_소유자의_등록은_그대로다() throws Exception {
        // **이 변경의 존재 이유다.** 유니크 제약이 토큰 단독이던 때는 이 두 번째 요청이 첫 행의 소유자를
        // 갈아끼웠다 — 남의 FCM 토큰을 아는 쪽이 상대의 푸시를 끊고(주인이 바뀌므로) 자기 알림을 상대
        // 기기로 보낼 수 있었다. 기기 소유 키가 아직 헤더 값이라 사칭 비용은 여전히 없다.
        String token = uniqueToken();
        String victim = guest("device-victim");
        String attacker = guest("device-attacker");

        mockMvc.perform(post(URL)
                        .header(GUEST_HEADER, victim)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(token, "IOS")))
                .andExpect(status().isOk());
        mockMvc.perform(post(URL)
                        .header(GUEST_HEADER, attacker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(token, "ANDROID")))
                .andExpect(status().isOk());

        // 피해자의 행이 남아 있어야 하고, 플랫폼까지 공격자 값으로 덮이지 않아야 한다.
        List<DevicePushToken> victims = devicePushTokenRepository.findByOwner(victim);
        assertEquals(1, victims.size());
        assertEquals(DevicePlatform.IOS, victims.getFirst().getPlatform());
        // 공격자에게는 자기 소유의 행이 하나 생길 뿐이다.
        assertEquals(1, devicePushTokenRepository.findByOwner(attacker).size());
    }

    @Test
    void 재설치로_게스트가_바뀌면_같은_토큰이_두_행으로_남는다() throws Exception {
        // 복합 유니크의 대가다. 옛 게스트의 행은 주인이 다시 오지 않는 죽은 행이라, 그대로 두면 같은
        // 기기로 알림이 두 번 간다. 그 정리는 발송 단계가 한다 — 토큰 기준 중복 제거와 FCM 의
        // UNREGISTERED 응답으로 걷어낸다(#270). 지금 정리 배치를 두지 않는 것이 의도임을 여기 남긴다.
        String token = uniqueToken();
        String before = guest("device-before-reinstall");
        String after = guest("device-after-reinstall");

        mockMvc.perform(post(URL)
                        .header(GUEST_HEADER, before)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(token, "IOS")))
                .andExpect(status().isOk());
        mockMvc.perform(post(URL)
                        .header(GUEST_HEADER, after)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(token, "IOS")))
                .andExpect(status().isOk());

        assertEquals(1, devicePushTokenRepository.findByOwner(before).size());
        assertEquals(1, devicePushTokenRepository.findByOwner(after).size());
    }

    @Test
    void 한_게스트가_기기_두_대를_등록할_수_있다() throws Exception {
        String guest = guest("device-two");

        mockMvc.perform(post(URL)
                        .header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueToken(), "IOS")))
                .andExpect(status().isOk());
        mockMvc.perform(post(URL)
                        .header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueToken(), "ANDROID")))
                .andExpect(status().isOk());

        assertEquals(2, devicePushTokenRepository.findByOwner(guest).size());
    }

    @Test
    void 해제하면_그_게스트의_토큰만_지운다() throws Exception {
        String guest = guest("device-unregister");
        String other = guest("device-untouched");
        mockMvc.perform(post(URL)
                        .header(GUEST_HEADER, guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueToken(), "IOS")))
                .andExpect(status().isOk());
        mockMvc.perform(post(URL)
                        .header(GUEST_HEADER, other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueToken(), "IOS")))
                .andExpect(status().isOk());

        mockMvc.perform(delete(URL).header(GUEST_HEADER, guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertEquals(0, devicePushTokenRepository.findByOwner(guest).size());
        assertEquals(1, devicePushTokenRepository.findByOwner(other).size());
    }

    @Test
    void 지울_토큰이_없어도_해제는_성공한다() throws Exception {
        // 원한 상태가 이미 이뤄져 있는데 로그아웃 화면이 404 를 띄울 이유가 없다.
        mockMvc.perform(delete(URL).header(GUEST_HEADER, guest("device-nothing")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void 같은_토큰이_동시에_등록돼도_행이_하나다() throws Exception {
        // 이 설계의 핵심 주장이다. "있나 보고 없으면 넣기" 로 풀었다면 여기서 둘 다 "없다" 를 읽고
        // 하나가 유니크 제약에 걸려 500 이 나간다. 판정을 DB 한 문장에 맡겨 경합 자체를 없앴다.
        String guest = guest("device-concurrent");
        String token = uniqueToken();
        int attempts = 4;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < attempts; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    // 클래스의 @WithLoginUser 는 테스트 스레드에 묶여 있어 여기까지 따라오지 않는다(전부 401).
                    // 요청마다 인증을 실어 보낸다 — 기기의 주인은 어차피 헤더가 정하므로 누구로 로그인해도 같다.
                    statuses.add(mockMvc.perform(post(URL)
                                    .with(loginAs(UUID.randomUUID()))
                                    .header(GUEST_HEADER, guest)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body(token, "IOS")))
                            .andReturn()
                            .getResponse()
                            .getStatus());
                } catch (Exception e) {
                    statuses.add(-1);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "동시 등록이 제시간에 끝나지 않았다");

        assertEquals(List.of(200, 200, 200, 200), statuses.stream().sorted().toList());
        assertEquals(1, devicePushTokenRepository.findByOwner(guest).size());
    }

    @Test
    void 토큰이_비면_400이다() throws Exception {
        mockMvc.perform(post(URL)
                        .header(GUEST_HEADER, guest("device-blank-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(" ", "IOS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 모르는_플랫폼은_400이다() throws Exception {
        mockMvc.perform(post(URL)
                        .header(GUEST_HEADER, guest("device-bad-platform"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueToken(), "WINDOWS_PHONE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    /**
     * 게스트 헤더가 비면 <b>여전히 400</b> 이다.
     *
     * <p>다른 도메인에서는 이 시나리오가 사라졌다 — 소유 키가 인증에서 오면서 빈 헤더라는 입력 자체가
     * 없어졌기 때문이다(#280). 기기는 그 전환 밖이라 헤더가 남았고, 그래서 이 계약도 남는다.
     */
    @Test
    void 게스트_헤더가_비면_400이고_등록되지_않는다() throws Exception {
        mockMvc.perform(post(URL)
                        .header(GUEST_HEADER, " ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueToken(), "IOS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("DEVICE-001"))
                .andExpect(jsonPath("$.detail").value("게스트 식별자가 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(delete(URL).header(GUEST_HEADER, " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEVICE-001"));
    }

    /**
     * 인증 없이 부르면 401 이다 — 헤더만으로는 못 들어온다(#280).
     *
     * <p>{@code SecurityConfig} 가 {@code /api/v1/devices/**} 를 로그인 뒤로 옮겼다. 기기 소유 키가 아직
     * 헤더라 이 게이트가 소유를 지켜주지는 않지만, 최소한 아무나 남의 게스트 키로 토큰을 심어 놓을 수는
     * 없게 한다.
     */
    @Test
    void 인증_없이_부르면_401이다() throws Exception {
        mockMvc.perform(post(URL)
                        .with(anonymous())
                        .header(GUEST_HEADER, guest("device-anonymous"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueToken(), "IOS")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("COMMON-401"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(delete(URL).with(anonymous()).header(GUEST_HEADER, guest("device-anonymous")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON-401"));
    }

    /**
     * 매번 다른 소유 키.
     *
     * <p>통합 테스트는 DB 를 공유하고 이 클래스는 롤백하지 않는다({@code findByOwner} 의 전체 행 수를 단언하려면
     * 커밋된 상태가 필요하다). 고정 id 를 쓰면 앞선 실행이 남긴 행이 그 수에 섞여, 코드가 멀쩡한데 빨간불이 되거나
     * 반대로 깨진 코드가 통과한다.
     */
    private static String guest(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
