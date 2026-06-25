package com.example.mcpgateway;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .importPackages("com.example.mcpgateway");

    @Test
    void domainMustNotDependOnFrameworkOrInfrastructure() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "com.baomidou.mybatisplus..",
                        "..infrastructure.."
                )
                .check(classes);
    }

    @Test
    void applicationMustNotDependOnInboundAdapters() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAPackage("..adapter.in..")
                .check(classes);
    }
}

