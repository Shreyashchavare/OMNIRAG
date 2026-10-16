package com.omragul.identity.entity.rbac;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "role_permissions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_role_permission",
                        columnNames = {"role_id", "permission_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "role_permission_id", nullable = false, updatable = false)
    private UUID rolePermissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "role_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_role_permissions_role")
    )
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "permission_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_role_permissions_permission")
    )
    private Permission permission;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @CreationTimestamp
    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;
}