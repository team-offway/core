package com.offway.core.common.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * {@code prod} 가 아닌 곳에서 알림을 로그로만 남긴다.
 *
 * <p><b>왜 no-op 이 아닌가.</b> 아무것도 안 하면 "언제 무엇이 나가는가" 를 운영에 올려 봐야 안다. 로그로
 * 남기면 로컬·테스트에서 조건과 문구를 그대로 확인할 수 있다 — 알림은 실제로 울려 봐야 맞는지 아는 기능이다.
 *
 * <p>레벨을 info 로 둔다. 단계 전환에만 불려 잦지 않고, debug 로 두면 확인하려는 사람이 로그 설정부터
 * 바꿔야 한다.
 */
@Slf4j
@Component
@Profile("!prod")
public class LoggingNotifier implements Notifier {

    @Override
    public void send(String message) {
        log.info("[알림] {}", message);
    }
}
