package com.offway.core.common.external;

import com.offway.core.common.batch.repository.BatchRunRepository;
import com.offway.core.common.external.controller.dto.BatchSettingRequest;
import com.offway.core.common.external.controller.dto.ExternalApiSettingRequest;
import com.offway.core.user.service.AdminAccountService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 API 연동 현황을 모은다(#398).
 *
 * <h2>왜 만들었나</h2>
 *
 * <p><b>우리가 하루에 API 를 몇 번 부르는지 아무도 몰랐다.</b> 볼 방법이 전부 막혀 있었다 —
 * {@code /api/v1/quotas} 는 <b>오늘치뿐</b>이라 어제를 못 보고, SSH 는 보안그룹에 막혀 있고,
 * 디스코드 경보는 10% 단계에서만 울려 조용한 날은 아무 정보가 없다.
 *
 * <p>그래서 실제로 틀렸다. 9/1 관측값 하나(700콜)를 평상시로 알고 계산했는데, 하필 <b>매월 1일</b>
 * 이라 월배치가 겹친 날이었다. 하루치로는 판단할 수 없다.
 */
@Service
@RequiredArgsConstructor
public class ExternalApiStatusService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /** 기본 조회 기간 — 주간 배치가 한 번은 들어오는 길이다. */
    private static final int DEFAULT_DAYS = 14;

    /**
     * 조회 상한.
     *
     * <p>행이 (날짜 × API) 라 90일이면 1,200행 남짓이다. 상한 자체는 크지 않지만 <b>없으면 안 된다</b> —
     * {@code days=100000} 한 번이 테이블 전체를 긁는다(목록 페이지네이션과 같은 판단).
     */
    private static final int MAX_DAYS = 90;

    private static final int MIN_DAYS = 1;

    private final ExternalApiCallRepository callRepository;
    private final BatchRunRepository batchRunRepository;
    private final ExternalApiSettings settings;
    private final AdminAccountService adminAccountService;

    /**
     * {@code days} 일치 현황. 오늘을 포함해 거슬러 센다.
     *
     * <p>기간을 <b>거절하지 않고 자른다</b> — 조회 화면에서 잘못된 값은 클라이언트 실수지 계약 위반이
     * 아니고, 400 으로 끊으면 화면이 통째로 빈다.
     */
    @Transactional(readOnly = true)
    public ExternalApiSnapshot snapshot(Integer days) {
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        LocalDate from = today.minusDays(clamp(days) - 1L);
        return new ExternalApiSnapshot(
                from,
                today,
                callRepository.countsBetween(from, today),
                callRepository.callerCountsBetween(from, today),
                batchRunRepository.all(),
                settings.touched(),
                settings.disabledBatches());
    }

    /**
     * 연동 설정을 바꾸고 <b>바뀐 현황 전체</b>를 돌려준다(#403).
     *
     * <p>모르는 연동 이름은 400 이다. 화면이 목록을 서버에서 받으므로 멀쩡한 클라이언트는 안 닿지만,
     * 경로 변수라 손으로 부를 수 있는 자리다.
     */
    @Transactional
    public ExternalApiSnapshot updateApi(String api, ExternalApiSettingRequest request, UUID adminUserId) {
        settings.update(
                parse(api), request.cacheEnabledOrDefault(), request.batchLimit(), labelOf(adminUserId));
        return snapshot(null);
    }

    /**
     * 배치를 멈추거나 다시 돌린다.
     *
     * <p>이름이 실제 배치인지는 <b>확인하지 않는다.</b> 배치 이름은 코드의 상수라 목록을 서버가 들고
     * 있지 않고, 오타로 만든 행은 아무 배치도 안 막아 해가 없다 — 대신 화면에 그대로 보여 눈에 띈다.
     */
    @Transactional
    public ExternalApiSnapshot updateBatch(String name, BatchSettingRequest request, UUID adminUserId) {
        settings.updateBatch(name, request.enabledOrDefault(), labelOf(adminUserId));
        return snapshot(null);
    }

    private static ExternalApi parse(String api) {
        try {
            return ExternalApi.valueOf(api);
        } catch (IllegalArgumentException e) {
            throw ExternalApiSettingException.unknownApi();
        }
    }

    /** 누가 바꿨는지 남긴다 — 배포 없이 바꿀 수 있게 되면서 git blame 이 하던 역할이 사라졌다(#344 와 같은 이유). */
    private String labelOf(UUID adminUserId) {
        return adminAccountService.labelOf(adminUserId).orElse(null);
    }

    private static int clamp(Integer days) {
        if (days == null) {
            return DEFAULT_DAYS;
        }
        return Math.max(MIN_DAYS, Math.min(MAX_DAYS, days));
    }
}
