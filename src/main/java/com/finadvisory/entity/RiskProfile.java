package com.finadvisory.entity;

import com.finadvisory.config.YNBooleanConverter;
import com.finadvisory.entity.enums.*;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Table: risk_profiles
 * Manages customer investment risk assessments.
 * Full history is preserved — every assessment is stored as a new row.
 * Only one row per customer has is_current = 'Y' at any time.
 *
 * When a new assessment is done:
 *   1. UPDATE risk_profiles SET is_current = 'N' WHERE user_id = ? AND is_current = 'Y'
 *   2. INSERT new row with is_current = 'Y'
 */
@Entity
@Table(name = "risk_profiles")
@SQLRestriction("del_flg = 'N'")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * The customer whose risk profile this is.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    // ── Risk Classification ──────────────────────────────────────────────────

    /**
     * Risk category derived from risk_score:
     * 0–20  → CONSERVATIVE
     * 21–40 → MODERATE_CONSERVATIVE
     * 41–60 → MODERATE
     * 61–80 → MODERATE_AGGRESSIVE
     * 81–100→ AGGRESSIVE
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_category", nullable = false, length = 30)
    private RiskCategory riskCategory;

    /**
     * Numeric score 0–100 derived from questionnaire responses.
     * Kept as Integer (not Boolean) — it is a real numeric value.
     */
    @Column(name = "risk_score", nullable = false)
    private Integer riskScore;

    // ── Investment Profile ───────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "investment_horizon", nullable = false, length = 20)
    private InvestmentHorizon investmentHorizon;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_goal", length = 30)
    private PrimaryGoal primaryGoal;

    // ── Assessment Metadata ──────────────────────────────────────────────────

    /**
     * The admin/advisor who conducted this risk assessment.
     * Stored for audit trail — cannot be deleted (ON DELETE RESTRICT).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessed_by", referencedColumnName = "id", nullable = false)
    private User assessedBy;

    /**
     * Advisor's notes or observations from the assessment session.
     */
    @Column(name = "assessment_notes", columnDefinition = "TEXT")
    private String assessmentNotes;

    @Column(name = "assessed_at", nullable = false)
    private LocalDateTime assessedAt;

    /**
     * Date by which this risk profile should be reviewed again.
     * Typically set to 1 year from assessment date.
     * System will alert admin when approaching this date.
     */
    @Column(name = "valid_until")
    private LocalDate validUntil;

    /**
     * Y = This is the active/current risk profile for the customer.
     * N = Historical assessment (superseded by a newer one).
     * Only one row per customer can be 'Y' at a time.
     */
    @Convert(converter = YNBooleanConverter.class)
    @Column(name = "is_current", nullable = false, length = 1, columnDefinition = "CHAR(1) DEFAULT 'Y'")
    @Builder.Default
    private Boolean isCurrent = true;

    /**
     * Y = Logically deleted  |  N = Active
     */
    @Convert(converter = YNBooleanConverter.class)
    @Column(name = "del_flg", nullable = false, length = 1, columnDefinition = "CHAR(1) DEFAULT 'N'")
    @Builder.Default
    private Boolean delFlg = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ── Lifecycle hooks ──────────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        this.createdAt  = LocalDateTime.now();
        this.assessedAt = LocalDateTime.now();
    }

    // ── Helper methods ───────────────────────────────────────────────────────

    /**
     * Returns true if the risk profile review date has passed.
     */
    public boolean isReviewDue() {
        return this.validUntil != null && LocalDate.now().isAfter(this.validUntil);
    }
}
