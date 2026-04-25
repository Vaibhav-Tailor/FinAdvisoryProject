package com.finadvisory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import com.finadvisory.config.YNBooleanConverter;

import java.time.LocalDateTime;

/**
 * Table: password_reset_tokens
 * Manages secure email-based password reset flow.
 *
 * Flow:
 *  1. Customer clicks "Forgot Password" → enters registered email
 *  2. System generates a UUID token → stores here → emails reset link
 *  3. Customer clicks link → system validates token (not expired, not used)
 *  4. Customer sets new password → token marked is_used = 'Y'
 *
 * Security rules enforced:
 *  - Token expires after 24 hours (expires_at)
 *  - Token is single-use (is_used = 'Y' after consumption)
 *  - Token is a UUID — cryptographically random, not guessable
 *  - Email always returns same generic message to prevent email enumeration
 */
@Entity
@Table(
    name = "password_reset_tokens",
    indexes = {
        @Index(name = "idx_prt_token",   columnList = "token"),
        @Index(name = "idx_prt_user_id", columnList = "user_id")
    }
)
@SQLRestriction("del_flg = 'N'")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * The user who requested the password reset.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    /**
     * UUID-based secure reset token.
     * Generated via UUID.randomUUID().toString()
     * Sent as a URL query parameter in the reset email.
     * Example: "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
     * UNIQUE constraint prevents collision.
     */
    @Column(name = "token", nullable = false, unique = true, length = 255)
    private String token;

    /**
     * Token expiry timestamp — 24 hours from creation.
     * System rejects tokens where LocalDateTime.now().isAfter(expiresAt)
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Y = Token has already been used to reset a password (consumed).
     *     Cannot be reused even if not yet expired.
     * N = Token is still valid and unused.
     */
    @Convert(converter = YNBooleanConverter.class)
    @Column(name = "is_used", nullable = false, length = 1, columnDefinition = "CHAR(1) DEFAULT 'N'")
    @Builder.Default
    private Boolean isUsed = false;

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
        if (this.expiresAt == null) {
            this.expiresAt = LocalDateTime.now().plusHours(24);
        }
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    /**
     * Returns true if this token is still valid — not expired and not used.
     */
    public boolean isValid() {
        return !this.isUsed
            && this.expiresAt != null
            && LocalDateTime.now().isBefore(this.expiresAt);
    }

    /**
     * Returns true if this token has expired.
     */
    public boolean isExpired() {
        return this.expiresAt != null && LocalDateTime.now().isAfter(this.expiresAt);
    }
}
