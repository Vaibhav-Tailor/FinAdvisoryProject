package com.finadvisory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import com.finadvisory.config.YNBooleanConverter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Table: portfolio_holdings
 * Pre-computed/materialized summary of current holdings per customer per scheme.
 *
 * This table exists purely for READ performance on the customer dashboard.
 * Without it, every dashboard load would need to aggregate all transactions
 * in real time — slow and expensive as transaction count grows.
 *
 * Recomputed by HoldingRecalculationService every time a transaction is:
 *  - Inserted (new transaction added)
 *  - Logically deleted (del_flg set to 'Y')
 *
 * Current Value and P&L are NOT stored here — they are calculated at runtime:
 *  Current Value = total_units × current_nav (from nav_history)
 *  Absolute P&L  = Current Value − total_invested_amount
 *  Absolute %    = (P&L / total_invested_amount) × 100
 *  XIRR          = Calculated by XirrCalculatorService using transaction cash flows
 *
 * UNIQUE constraint on (user_id, scheme_id, folio_number) ensures one
 * holding row per customer per scheme per folio.
 */
@Entity
@Table(
    name = "portfolio_holdings",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_holding",
            columnNames = {"user_id", "scheme_id", "folio_number"}
        )
    },
    indexes = {
        @Index(name = "idx_holding_user",   columnList = "user_id"),
        @Index(name = "idx_holding_scheme", columnList = "scheme_id")
    }
)
@SQLRestriction("del_flg = 'N'")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_id", referencedColumnName = "id", nullable = false)
    private MfScheme scheme;

    /**
     * Folio number for this holding.
     * A customer may hold the same scheme under multiple folios.
     */
    @Column(name = "folio_number", length = 50)
    private String folioNumber;

    /**
     * Net units currently held.
     * = Sum of all purchase/SIP/bonus/dividend-reinvest units
     *   MINUS all redemption/switch-out units
     * from the transactions table for this (user_id, scheme_id, folio_number).
     */
    @Column(name = "total_units", nullable = false, precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal totalUnits = BigDecimal.ZERO;

    /**
     * Total amount invested in INR (sum of all purchase/SIP amounts).
     * Redemptions are NOT subtracted here — this is cost basis, not net invested.
     * Used for Absolute Return calculation.
     */
    @Column(name = "total_invested_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalInvestedAmount = BigDecimal.ZERO;

    /**
     * Weighted average NAV at which units were purchased.
     * = total_invested_amount / total_units
     * Useful for showing customers their average cost per unit.
     */
    @Column(name = "avg_purchase_nav", nullable = false, precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal avgPurchaseNav = BigDecimal.ZERO;

    /**
     * Y = One or more transactions for this holding are estimated
     *     (derived from CAS opening balance, not actual purchase history).
     *     UI shows "Estimated" warning badge for this holding.
     *     XIRR for this holding is approximate.
     * N = All transactions are actual with verified purchase NAVs.
     */
    @Convert(converter = YNBooleanConverter.class)
    @Column(name = "has_estimated_data", nullable = false, length = 1, columnDefinition = "CHAR(1) DEFAULT 'N'")
    @Builder.Default
    private Boolean hasEstimatedData = false;

    /**
     * Y = Logically deleted  |  N = Active
     * Set to 'Y' when total_units reaches zero (full redemption).
     */
    @Convert(converter = YNBooleanConverter.class)
    @Column(name = "del_flg", nullable = false, length = 1, columnDefinition = "CHAR(1) DEFAULT 'N'")
    @Builder.Default
    private Boolean delFlg = false;

    @Column(name = "last_updated_at", nullable = false)
    private LocalDateTime lastUpdatedAt;

    // ── Lifecycle hooks ───────────────────────────────────────────────────────

    @PrePersist
    @PreUpdate
    protected void onSave() {
        this.lastUpdatedAt = LocalDateTime.now();
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    /**
     * Returns true if the customer has fully exited this scheme
     * (all units redeemed — total units is zero or negative).
     */
    public boolean isFullyRedeemed() {
        return this.totalUnits.compareTo(BigDecimal.ZERO) <= 0;
    }
}
