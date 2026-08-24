package com.omragul.identity.entity.user;

import com.omragul.identity.entity.audit.LoginHistory;
import com.omragul.identity.entity.audit.UserSession;
import com.omragul.identity.entity.auth.Otp;
import com.omragul.identity.entity.auth.PasswordHistory;
import com.omragul.identity.entity.auth.RefreshToken;
import com.omragul.identity.entity.common.AuditableEntity;
import com.omragul.identity.entity.rbac.UserPermission;
import com.omragul.identity.entity.rbac.UserRole;
import com.omragul.identity.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "account_locked", nullable = false)
    @Builder.Default
    private Boolean accountLocked = false;

    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @OneToOne(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @ToString.Exclude
    private UserProfile userProfile;

    @OneToMany(
            mappedBy = "user",
            cascade = {
                    CascadeType.PERSIST,
                    CascadeType.MERGE
            },
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    @ToString.Exclude
    private Set<UserRole> userRoles = new HashSet<>();

    @OneToMany(
            mappedBy = "user",
            cascade = {
                    CascadeType.PERSIST,
                    CascadeType.MERGE
            },
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    @ToString.Exclude
    private Set<UserPermission> userPermissions = new HashSet<>();

    @OneToMany(
            mappedBy = "user",
            fetch = FetchType.LAZY
    )
    @Builder.Default
    @ToString.Exclude
    private Set<RefreshToken> refreshTokens = new HashSet<>();

    @OneToMany(
            mappedBy = "user",
            fetch = FetchType.LAZY
    )
    @Builder.Default
    @ToString.Exclude
    private Set<PasswordHistory> passwordHistory = new HashSet<>();

    @OneToMany(
            mappedBy = "user",
            fetch = FetchType.LAZY
    )
    @Builder.Default
    @ToString.Exclude
    private Set<Otp> otps = new HashSet<>();

    @OneToMany(
            mappedBy = "user",
            fetch = FetchType.LAZY
    )
    @Builder.Default
    @ToString.Exclude
    private Set<UserSession> userSessions = new HashSet<>();

    @OneToMany(
            mappedBy = "user",
            fetch = FetchType.LAZY
    )
    @Builder.Default
    @ToString.Exclude
    private Set<LoginHistory> loginHistory = new HashSet<>();
}