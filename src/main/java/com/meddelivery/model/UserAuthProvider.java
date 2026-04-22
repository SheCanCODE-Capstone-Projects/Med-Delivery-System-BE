package com.meddelivery.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_auth_providers",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_auth_provider_external",
            columnNames = {"provider", "provider_user_id"}),
        @UniqueConstraint(name = "uk_auth_provider_user",
            columnNames = {"user_id", "provider"})
    }
)
@Getter
@Setter
@ToString(exclude = "user")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAuthProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String providerUserId;

    @CreationTimestamp
    private LocalDateTime linkedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}