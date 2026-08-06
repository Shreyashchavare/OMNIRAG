package com.omragul.identity.entity.rbac;

import com.omragul.identity.entity.common.AuditableEntity;
import com.omragul.identity.enums.PermissionType;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "permission_id", nullable = false, updatable = false)
    private UUID permissionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_name", nullable = false, unique = true, length = 100)
    private PermissionType permissionName;

    @Column(name = "description", length = 500)
    private String description;

    @OneToMany(
            mappedBy = "permission",
            cascade = {
                    CascadeType.PERSIST,
                    CascadeType.MERGE
            },
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    @ToString.Exclude
    private Set<RolePermission> rolePermissions = new HashSet<>();

    @OneToMany(
            mappedBy = "permission",
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
}