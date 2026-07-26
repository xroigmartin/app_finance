package com.xroig.finance.investments.infrastructure.persistence;

import com.xroig.finance.investments.domain.ImportRecord;
import com.xroig.finance.investments.domain.ImportRowIssue;
import com.xroig.finance.investments.domain.PortfolioId;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Translates between the pure {@link ImportRecord} aggregate and its
 * {@link ImportRecordJpaEntity}: {@code errors}/{@code warnings} are serialized
 * to/from the {@code jsonb} columns with the project's {@code ObjectMapper} bean
 * (§2.3 of docs/plan/historial-imports.md) — a corrupted row fails fast, same as
 * every other rehydration in the context.
 */
@Component
public class ImportRecordJpaMapper {

    private static final TypeReference<List<ImportRowIssue>> ERRORS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> WARNINGS_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public ImportRecordJpaMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ImportRecord toDomain(ImportRecordJpaEntity entity) {
        return new ImportRecord(entity.getId(), new PortfolioId(entity.getPortfolioId()), entity.getImportedAt(),
                entity.getFileName(), entity.getFromDate(), entity.getToDate(),
                entity.getImportedCount(), entity.getDuplicatedCount(),
                readJson(entity.getErrors(), ERRORS_TYPE), readJson(entity.getWarnings(), WARNINGS_TYPE));
    }

    public ImportRecordJpaEntity toJpa(ImportRecord record) {
        ImportRecordJpaEntity entity = new ImportRecordJpaEntity();
        if (record.id() != null) {
            entity.setId(record.id());
        }
        entity.setPortfolioId(record.portfolioId().value());
        entity.setImportedAt(record.importedAt());
        entity.setFileName(record.fileName());
        entity.setFromDate(record.fromDate());
        entity.setToDate(record.toDate());
        entity.setImportedCount(record.imported());
        entity.setDuplicatedCount(record.duplicated());
        entity.setErrors(objectMapper.writeValueAsString(record.errors()));
        entity.setWarnings(objectMapper.writeValueAsString(record.warnings()));
        return entity;
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        return objectMapper.readValue(json, type);
    }
}
