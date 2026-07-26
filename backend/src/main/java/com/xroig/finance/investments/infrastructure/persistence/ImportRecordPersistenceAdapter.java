package com.xroig.finance.investments.infrastructure.persistence;

import com.xroig.finance.investments.domain.ImportRecord;
import com.xroig.finance.investments.domain.ImportRecordRepository;
import org.springframework.stereotype.Component;

/** Persistence adapter: implements the {@link ImportRecordRepository} outbound port over JPA. */
@Component
public class ImportRecordPersistenceAdapter implements ImportRecordRepository {

    private final ImportRecordJpaRepository jpa;
    private final ImportRecordJpaMapper mapper;

    public ImportRecordPersistenceAdapter(ImportRecordJpaRepository jpa, ImportRecordJpaMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public ImportRecord save(ImportRecord record) {
        return mapper.toDomain(jpa.save(mapper.toJpa(record)));
    }
}
