package com.offway.core.device.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offway.core.device.domain.DevicePlatform;
import com.offway.core.device.domain.DevicePushToken;
import com.offway.core.device.repository.DevicePushTokenRepository;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 푸시 토큰 등록·해제의 HTTP 계약(#264).
 *
 * <p><b>소유자·토큰을 테스트마다 다르게 쓴다.</b> 이 클래스는 DB 를 롤백하지 않아(컨텍스트를 공유하는 다른
 * 컨트롤러 통합 테스트와 같다) 같은 값을 쓰면 앞 테스트의 잔여 상태가 다음 시나리오로 새어 든다. 특히
 * 토큰은 테이블 전체에서 유니크라 소유자만 갈라서는 격리되지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class DeviceIntegrationTest {

    private static final String URL = "/api/v1/devices";
    private static final String GUEST_HEADER = "X-Guest-Id";

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
        String guest = "device-register";

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

    @Test
    void 같은_토큰을_다시_보내도_행이_늘지_않고_갱신된다() throws Exception {
        // 토큰은 갱신되고 같은 기기가 여러 번 등록한다 — 그때마다 행이 생기면 같은 알림이 여러 번 간다.
        String guest = "device-reregister";
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
    void 같은_토큰이_다른_게스트로_오면_주인이_바뀐다() throws Exception {
        // 앱을 지웠다 깔면 게스트 ID 는 새로 발급되지만 토큰은 이어질 수 있다. 그 기기의 주인은 새 게스트다.
        String token = uniqueToken();

        mockMvc.perform(post(URL)
                        .header(GUEST_HEADER, "device-old-owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(token, "IOS")))
                .andExpect(status().isOk());
        mockMvc.perform(post(URL)
                        .header(GUEST_HEADER, "device-new-owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(token, "IOS")))
                .andExpect(status().isOk());

        assertEquals(0, devicePushTokenRepository.findByOwner("device-old-owner").size());
        assertEquals(1, devicePushTokenRepository.findByOwner("device-new-owner").size());
    }

    @Test
    void 한_게스트가_기기_두_대를_등록할_수_있다() throws Exception {
        String guest = "device-two";

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
        String guest = "device-unregister";
        String other = "device-untouched";
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
        mockMvc.perform(delete(URL).header(GUEST_HEADER, "device-nothing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void 같은_토큰이_동시에_등록돼도_행이_하나다() throws Exception {
        // 이 설계의 핵심 주장이다. "있나 보고 없으면 넣기" 로 풀었다면 여기서 둘 다 "없다" 를 읽고
        // 하나가 유니크 제약에 걸려 500 이 나간다. 판정을 DB 한 문장에 맡겨 경합 자체를 없앴다.
        String guest = "device-concurrent";
        String token = uniqueToken();
        int attempts = 4;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < attempts; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    // 클래스의 @WithMockUser 는 스레드에 묶여 있어 여기까지 따라오지 않는다(전부 401).
                    // 요청마다 인증을 실어 보낸다.
                    statuses.add(mockMvc.perform(post(URL)
                                    .with(user("tester"))
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
                        .header(GUEST_HEADER, "device-blank-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(" ", "IOS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 모르는_플랫폼은_400이다() throws Exception {
        mockMvc.perform(post(URL)
                        .header(GUEST_HEADER, "device-bad-platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueToken(), "WINDOWS_PHONE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void 게스트_헤더가_비면_400이고_등록되지_않는다() throws Exception {
        // 빈 헤더는 @RequestHeader 를 통과한다 — 도메인까지 흘려보내면 500 이 나간다.
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
}
