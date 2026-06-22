package com.xroig.finance.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Fences the hexagonal + DDD boundaries the single Maven module cannot enforce at
 * compile time. The rules are scoped to the <b>new</b> layered packages
 * ({@code ..domain.. / ..application.. / ..infrastructure..}); the legacy packages
 * ({@code model}, {@code controller}, {@code service}, {@code repository}, {@code dto},
 * {@code config}) carry no such segment, so they are excluded for free while the
 * migration is in flight. Rules that still match nothing pass thanks to
 * {@code archRule.failOnEmptyShould=false} (see {@code archunit.properties}).
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
}
