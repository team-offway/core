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
 * <p><b>기기의 주인은 로그인한 사용자다</b>(#280). 기기를 가리키는 것은 {@code token} 이고, 소유 칸은
 * "누구의 기기냐" 를 담는다 — 그건 사람이어야 한다.
 *
 * <p>예전에는 {@code X-Guest-Id} 헤더가 그 자리였다. 그러면 알림은 {@code user_id} 로 만들어지는데 기기는
 * 헤더 값으로 저장돼 <b>발송이 한 대도 찾지 못했다</b> — 알림은 생기는데 푸시만 조용히 안 가는 상태다.
 * 등록과 발송이 같은 키를 쓰는지가 이 클래스가 지키는 것이다.
 *
 * <p><b>소유자·토큰을 테스트마다 다르게 쓴다.</b> 이 클래스는 DB 를 롤백하지 않아(컨텍스트를 공유하는 다른
 * 컨트롤러 통합 테스트와 같다) 같은 값을 쓰면 앞 테스트의 잔여 상태가 다음 시나리오로 새어 든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithLoginUser
class DeviceIntegrationTest {

    private static final String URL = "/api/v1/devices";
    /**
     * 소유자를 명시해야 하는 시나리오의 고정 사용자.
     *
     * <p><b>시나리오마다 다른 값을 쓴다.</b> 이 클래스는 DB 를 롤백하지 않아, 둘이 같은 사용자를 쓰면
     * 앞 테스트가 등록한 기기가 뒤 테스트의 건수에 섞인다 — 실제로 그렇게 한 번 깨졌다.
     */
    private static final String ACTOR = "00000264-0000-4000-8000-000000000001";

    /** 헤더를 무시하는지 보는 시나리오의 고정 사용자 — 위와 겹치면 안 된다. */
    private static final String HEADER_IGNORED_ACTOR = "00000264-0000-4000-8000-000000000002";

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
        String guest = owner("device-register");

        mockMvc.perform(post(URL).with(loginAs(UUID.fromString(guest)))
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
     * 기기의 주인은 <b>로그인한 사용자</b>다 — 요청이 정하지 않는다.
     *
     * <p>이 값이 곧 푸시 발송이 기기를 찾는 키다({@code PushDispatcher} 는 알림 소유자
     * {@code UUID.toString()} 으로 조회한다). 등록과 발송이 같은 키를 써야 알림이 실제로 닿는다 —
     * 예전에는 등록이 헤더 값을 넣어 <b>발송이 한 대도 못 찾았다.</b>
     */
    @Test
    @WithLoginUser(ACTOR)
    void 로그인한_사용자를_기기의_주인으로_저장한다() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueToken(), "IOS")))
                .andExpect(status().isOk());

        assertEquals(1, devicePushTokenRepository.findByOwner(ACTOR).size(),
                "푸시 발송이 이 키로 기기를 찾는다");
    }

    @Test
    void 같은_토큰을_다시_보내도_행이_늘지_않고_갱신된다() throws Exception {
        // 토큰은 갱신되고 같은 기기가 여러 번 등록한다 — 그때마다 행이 생기면 같은 알림이 여러 번 간다.
        String guest = owner("device-reregister");
        String token = uniqueToken();

        mockMvc.perform(post(URL).with(loginAs(UUID.fromString(guest)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(token, "IOS")))
                .andExpect(status().isOk());
        mockMvc.perform(post(URL).with(loginAs(UUID.fromString(guest)))
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
        String victim = owner("device-victim");
        String attacker = owner("device-attacker");

        mockMvc.perform(post(URL).with(loginAs(UUID.fromString(victim)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(token, "IOS")))
                .andExpect(status().isOk());
        mockMvc.perform(post(URL).with(loginAs(UUID.fromString(attacker)))
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
        String before = owner("device-before-reinstall");
        String after = owner("device-after-reinstall");

        mockMvc.perform(post(URL).with(loginAs(UUID.fromString(before)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(token, "IOS")))
                .andExpect(status().isOk());
        mockMvc.perform(post(URL).with(loginAs(UUID.fromString(after)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(token, "IOS")))
                .andExpect(status().isOk());

        assertEquals(1, devicePushTokenRepository.findByOwner(before).size());
        assertEquals(1, devicePushTokenRepository.findByOwner(after).size());
    }

    @Test
    void 한_게스트가_기기_두_대를_등록할_수_있다() throws Exception {
        String guest = owner("device-two");

        mockMvc.perform(post(URL).with(loginAs(UUID.fromString(guest)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueToken(), "IOS")))
                .andExpect(status().isOk());
        mockMvc.perform(post(URL).with(loginAs(UUID.fromString(guest)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueToken(), "ANDROID")))
                .andExpect(status().isOk());

        assertEquals(2, devicePushTokenRepository.findByOwner(guest).size());
    }

    @Test
    void 해제하면_그_게스트의_토큰만_지운다() throws Exception {
        String guest = owner("device-unregister");
        String other = owner("device-untouched");
        mockMvc.perform(post(URL).with(loginAs(UUID.fromString(guest)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueToken(), "IOS")))
                .andExpect(status().isOk());
        mockMvc.perform(post(URL).with(loginAs(UUID.fromString(other)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueToken(), "IOS")))
                .andExpect(status().isOk());

        mockMvc.perform(delete(URL).with(loginAs(UUID.fromString(guest))))
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
        mockMvc.perform(delete(URL).with(loginAs(UUID.fromString(owner("device-nothing")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void 같은_토큰이_동시에_등록돼도_행이_하나다() throws Exception {
        // 이 설계의 핵심 주장이다. "있나 보고 없으면 넣기" 로 풀었다면 여기서 둘 다 "없다" 를 읽고
        // 하나가 유니크 제약에 걸려 500 이 나간다. 판정을 DB 한 문장에 맡겨 경합 자체를 없앴다.
        String actor = owner("device-concurrent");
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
                    // 요청마다 인증을 실어 보낸다 — **같은 사용자여야 한다.** 주인이 다르면 경합 자체가
                    // 없어 이 테스트가 아무것도 검증하지 않는다(#280 으로 주인이 로그인 사용자가 됐다).
                    statuses.add(mockMvc.perform(post(URL)
                                    .with(loginAs(UUID.fromString(actor)))
                                    
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
        assertEquals(1, devicePushTokenRepository.findByOwner(actor).size());
    }

    @Test
    void 토큰이_비면_400이다() throws Exception {
        mockMvc.perform(post(URL)
                        
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(" ", "IOS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 모르는_플랫폼은_400이다() throws Exception {
        mockMvc.perform(post(URL)
                        
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueToken(), "WINDOWS_PHONE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    /**
     * <b>요청이 소유자를 정하지 못한다</b>(#280).
     *
     * <p>예전에는 {@code X-Guest-Id} 헤더가 주인이라 빈 값이 400 이었고, 남의 값을 적어 보내면 남의
     * 기기가 됐다. 이제 그 입력 자체가 없다 — 헤더를 실어 보내도 서버가 안 읽는다.
     */
    @Test
    @WithLoginUser(HEADER_IGNORED_ACTOR)
    void 헤더로_소유자를_바꿔_보내도_로그인한_사용자로_저장된다() throws Exception {
        String other = owner("device-someone-else");

        mockMvc.perform(post(URL)
                        .header("X-Guest-Id", other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueToken(), "IOS")))
                .andExpect(status().isOk());

        assertEquals(1, devicePushTokenRepository.findByOwner(HEADER_IGNORED_ACTOR).size(),
                "로그인한 사용자로 저장돼야 한다 — 푸시 발송이 이 값으로 찾는다");
        assertTrue(devicePushTokenRepository.findByOwner(other).isEmpty(),
                "헤더에 적은 남의 값으로는 저장되지 않는다");
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
                        
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueToken(), "IOS")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("COMMON-401"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(delete(URL).with(anonymous()))
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
    private static String owner(String unused) {
        // 소유자는 이제 UUID 다(#280). 인자는 시나리오를 읽기 쉽게 남겨 둔 이름표일 뿐이다 —
        // 값이 겹치면 롤백 없는 이 클래스에서 앞 테스트의 잔여 상태가 새어 든다.
        return UUID.randomUUID().toString();
    }
}
