package com.finadvisory.entity;

import com.finadvisory.config.YNBooleanConverter;
import com.finadvisory.entity.enums.UploadStatus;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Table: cas_uploads
 * Tracks the lifecycle of each CAS (Consolidated Account Statement) PDF
 * uploaded by a customer.
 *
 * Status flow:
 *   PENDING → PROCESSING → COMPLETED
 *                        → FAILED (with error_message populated)
 *
 * The actual PDF file is NOT stored in the database.
 * It is saved to the filesystem or S3/object storage.
 * This table only tracks metadata and processing status.
 */
@Entity
@Table(
    name = "cas_uploads",
    indexes = {
        @Index(name = "idx_cas_user", columnList = "user_id")
    }
)
@SQLRestriction("del_flg = 'N'")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CasUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * The customer who uploaded this CAS PDF.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    /**
     * Original file name of the uploaded PDF.
     * Example: "CAS_202412_CAMS.pdf"
     */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /**
     * Current processing status of this CAS upload.
     * PENDING     → Uploaded, queued for processing
     * PROCESSING  → PDFBox parsing in progress
     * COMPLETED   → All transactions successfully extracted and stored
     * FAILED      → Parsing failed (see error_message for details)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 20)
    @Builder.Default
    private UploadStatus uploadStatus = UploadStatus.PENDING;

    /**
     * Number of transaction records successfully parsed from this CAS.
     * Populated after COMPLETED status. Useful for verification.
     */
    @Column(name = "records_parsed")
    @Builder.Default
    private Integer recordsParsed = 0;

    /**
     * Error details if upload_status = FAILED.
     * Examples: "PDF password incorrect", "Unsupported CAS format", etc.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Y = Logically deleted  |  N = Active
     */
    @Convert(converter = YNBooleanConverter.class)
    @Column(name = "del_flg", nullable = false, length = 1, columnDefinition = "CHAR(1) DEFAULT 'N'")
    @Builder.Default
    private Boolean delFlg = false;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    /**
     * Timestamp when processing completed (success or failure).
     * Null while still in PENDING or PROCESSING status.
     */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    // ── Relationships ─────────────────────────────────────────────────────────

    /**
     * Transactions parsed out of this CAS upload.
     * Linked back here so we can trace which CAS each transaction came from.
     */
    @OneToMany(mappedBy = "casUpload", fetch = FetchType.LAZY)
    private List<Transaction> transactions;

    // ── Lifecycle hooks ───────────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    public boolean isCompleted() {
        return UploadStatus.COMPLETED.equals(this.uploadStatus);
    }

    public boolean isFailed() {
        return UploadStatus.FAILED.equals(this.uploadStatus);
    }

    public boolean isProcessing() {
        return UploadStatus.PROCESSING.equals(this.uploadStatus);
    }
}
