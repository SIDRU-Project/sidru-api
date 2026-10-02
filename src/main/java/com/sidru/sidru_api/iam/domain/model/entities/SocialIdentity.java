package com.sidru.sidru_api.iam.domain.model.entities;

import com.sidru.sidru_api.iam.domain.model.valueobjects.SocialProvider;
import com.sidru.sidru_api.shared.domain.model.entities.AuditableModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Identidad de un usuario en un proveedor externo ({@code sub} estable por proveedor).
 * La restricción única (provider, subject) garantiza una sola cuenta por identidad externa.
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "social_identities",
        uniqueConstraints = @UniqueConstraint(name = "uk_social_identity_provider_subject",
                columnNames = {"provider", "subject"}),
        indexes = @Index(name = "ix_social_identity_user", columnList = "user_id"))
public class SocialIdentity extends AuditableModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SocialProvider provider;

    @Column(nullable = false)
    private String subject;

    /** Correo del token al momento de vincular (auditoría). */
    private String email;

    public SocialIdentity(Long userId, SocialProvider provider, String subject, String email) {
        this.userId = userId;
        this.provider = provider;
        this.subject = subject;
        this.email = email;
    }
}
