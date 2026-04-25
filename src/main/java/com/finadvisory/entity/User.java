package com.finadvisory.entity;

import com.finadvisory.config.YNBooleanConverter;
import com.finadvisory.entity.enums.UserRole;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Table: users
 * Central authentication table for both ADMIN and CUSTOMER roles.
 * @SQLRestriction ensures all queries automatically filter out logically deleted records.
 */
@Entity
@Table(name = "users")
@SQLRestriction("del_flg = 'N'")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    /**
     * Y = Account is active and can log in
     * N = Account is deactivated by admin (cannot log in)
     */
    @Convert(converter = YNBooleanConverter.class)
    @Column(name = "is_active", nullable = false, length = 1, columnDefinition = "CHAR(1) DEFAULT 'Y'")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Y = Customer must change password on first login
     * N = Password already changed at least once
     */
    @Convert(converter = YNBooleanConverter.class)
    @Column(name = "is_first_login", nullable = false, length = 1, columnDefinition = "CHAR(1) DEFAULT 'Y'")
    @Builder.Default
    private Boolean isFirstLogin = true;

    /**
     * FK to admin user who created this customer account.
     * Null for the first admin (self-seeded).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "id")
    private User createdBy;

    /**
     * Y = Logically deleted  |  N = Active
     * Never use physical DELETE on this table.
     */
    @Convert(converter = YNBooleanConverter.class)
    @Column(name = "del_flg", nullable = false, length = 1, columnDefinition = "CHAR(1) DEFAULT 'N'")
    @Builder.Default
    private Boolean delFlg = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Relationships ───────────────────────────────────────────────────────

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private CustomerProfile customerProfile;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CustomerAddress> addresses;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RiskProfile> riskProfiles;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Transaction> transactions;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<PortfolioHolding> portfolioHoldings;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<CasUpload> casUploads;

    // ── Lifecycle hooks ─────────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── Helper methods ───────────────────────────────────────────────────────

    public boolean isAdmin() {
        return UserRole.ADMIN.equals(this.role);
    }

    public boolean isCustomer() {
        return UserRole.CUSTOMER.equals(this.role);
    }
}
