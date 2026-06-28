package com.xroig.finance.config;

import com.xroig.finance.categories.application.port.CreateCategory;
import com.xroig.finance.categories.application.port.CreateCategory.CreateCategoryCommand;
import com.xroig.finance.categories.application.port.FindCategories;
import com.xroig.finance.shared.domain.TransactionType;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seeds the default global categories on first run so a fresh database is usable out of
 * the box. Orchestrates the categories context's own create use case (so the same
 * invariants apply as for any user input); idempotency is guarded by the context's
 * "is it empty?" read.
 */
@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seed(CreateCategory createCategory, FindCategories findCategories) {
        return args -> {
            if (findCategories.all().isEmpty()) {
                seedCategories(createCategory);
            }
        };
    }

    private void seedCategories(CreateCategory createCategory) {
        record Seed(String name, TransactionType type, String color) {
        }
        var seeds = new Seed[]{
                new Seed("Nómina", TransactionType.INCOME, "#22c55e"),
                new Seed("Otros ingresos", TransactionType.INCOME, "#10b981"),
                new Seed("Vivienda", TransactionType.EXPENSE, "#6366f1"),
                new Seed("Alimentación", TransactionType.EXPENSE, "#f59e0b"),
                new Seed("Transporte", TransactionType.EXPENSE, "#3b82f6"),
                new Seed("Ocio", TransactionType.EXPENSE, "#ec4899"),
                new Seed("Salud", TransactionType.EXPENSE, "#ef4444"),
                new Seed("Suscripciones", TransactionType.EXPENSE, "#8b5cf6"),
                new Seed("Otros gastos", TransactionType.EXPENSE, "#64748b"),
        };
        for (Seed seed : seeds) {
            createCategory.create(new CreateCategoryCommand(seed.name(), seed.type(), seed.color(), null, null));
        }
    }
}
