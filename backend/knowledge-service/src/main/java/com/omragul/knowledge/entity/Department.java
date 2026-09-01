package com.omragul.knowledge.entity;

import com.omragul.knowledge.entity.common.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "departments",
        indexes = {
                @Index(name = "idx_departments_name", columnList = "name")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Builder.Default
    @OneToMany(
            mappedBy = "department",
            fetch = FetchType.LAZY
    )
    private List<KnowledgeBase> knowledgeBases = new ArrayList<>();
}