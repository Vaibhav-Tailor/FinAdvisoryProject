package com.finadvisory.entity;

import com.finadvisory.config.YNBooleanConverter;
import com.finadvisory.entity.enums.*;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Table: customer_profiles
 * Extended personal and financial information for customers.
 * One-to-one with users table. Address fields are in customer_addresses.
 */
@Entity
@Table(name = "customer_profiles")
@SQLRestriction("del_flg = 'N'")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, unique = true)
    private User user;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "alternate_phone", length = 15)
    private String alternatePhone;

    /**
     * PAN number — unique identifier that links CAS data to this customer.
     * Used during CAS parsing to match imported transactions to the correct customer.
     */
    @Column(name = "pan_number", unique = true, length = 10)
    private String panNumber;

    /**
     * Only the last 4 digits of Aadhaar are stored — never the full 12-digit number.
     * Complies with UIDAI guidelines and data privacy best practices.
     */
    @Column(name = "aadhaar_last4", length = 4)
    private String aadhaarLast4;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", length = 20)
    private MaritalStatus maritalStatus;

    // ── Occupation Details ───────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "occupation_type", length = 30)
    private OccupationType occupationType;

    /**
     * Free text: employer name for salaried, business name for self-employed, etc.
     */
    @Column(name = "occupation_details", length = 255)
    private String occupationDetails;

    /**
     * Job title / designation / role (e.g., "Senior Manager", "Proprietor")
     */
    @Column(name = "designation", length = 255)
    private String designation;

    // ── Income ───────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "annual_income_range", length = 20)
    private AnnualIncomeRange annualIncomeRange;

    // ── Soft Delete ───────────────────────────────────────────────────────────

    /**
     * Y = Logically deleted  |  N = Active
     */
    @Convert(converter = YNBooleanConverter.class)
    @Column(name = "del_flg", nullable = false, length = 1, columnDefinition = "CHAR(1) DEFAULT 'N'")
    @Builder.Default
    private Boolean delFlg = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Lifecycle hooks ──────────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
