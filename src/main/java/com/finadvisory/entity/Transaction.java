package com.finadvisory.entity;

import com.finadvisory.config.YNBooleanConverter;
import com.finadvisory.entity.enums.TransactionSource;
import com.finadvisory.entity.enums.TransactionType;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Table: transactions
 * The single source of truth for all investment activity.
 * Every transaction — whether parsed from a CAS PDF or manually entered
 * by an admin — lives here.
 *
 * Key rules:
 *  - amount and units are NEGATIVE for REDEMPTION and SWITCH_OUT (outflows)
 *  - is_estimated = 'Y' for OPENING_BALANCE entries (from CAS) — triggers
 *    "Estimated" badge in the UI and excludes from precise XIRR calculation
 *  - source = 'CAS' for parsed entries, 'MANUAL' for admin entries
 *  - cas_upload_id is only set when source = 'CAS'
 *
 * Composite index on (user_id, scheme_id) optimises portfolio queries.
 */
@Entity
@Table(
    name = "transactions",
    indexes = {
        @Index(name = "idx_txn_user",       columnList = "user_id"),
        @Index(name = "idx_txn_scheme",     columnList = "scheme_id"),
        @Index(name = "idx_txn_date",       columnList = "transaction_date"),
        @Index(name = "idx_txn_user_scheme",columnList = "user_id, scheme_id")
    }
)
@SQLRestriction("del_flg = 'N'")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * The customer this transaction belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    /**
     * The mutual fund scheme of this transaction.
     * ON DELETE RESTRICT — scheme cannot be deleted if transactions exist.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scheme_id", referencedColumnName = "id", nullable = false)
    private MfScheme scheme;

    /**
     * Folio number under which this investment is held.
     * A customer may have multiple folios for the same scheme.
     * Example: "12345678/90"
     */
    @Column(name = "folio_number", length = 50)
    private String folioNumber;

    /**
     * The actual date of the transaction (not the entry date).
     * For SIPs this is the SIP instalment date.
     * Used as the cash flow date for XIRR calculation.
     */
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    /**
     * Type of transaction. See TransactionType enum for full list.
     * REDEMPTION and SWITCH_OUT will have negative amount and units.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 25)
    private TransactionType transactionType;

    /**
     * Transaction amount in INR.
     * POSITIVE for purchases/SIP/switch-in/dividend-reinvest.
     * NEGATIVE for redemptions/switch-out.
     * Zero for BONUS units.
     * Always use BigDecimal — never float or double for financial values.
     */
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    /**
     * Number of units transacted.
     * POSITIVE for inflows, NEGATIVE for outflows (redemptions).
     * DECIMAL(15,4) — mutual fund units can have up to 4 decimal places.
     */
    @Column(name = "units", nullable = false, precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal units = BigDecimal.ZERO;

    /**
     * NAV at the time of the transaction.
     * For CAS-parsed transactions: extracted directly from the statement.
     * For manual entries: entered by admin (can be auto-filled from nav_history
     * using the transaction_date if available).
     */
    @Column(name = "nav_at_transaction", nullable = false, precision = 15, scale = 4)
    private BigDecimal navAtTransaction;

    /**
     * CAS = Parsed from CAS PDF upload
     * MANUAL = Entered manually by admin
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private TransactionSource source;

    /**
     * Y = This is an estimated entry created from a CAS "Opening Balance".
     *     The opening balance gives current units but no purchase history,
     *     so the NAV used is an estimate.
     *     UI shows "Estimated" badge for schemes with such entries.
     *     XIRR is flagged as approximate when this is Y.
     * N = Actual transaction with real purchase NAV.
     */
    @Convert(converter = YNBooleanConverter.class)
    @Column(name = "is_estimated", nullable = false, length = 1, columnDefinition = "CHAR(1) DEFAULT 'N'")
    @Builder.Default
    private Boolean isEstimated = false;

    /**
     * Link back to the CAS upload that generated this transaction.
     * Null for MANUAL source entries.
     * Allows tracing: "which CAS file did this transaction come from?"
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cas_upload_id", referencedColumnName = "id")
    private CasUpload casUpload;

    /**
     * Admin notes for manual entries.
     * Example: "Lumpsum investment April 2026 — client called on 10th"
     */
    @Column(name = "notes", length = 500)
    private String notes;

    /**
     * Y = Logically deleted  |  N = Active
     * Deleting a transaction triggers a portfolio_holdings recalculation.
     */
    @Convert(converter = YNBooleanConverter.class)
    @Column(name = "del_flg", nullable = false, length = 1, columnDefinition = "CHAR(1) DEFAULT 'N'")
    @Builder.Default
    private Boolean delFlg = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ── Lifecycle hooks ───────────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    /**
     * Returns true if this transaction is an outflow (redemption or switch-out).
     */
    public boolean isOutflow() {
        return TransactionType.REDEMPTION.equals(this.transactionType)
            || TransactionType.SWITCH_OUT.equals(this.transactionType);
    }

    /**
     * Returns true if this transaction came from a CAS file.
     */
    public boolean isFromCas() {
        return TransactionSource.CAS.equals(this.source);
    }
}
