package com.offway.core.leave.domain;

import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.JdbcTypeCode;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소유자의 <b>총 연차</b>. 남은 연차는 저장하지 않는다 — 사용 내역({@link LeaveUsage})을 정본으로 두고
 * {@link LeaveSummary} 가 파생한다.
 *
 * <p>소유 키는 코스 저장과 같은 {@code user_id} 다(#280). 예전에는 요청 헤더로 오던 게스트 키였는데, 발급도
 * 검증도 없어 그 문자열을 아는 것만으로 남의 연차를 지울 수 있었다. 이제 인증이 확인한 사용자만 자기 행에 닿는다.
 */
@Entity
@Table(name = "leave_balance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    /** 총 연차(0.25 단위). 사용 내역을 빼면 남은 연차가 된다. */
    @Column(name = "total_days", nullable = false)
    private double totalDays;

    private LeaveBalance(UUID userId, double totalDays) {
        this.userId = requireOwner(userId);
        this.totalDays = requireTotal(totalDays);
    }

    /** 처음 저장할 때. 이후 수정은 {@link #changeTotal}. */
    public static LeaveBalance of(UUID userId, double totalDays) {
        return new LeaveBalance(userId, totalDays);
    }

    /** 스테퍼로 총 연차를 고쳐 쓴다. */
    public void changeTotal(double totalDays) {
        this.totalDays = requireTotal(totalDays);
    }

    /**
     * 불변식 — 여기 닿는 위반은 버그다. 계약 검증(0.25 단위·상한)은 요청 DTO 경계가 이미 400 으로 걸러야 한다.
     * 그래도 두는 이유는 누가 만들든 스스로 유효함을 보장하는 최후의 보루이기 때문이다.
     */
    private static double requireTotal(double totalDays) {
        if (!LeaveDays.isValidTotal(totalDays)) {
            throw new IllegalArgumentException("총 연차가 유효하지 않습니다: " + totalDays);
        }
        return totalDays;
    }

    /**
     * 불변식 — 여기 닿는 위반은 버그다. 예전에는 계약 예외(400)였다. 소유 키가 요청 헤더라 빈 값·초과 길이가
     * 멀쩡한 클라이언트에게서도 들어왔기 때문이다. 이제 소유자는 인증이 확인한 UUID 라, 값이 없다는 것은
     * 인증을 지나온 요청에 주체가 없다는 뜻뿐이다(#280).
     */
    private static UUID requireOwner(UUID userId) {
        return Objects.requireNonNull(userId, "userId 는 null 일 수 없습니다.");
    }
}
