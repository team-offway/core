package com.offway.core.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 로그인한 사용자와 그 기기의 게스트 키를 잇는 기록(#34).
 *
 * <p><b>왜 필요한가.</b> 코스·연차는 아직 {@code guest_id} 로 묶여 있고 사용자 식별이 그리로 옮겨가지 않았다.
 * 그래서 서버는 "이 사용자의 데이터가 무엇인가" 를 스스로 알지 못하고 요청 헤더가 들고 오는 값에 의존한다.
 * 그 상태에서는 탈퇴가 헤더 없이 오면 데이터가 주인 없이 남고, 헤더를 바꿔 보내면 남의 것을 지울 수 있다.
 *
 * <p>로그인 시점에 한 줄 적어 두면 둘 다 닫히고, 나중에 소유를 {@code user_id} 로 옮길 때 그 backfill 키가 된다.
 *
 * <p><b>한 게스트 키는 한 사용자에게만 붙는다</b>({@code uk_user_guest_link_guest}). 한 기기에서 두 사람이
 * 로그인해도 그 기기의 옛 데이터는 먼저 로그인한 사용자의 것으로 고정한다 — 뒤에 온 사람에게 상속시키면 남의
 * 데이터를 넘기는 셈이라, 안 넘기는 쪽이 낫다.
 */
@Entity
@Table(name = "user_guest_link")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserGuestLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "guest_id", nullable = false, length = 64)
    private String guestId;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    private UserGuestLink(UUID userId, String guestId, Instant linkedAt) {
        this.userId = Objects.requireNonNull(userId, "사용자 식별자는 필수입니다");
        this.guestId = requireGuestId(guestId);
        this.linkedAt = Objects.requireNonNull(linkedAt, "연결 시각은 필수입니다");
    }

    /**
     * 기기 하나를 사용자에게 잇는다.
     *
     * <p>시각이 입력에서 도출되므로 빌더가 아니라 팩토리다(조립이면 빌더, 계산이면 팩토리).
     */
    public static UserGuestLink of(UUID userId, String guestId, Instant now) {
        return new UserGuestLink(userId, guestId, now);
    }

    /**
     * 게스트 키 형식 검증.
     *
     * <p>{@code X-Guest-Id: " "} 처럼 빈 헤더는 {@code @RequestHeader} 를 통과해 여기까지 온다. 그대로 적으면
     * 아무 데이터도 안 가리키는 행이 남고, 나중에 그것으로 지울 대상을 찾는 쪽이 빈 값을 만난다.
     */
    private static String requireGuestId(String guestId) {
        if (guestId == null || guestId.isBlank()) {
            throw new IllegalArgumentException("게스트 식별자가 비어 있습니다");
        }
        return guestId;
    }
}
