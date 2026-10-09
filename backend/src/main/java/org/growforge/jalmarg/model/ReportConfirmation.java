package org.growforge.jalmarg.model;

import jakarta.persistence.*;

import java.time.Instant;

/** One person's "still flooded" / "cleared" vote on a report. One vote per person per report. */
@Entity
@Table(name = "report_confirmations",
        uniqueConstraints = @UniqueConstraint(name = "uk_confirm_report_user", columnNames = {"report_id", "user_id"}))
public class ReportConfirmation {

    public enum Kind { STILL_FLOODED, CLEARED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    private Instant createdAt;

    public ReportConfirmation() {}

    public ReportConfirmation(Long reportId, Long userId, Kind kind, Instant createdAt) {
        this.reportId = reportId;
        this.userId = userId;
        this.kind = kind;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getReportId() { return reportId; }
    public Long getUserId() { return userId; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
