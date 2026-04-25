package com.finadvisory.entity;

import com.finadvisory.config.YNBooleanConverter;
import com.finadvisory.entity.enums.AddressType;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * Table: customer_addresses
 * Stores permanent and current address separately with fully segregated fields.
 * UNIQUE constraint on (user_id, address_type) ensures exactly one
 * PERMANENT and one CURRENT row per customer.
 */
@Entity
@Table(
    name = "customer_addresses",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_user_addr_type",
            columnNames = {"user_id", "address_type"}
        )
    }
)
@SQLRestriction("del_flg = 'N'")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    /**
     * PERMANENT = Permanent / native address
     * CURRENT   = Current residential address
     * UNIQUE constraint ensures only one of each type per customer.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 15)
    private AddressType addressType;

    /**
     * Flat No. / House No. / Building Name / Plot No.
     */
    @Column(name = "address_line1", nullable = false, length = 255)
    private String addressLine1;

    /**
     * Street / Road / Area / Locality / Colony
     */
    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    /**
     * Nearby landmark for easier identification
     */
    @Column(name = "landmark", length = 255)
    private String landmark;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "state", length = 100)
    private String state;

    /**
     * Defaults to India. Store full country name (e.g., "India", "United States")
     */
    @Column(name = "country", nullable = false, length = 100, columnDefinition = "VARCHAR(100) DEFAULT 'India'")
    @Builder.Default
    private String country = "India";

    @Column(name = "pincode", length = 10)
    private String pincode;

    /**
     * Y = Current address is same as permanent address.
     * When Y, frontend shows "Same as Permanent" checkbox checked.
     * Backend skips creating a separate CURRENT row.
     * N = Addresses are different.
     */
    @Convert(converter = YNBooleanConverter.class)
    @Column(name = "is_same_as_permanent", nullable = false, length = 1, columnDefinition = "CHAR(1) DEFAULT 'N'")
    @Builder.Default
    private Boolean isSameAsPermanent = false;

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
