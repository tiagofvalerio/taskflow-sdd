package com.taskflow.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Mechanical enforcement of the hexagonal rules from CLAUDE.md. Panache and
 * the JDBC driver are already on the compile classpath for the future
 * adapters, so an accidental {@code jakarta.persistence} import in the domain
 * would compile fine — these rules are what fail the build instead.
 */
@AnalyzeClasses(packages = "com.taskflow", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    private static final String[] FORBIDDEN_FRAMEWORK_PACKAGES = {
            "jakarta..",
            "io.quarkus..",
            "org.hibernate..",
            "com.fasterxml.."
    };

    @ArchTest
    static final ArchRule domainDependsOnNothingButItselfAndTheJdk =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            concat(FORBIDDEN_FRAMEWORK_PACKAGES, "..adapter..", "..application.."))
                    .because("the domain layer must have zero framework imports and"
                            + " must not know about outer layers (CLAUDE.md)");

    @ArchTest
    static final ArchRule applicationDependsOnlyOnDomainAndTheJdk =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            concat(FORBIDDEN_FRAMEWORK_PACKAGES, "..adapter.."))
                    .because("the application layer may depend on the domain, never on"
                            + " adapters or framework classes (CLAUDE.md)")
                    // package is an empty scaffold until the application layer lands
                    .allowEmptyShould(true);

    private static String[] concat(String[] base, String... extra) {
        String[] all = new String[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return all;
    }
}
