package com.omragul.identity.entity.rbac;

import com.omragul.identity.entity.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "user_permissions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_permission",
                        columnNames = {"user_id", "permission_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_permission_id", nullable = false, updatable = false)
    private UUID userPermissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_permissions_user")
    )
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "permission_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_permissions_permission")
    )
    private Permission permission;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @CreationTimestamp
    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;
}