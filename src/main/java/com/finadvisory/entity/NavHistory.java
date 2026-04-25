package com.finadvisory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import com.finadvisory.config.YNBooleanConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Table: nav_history
 * Stores daily NAV (Net Asset Value) for every mutual fund scheme.
 * Populated daily at 11 PM by NavSyncScheduler from AMFI's NAVAll.txt.
 *
 * UNIQUE constraint on (scheme_id, nav_date) prevents duplicate entries
 * if the scheduler runs more than once on the same day.
 *
 * Query pattern for current NAV:
 *   SELECT nav_value FROM nav_history
 *   WHERE scheme_id = ?
 *   AND nav_date = (SELECT MAX(nav_date) FROM nav_history WHERE scheme_id = ?)
 *   AND del_flg = 'N'
 */
@Entity
@Table(
    name = "nav_history",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_scheme_date",
            columnNames = {"scheme_id", "nav_date"}
        )
    },
    indexes = {
        @Index(name = "idx_nav_date",   columnList = "nav_date"),
        @Index(name = "idx_nav_scheme", columnList = "scheme_id")
    }
)
@SQLRestriction("del_flg = 'N'")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NavHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * The mutual fund scheme this NAV record belongs to.
     * ON DELETE CASCADE — if scheme is deleted, NAV history is also removed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_id", referencedColumnName = "id", nullable = false)
    private MfScheme scheme;

    /**
     * The business date for this NAV record.
     * AMFI publishes NAV for each business day (Mon–Fri, excluding holidays).
     * Format: YYYY-MM-DD
     */
    @Column(name = "nav_date", nullable = false)
    private LocalDate navDate;

    /**
     * Net Asset Value in INR.
     * DECIMAL(15, 4) — supports values up to ₹99,999,999,999.9999
     * Example: 160.4321
     * Always use BigDecimal for financial values — never float or double.
     */
    @Column(name = "nav_value", nullable = false, precision = 15, scale = 4)
    private BigDecimal navValue;

    /**
     * Y = Logically deleted  |  N = Active
     */
    @Convert(converter = YNBooleanConverter.class)
    @Column(name = "del_flg", nullable = false, length = 1, columnDefinition = "CHAR(1) DEFAULT 'N'")
    @Builder.Default
    private Boolean delFlg = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ── Lifecycle hooks ───────────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
