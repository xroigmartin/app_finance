package com.xroig.finance.config;

import com.xroig.finance.accounts.application.port.CreateAccount;
import com.xroig.finance.accounts.application.port.CreateAccount.CreateAccountCommand;
import com.xroig.finance.accounts.application.port.FindAccounts;
import com.xroig.finance.categories.application.CategoryView;
import com.xroig.finance.categories.application.port.CreateCategory;
import com.xroig.finance.categories.application.port.CreateCategory.CreateCategoryCommand;
import com.xroig.finance.categories.application.port.FindCategories;
import com.xroig.finance.shared.domain.Money;
import com.xroig.finance.shared.domain.TransactionType;
import com.xroig.finance.transactions.application.port.CreateTransaction;
import com.xroig.finance.transactions.application.port.CreateTransaction.TransactionCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Seeds default categories on first run, plus demo data when {@code finance.seed-demo=true}.
 * Orchestrates the contexts' own create use cases (so the same invariants apply as for any
 * user input); idempotency is guarded by the contexts' "is it empty?" reads.
 */
@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seed(CreateAccount createAccount, FindAccounts findAccounts,
                           CreateCategory createCategory, FindCategories findCategories,
                           CreateTransaction createTransaction,
                           @Value("${finance.seed-demo}") boolean demo) {
        return args -> {
            if (findCategories.all().isEmpty()) {
                seedCategories(createCategory);
            }
            if (demo && findAccounts.all().isEmpty()) {
                seedDemo(createAccount, createCategory, findCategories, createTransaction);
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

    private void seedDemo(CreateAccount createAccount, CreateCategory createCategory,
                          FindCategories findCategories, CreateTransaction createTransaction) {
        long mainId = createAccount.create(
                new CreateAccountCommand("Cuenta corriente", "Banco", Money.of(new BigDecimal("2500.00"))))
                .id().value();
        createAccount.create(
                new CreateAccountCommand("Ahorro", "Banco", Money.of(new BigDecimal("8000.00"))));

        Map<String, Long> byName = findCategories.all().stream()
                .collect(Collectors.toMap(CategoryView::name, CategoryView::id, (a, b) -> a));

        // Categoría propia de la cuenta corriente, para mostrar el ámbito por cuenta.
        long lightId = createCategory.create(
                new CreateCategoryCommand("Luz", TransactionType.EXPENSE, "#eab308", null, mainId)).id();

        Random random = new Random(42);
        YearMonth current = YearMonth.now();
        for (int i = 11; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            add(createTransaction, mainId, byName.get("Nómina"), TransactionType.INCOME,
                    ym.atDay(1), new BigDecimal("2150.00"), "Nómina mensual");
            add(createTransaction, mainId, byName.get("Vivienda"), TransactionType.EXPENSE,
                    ym.atDay(2), new BigDecimal("780.00"), "Alquiler");
            add(createTransaction, mainId, byName.get("Suscripciones"), TransactionType.EXPENSE,
                    ym.atDay(5), new BigDecimal("34.99"), "Streaming y software");
            add(createTransaction, mainId, lightId, TransactionType.EXPENSE,
                    ym.atDay(8), amount(random, 40, 80), "Factura de luz");
            for (int week = 0; week < 4; week++) {
                LocalDate day = ym.atDay(Math.min(3 + week * 7, ym.lengthOfMonth()));
                if (day.isAfter(LocalDate.now())) {
                    continue;
                }
                add(createTransaction, mainId, byName.get("Alimentación"), TransactionType.EXPENSE,
                        day, amount(random, 45, 120), "Supermercado");
            }
            LocalDate leisureDay = ym.atDay(Math.min(15, ym.lengthOfMonth()));
            if (!leisureDay.isAfter(LocalDate.now())) {
                add(createTransaction, mainId, byName.get("Ocio"), TransactionType.EXPENSE,
                        leisureDay, amount(random, 30, 150), "Ocio");
                add(createTransaction, mainId, byName.get("Transporte"), TransactionType.EXPENSE,
                        leisureDay, amount(random, 40, 90), "Transporte");
            }
        }
    }

    private BigDecimal amount(Random random, int min, int max) {
        return BigDecimal.valueOf(min + random.nextInt(max - min) + random.nextInt(100) / 100.0)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private void add(CreateTransaction createTransaction, long accountId, long categoryId,
                     TransactionType type, LocalDate date, BigDecimal amount, String description) {
        if (date.isAfter(LocalDate.now())) {
            return;
        }
        createTransaction.create(new TransactionCommand(date, amount, description, type,
                accountId, categoryId, null));
    }

    /** Silences the unused-import warning kept tidy: {@link Function} not needed. */
    private static <T> T identity(T value) {
        return value;
    }
}
