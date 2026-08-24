package com.offway.core.itinerary.service.dto;

import com.offway.core.leave.service.dto.CourseDeduction;

/**
 * 소유자가 보는 코스 하나 — 코스와 <b>그 코스로 깎인 연차</b>를 함께 든다(#317).
 *
 * <p><b>왜 묶는가.</b> 앱이 코스 상세를 열 때마다 목록을 병행 조회해 요약의 차감 여부를 찾아 채우고 있었다 —
 * 상세 하나 보는 데 요청이 두 번 나갔다. 상세가 스스로 답하면 그 왕복이 사라진다.
 *
 * <p><b>공개 공유에는 쓰지 않는다.</b> 차감 정보는 코스가 아니라 <b>소유자</b>에게 딸린 값이라, 링크를 받은
 * 사람에게 나가면 남의 연차 사정을 알려주는 셈이 된다. 그래서 이 타입은 소유자 경로에서만 만든다.
 *
 * @param course 코스 본문
 * @param deduction 이 코스로 깎인 연차. <b>차감한 적 없으면 null</b> — 0 과 구분해야 한다.
 *     차감량 0 은 "확정했고 깎을 평일이 없었다" 는 뜻이라 차감하지 않은 것과 다르다(#212)
 */
public record OwnedCourse(GeneratedCourse course, CourseDeduction deduction) {

    /** 차감 여부는 값의 유무가 답한다 — 별도 플래그를 두면 둘이 어긋날 수 있다. */
    public boolean deducted() {
        return deduction != null;
    }

    /** 깎인 일수. 차감한 적 없으면 null 이다. */
    public Double consumedLeaveDays() {
        return deduction == null ? null : deduction.days();
    }
}
