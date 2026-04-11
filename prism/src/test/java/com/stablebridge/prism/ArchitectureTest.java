package com.stablebridge.prism;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.stablebridge.prism")
class ArchitectureTest {

    private static final String BASE = "com.stablebridge.prism";

    @ArchTest
    static final ArchRule domainMustNotDependOnInfrastructure =
            noClasses().that().resideInAPackage(BASE + ".domain..")
                    .should().dependOnClassesThat().resideInAPackage(BASE + ".infrastructure..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainMustNotDependOnApplication =
            noClasses().that().resideInAPackage(BASE + ".domain..")
                    .should().dependOnClassesThat().resideInAPackage(BASE + ".application..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainMustNotImportFrameworks =
            noClasses().that().resideInAPackage(BASE + ".domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "io.helidon..", "jakarta..", "io.avaje..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainMustNotImportJdbc =
            noClasses().that().resideInAPackage(BASE + ".domain..")
                    .should().dependOnClassesThat().resideInAPackage("java.sql..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule infrastructureMustNotDependOnRouting =
            noClasses().that().resideInAPackage(BASE + ".infrastructure..")
                    .should().dependOnClassesThat().resideInAPackage(BASE + ".application.route..")
                    .allowEmptyShould(true);
}
