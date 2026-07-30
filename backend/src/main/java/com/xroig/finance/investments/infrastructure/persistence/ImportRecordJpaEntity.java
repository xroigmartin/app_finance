package com.xroig.finance.investments.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Persistence entity of the investments context, mapped to
 * {@code investments.import_record} (owned by the V8 migration). {@code errors}
 * and {@code warnings} are stored as {@code jsonb} — serialized/deserialized by
 * {@link ImportRecordJpaMapper} with the project's {@code ObjectMapper} bean,
 * consistent with the rest of the context never using {@code @OneToMany} for
 * small, always-read-as-a-block collections (§2.3 of
 * docs/plan/historial-imports.md).
 */
@Entity
@Table(name = "import_record", schema = "investments")
public class ImportRecordJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "from_date")
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(name = "imported_count", nullable = false)
    private int importedCount;

    @Column(name = "duplicated_count", nullable = false)
    private int duplicatedCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String errors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String warnings;

    public ImportRecordJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(Instant importedAt) {
        this.importedAt = importedAt;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public int getImportedCount() {
        return importedCount;
    }

    public void setImportedCount(int importedCount) {
        this.importedCount = importedCount;
    }

    public int getDuplicatedCount() {
        return duplicatedCount;
    }

    public void setDuplicatedCount(int duplicatedCount) {
        this.duplicatedCount = duplicatedCount;
    }

    public String getErrors() {
        return errors;
    }

    public void setErrors(String errors) {
        this.errors = errors;
    }

    public String getWarnings() {
        return warnings;
    }

    public void setWarnings(String warnings) {
        this.warnings = warnings;
    }
}
