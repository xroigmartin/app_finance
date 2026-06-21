package com.xroig.finance.config;

import com.xroig.finance.model.Account;
import com.xroig.finance.model.Category;
import com.xroig.finance.model.Transaction;
import com.xroig.finance.model.TransactionType;
import com.xroig.finance.repository.AccountRepository;
import com.xroig.finance.repository.CategoryRepository;
import com.xroig.finance.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Random;

/**
 * Seeds default categories on first run, plus demo data when finance.seed-demo=true.
 */
@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seed(AccountRepository accounts,
                           CategoryRepository categories,
                           TransactionRepository transactions,
                           @Value("${finance.seed-demo}") boolean demo) {
        return args -> {
            if (categories.count() == 0) {
                seedCategories(categories);
            }
            if (demo && accounts.count() == 0) {
                seedDemo(accounts, categories, transactions);
            }
        };
    }

    private void seedCategories(CategoryRepository categories) {
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
            Category category = new Category();
            category.setName(seed.name());
            category.setType(seed.type());
            category.setColor(seed.color());
            categories.save(category);
        }
    }

    private void seedDemo(AccountRepository accounts,
                          CategoryRepository categories,
                          TransactionRepository transactions) {
        Account main = new Account();
        main.setName("Cuenta corriente");
        main.setType("Banco");
        main.setInitialBalance(new BigDecimal("2500.00"));
        accounts.save(main);

        Account savings = new Account();
        savings.setName("Ahorro");
        savings.setType("Banco");
        savings.setInitialBalance(new BigDecimal("8000.00"));
        accounts.save(savings);

        var byName = categories.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Category::getName, c -> c));

        // Categoría propia de la cuenta corriente, para mostrar el ámbito por cuenta.
        Category light = new Category();
        light.setName("Luz");
        light.setType(TransactionType.EXPENSE);
        light.setColor("#eab308");
        light.setAccount(main);
        categories.save(light);

        Random random = new Random(42);
        YearMonth current = YearMonth.now();
        for (int i = 11; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            add(transactions, main, byName.get("Nómina"), TransactionType.INCOME,
                    ym.atDay(1), new BigDecimal("2150.00"), "Nómina mensual");
            add(transactions, main, byName.get("Vivienda"), TransactionType.EXPENSE,
                    ym.atDay(2), new BigDecimal("780.00"), "Alquiler");
            add(transactions, main, byName.get("Suscripciones"), TransactionType.EXPENSE,
                    ym.atDay(5), new BigDecimal("34.99"), "Streaming y software");
            add(transactions, main, light, TransactionType.EXPENSE,
                    ym.atDay(8), amount(random, 40, 80), "Factura de luz");
            for (int week = 0; week < 4; week++) {
                LocalDate day = ym.atDay(Math.min(3 + week * 7, ym.lengthOfMonth()));
                if (day.isAfter(LocalDate.now())) {
                    continue;
                }
                add(transactions, main, byName.get("Alimentación"), TransactionType.EXPENSE,
                        day, amount(random, 45, 120), "Supermercado");
            }
            LocalDate leisureDay = ym.atDay(Math.min(15, ym.lengthOfMonth()));
            if (!leisureDay.isAfter(LocalDate.now())) {
                add(transactions, main, byName.get("Ocio"), TransactionType.EXPENSE,
                        leisureDay, amount(random, 30, 150), "Ocio");
                add(transactions, main, byName.get("Transporte"), TransactionType.EXPENSE,
                        leisureDay, amount(random, 40, 90), "Transporte");
            }
        }
    }

    private BigDecimal amount(Random random, int min, int max) {
        return BigDecimal.valueOf(min + random.nextInt(max - min) + random.nextInt(100) / 100.0)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private void add(TransactionRepository transactions, Account account, Category category,
                     TransactionType type, LocalDate date, BigDecimal amount, String description) {
        if (date.isAfter(LocalDate.now())) {
            return;
        }
        Transaction transaction = new Transaction();
        transaction.setAccount(account);
        transaction.setCategory(category);
        transaction.setType(type);
        transaction.setDate(date);
        transaction.setAmount(amount);
        transaction.setDescription(description);
        transactions.save(transaction);
    }
}
