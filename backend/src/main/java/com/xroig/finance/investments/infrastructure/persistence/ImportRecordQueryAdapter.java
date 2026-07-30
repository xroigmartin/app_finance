package com.xroig.finance.investments.infrastructure.persistence;

import com.xroig.finance.investments.application.FlexRowError;
import com.xroig.finance.investments.application.ImportRecordQueryPort;
import com.xroig.finance.investments.application.ImportRecordView;
import com.xroig.finance.shared.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Read-side adapter (CQRS) for the import history (§6): reads
 * {@code investments.import_record} directly and deserializes errors/warnings
 * into {@link FlexRowError}/{@code String} — no aggregate reconstruction, this
 * is a listing, not a business calculation.
 */
@Component
public class ImportRecordQueryAdapter implements ImportRecordQueryPort {

    private static final TypeReference<List<FlexRowError>> ERRORS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> WARNINGS_TYPE = new TypeReference<>() {
    };

    private final ImportRecordJpaRepository jpa;
    private final ObjectMapper objectMapper;

    public ImportRecordQueryAdapter(ImportRecordJpaRepository jpa, ObjectMapper objectMapper) {
        this.jpa = jpa;
        this.objectMapper = objectMapper;
    }

    @Override
    public Page<ImportRecordView> history(long portfolioId, int page, int size) {
        org.springframework.data.domain.Page<ImportRecordJpaEntity> result =
                jpa.findByPortfolioIdOrderByImportedAtDesc(portfolioId, PageRequest.of(page, size));
        List<ImportRecordView> views = result.getContent().stream().map(this::toView).toList();
        return new Page<>(views, page, size, result.getTotalElements());
    }

    private ImportRecordView toView(ImportRecordJpaEntity entity) {
        return new ImportRecordView(entity.getId(), entity.getImportedAt(), entity.getFileName(),
                entity.getFromDate(), entity.getToDate(), entity.getImportedCount(), entity.getDuplicatedCount(),
                objectMapper.readValue(entity.getErrors(), ERRORS_TYPE),
                objectMapper.readValue(entity.getWarnings(), WARNINGS_TYPE));
    }
}
