package com.finadvisory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import com.finadvisory.config.YNBooleanConverter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Table: mf_schemes
 * AMFI Mutual Fund Scheme Master — loaded from:
 * https://www.amfiindia.com/spages/NAVAll.txt
 *
 * Populated once on application startup by NavSyncScheduler.
 * Refreshed daily at 11 PM along with NAV data.
 * Contains ~18,000 schemes.
 *
 * FULLTEXT index on (scheme_name, amc_name) enables fast
 * search-as-you-type when admin adds a manual transaction.
 */
@Entity
@Table(
    name = "mf_schemes",
    indexes = {
        @Index(name = "idx_isin_growth",    columnList = "isin_growth"),
        @Index(name = "idx_isin_div",       columnList = "isin_div_reinvest"),
        @Index(name = "idx_amfi_code",      columnList = "amfi_code")
    }
)
@SQLRestriction("del_flg = 'N'")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MfScheme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * AMFI Scheme Code — unique numeric code assigned by AMFI.
     * Used as the primary reference when parsing NAVAll.txt.
     * Example: "120503" for HDFC Mid-Cap Opportunities Fund - Growth
     */
    @Column(name = "amfi_code", nullable = false, unique = true, length = 20)
    private String amfiCode;

    /**
     * ISIN for Growth / Dividend Payout plan.
     * Format: IN + 10 alphanumeric characters (e.g., INF179KB1DY2)
     * Used during CAS parsing to identify schemes from transaction data.
     */
    @Column(name = "isin_growth", length = 20)
    private String isinGrowth;

    /**
     * ISIN for Dividend Reinvestment plan.
     * Some schemes have a separate ISIN for the reinvestment variant.
     */
    @Column(name = "isin_div_reinvest", length = 20)
    private String isinDivReinvest;

    /**
     * Full scheme name as published by AMFI.
     * Example: "HDFC Mid-Cap Opportunities Fund - Direct Plan - Growth Option"
     * Length 500 to accommodate verbose scheme names.
     */
    @Column(name = "scheme_name", nullable = false, length = 500)
    private String schemeName;

    /**
     * Asset Management Company name.
     * Example: "HDFC Mutual Fund", "SBI Mutual Fund"
     */
    @Column(name = "amc_name", length = 255)
    private String amcName;

    /**
     * SEBI category of the scheme.
     * Example: "Equity Scheme - Mid Cap Fund",
     *          "Debt Scheme - Banking and PSU Fund"
     */
    @Column(name = "category", length = 100)
    private String category;

    /**
     * Open Ended / Close Ended / Interval Fund
     */
    @Column(name = "scheme_type", length = 50)
    private String schemeType;

    /**
     * Y = Scheme is currently active and accepting investments.
     * N = Scheme has been wound up or discontinued by AMC.
     */
    @Convert(converter = YNBooleanConverter.class)
    @Column(name = "is_active", nullable = false, length = 1, columnDefinition = "CHAR(1) DEFAULT 'Y'")
    @Builder.Default
    private Boolean isActive = true;

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

    // ── Relationships ─────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "scheme", fetch = FetchType.LAZY)
    private List<NavHistory> navHistories;

    @OneToMany(mappedBy = "scheme", fetch = FetchType.LAZY)
    private List<Transaction> transactions;

    @OneToMany(mappedBy = "scheme", fetch = FetchType.LAZY)
    private List<PortfolioHolding> portfolioHoldings;

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
}
