package com.omragul.identity.entity.audit;

import com.omragul.identity.entity.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_sessions_user")
    )
    @ToString.Exclude
    private User user;

    @Column(name = "jwt_id", nullable = false, unique = true, length = 255)
    private String jwtId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "login_time", nullable = false, updatable = false)
    private Instant loginTime;

    @Column(name = "logout_time")
    private Instant logoutTime;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;
}