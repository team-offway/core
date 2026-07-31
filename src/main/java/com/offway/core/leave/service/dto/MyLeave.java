package com.offway.core.leave.service.dto;

import com.offway.core.leave.domain.LeaveSummary;
import com.offway.core.leave.domain.LeaveUsage;
import java.util.List;

/**
 * "내 연차" 화면 결과 — 현황과 사용 내역.
 *
 * @param summary 총·사용·남은(파생) 연차
 * @param usages 사용 내역 (최근 순)
 */
public record MyLeave(LeaveSummary summary, List<LeaveUsage> usages) {
}
