package com.xroig.finance.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Fences the hexagonal + DDD boundaries the single Maven module cannot enforce at
 * compile time. With the migration complete, every bounded context lives in the
 * layered packages ({@code ..domain.. / ..application.. / ..infrastructure..}) the
 * rules are scoped to; the only code outside them is the {@code config} bootstrap
 * ({@code DataSeeder}), which wires use cases at startup. Each rule now matches real
 * classes, so the previous {@code archRule.failOnEmptyShould=false} escape hatch is gone.
 */
@AnalyzeClasses(packages = "com.xroig.finance", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_is_pure_of_frameworks = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "jakarta.validation..");

    @ArchTest
    static final ArchRule domain_does_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..");

    @ArchTest
    static final ArchRule application_does_not_depend_on_infrastructure = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..");

    /**
     * Within a context the inbound web adapter must reach the application through its
     * ports, never the persistence adapter/JPA directly (it would couple the two
     * sides of the hexagon). Matches once a context like {@code accounts} exists.
     */
    @ArchTest
    static final ArchRule web_adapters_do_not_depend_on_persistence = noClasses()
            .that().resideInAPackage("..infrastructure.web..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure.persistence..");
}
