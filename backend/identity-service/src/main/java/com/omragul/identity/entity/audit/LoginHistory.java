package com.omragul.identity.entity.audit;

import com.omragul.identity.entity.user.User;
import com.omragul.identity.enums.LoginStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "login_id", nullable = false, updatable = false)
    private UUID loginId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            foreignKey = @ForeignKey(name = "fk_login_history_user")
    )
    @ToString.Exclude
    private User user;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "login_status", nullable = false, length = 30)
    private LoginStatus loginStatus;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "login_time", nullable = false, updatable = false)
    private Instant loginTime;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}