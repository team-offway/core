package com.offway.core.policy.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 팀에게 보낼 정책 점검 한 통(#220) — <b>여러 건을 한 메시지로 묶는다.</b>
 *
 * <p>정책마다 따로 보내면 그 자체가 소음이라, 같은 실행에서 걸린 것을 한 통에 담는다.
 *
 * <p><b>보낼 것이 없으면 만들어지지 않는다</b>({@link #of} 가 빈 값). "오늘은 없음" 을 보내는 것도 알림
 * 피로다 — 조용한 날이 정상이라는 것을 채널이 스스로 말해야 한다.
 *
 * <p>문구를 도메인이 드는 이유는 <b>무엇을 해야 하는지가 판정과 붙어 있기</b> 때문이다. "확인 부탁" 만
 * 보내면 받는 사람이 출처를 다시 찾아야 하는데, 어디를 봐야 하는지는 사유를 정한 쪽이 안다.
 */
public record PolicyAlert(String title, List<String> lines) {

    public PolicyAlert {
        Objects.requireNonNull(title, "제목은 null 일 수 없습니다.");
        lines = List.copyOf(lines);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("보낼 것이 없으면 알림을 만들지 않습니다.");
        }
    }

    /**
     * 걸린 정책들로 한 통을 만든다 — <b>없으면 빈 값</b>.
     *
     * @param title 첫 줄. 이 알림이 무슨 종류인지 채널에서 한눈에 갈리게 한다
     * @param entries 정책과 그 사유. 비어 있으면 알림을 만들지 않는다
     * @param today 남은 날·지난 날을 세는 기준
     */
    public static Optional<PolicyAlert> of(String title, List<Entry> entries, LocalDate today) {
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PolicyAlert(
                title, entries.stream().map(entry -> entry.describe(today)).toList()));
    }

    /** 디스코드로 나갈 한 덩이. 제목 아래에 사유들이 줄줄이 붙는다. */
    public String message() {
        return "⚠️ %s %d건\n%s".formatted(title, lines.size(), String.join("\n", lines));
    }

    /**
     * 손봐야 할 정책 하나.
     *
     * @param policy 대상
     * @param reason 왜 걸렸는가
     */
    public record Entry(Policy policy, PolicyStaleness reason) {

        public Entry {
            Objects.requireNonNull(policy, "정책은 null 일 수 없습니다.");
            Objects.requireNonNull(reason, "사유는 null 일 수 없습니다.");
        }

        /**
         * 한 줄로 적는다 — <b>정책명·사유·기한·확인일·주소</b>.
         *
         * <p>받는 사람이 이 줄만 보고 바로 확인하러 갈 수 있어야 한다. 주소가 없으면 어디를 볼지부터
         * 찾아야 하고, 확인일이 없으면 이 값이 언제 적부터 방치됐는지 알 수 없다.
         */
        public String describe(LocalDate today) {
            StringBuilder line = new StringBuilder("· %s — %s".formatted(policy.getName(), detail(today)));
            line.append(" · 확인 ").append(policy.getCheckedOn() == null ? "기록 없음" : policy.getCheckedOn());
            if (policy.getApplyUrl() != null) {
                line.append('\n').append("  ").append(policy.getApplyUrl());
            }
            return line.toString();
        }

        /** 사유마다 함께 봐야 할 값이 다르다 — 끝나가는 것은 남은 날, 미검증은 그대로다. */
        private String detail(LocalDate today) {
            LocalDate end = policy.getPeriodEnd();
            return switch (reason) {
                case EXPIRING_SOON -> "%s(%s, %d일 남음)"
                        .formatted(reason.label(), end, java.time.temporal.ChronoUnit.DAYS.between(today, end));
                case EXPIRES_TODAY -> "%s(%s) — 내일부터 뱃지가 사라집니다".formatted(reason.label(), end);
                case EXPIRED -> "%s(%s, %d일 지남)"
                        .formatted(reason.label(), end, java.time.temporal.ChronoUnit.DAYS.between(end, today));
                case UNVERIFIED -> "%s — 상세·기간이 확정되지 않아 화면에 안 나갑니다".formatted(reason.label());
                case STALE_CHECK -> "%s(%d일 전) — 기관 페이지 개편 여부를 봐 주세요"
                        .formatted(
                                reason.label(),
                                java.time.temporal.ChronoUnit.DAYS.between(policy.getCheckedOn(), today));
            };
        }
    }

    /** 사유별로 걸린 정책을 모은다 — 스케줄러가 "예고" 와 "요약" 을 나눠 담을 때 쓴다. */
    public static List<Entry> entriesOf(List<Policy> policies, LocalDate today, boolean expiryNotice) {
        return policies.stream()
                .flatMap(policy -> PolicyStaleness.of(policy, today).stream()
                        .filter(reason -> reason.isExpiryNotice() == expiryNotice)
                        .map(reason -> new Entry(policy, reason)))
                .collect(Collectors.toList());
    }
}
