package com.finbase.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Money is BigDecimal, never a binary floating-point type. See CLAUDE.md /
 * tech-stack doc §2.1 (week-one checklist, item 2).
 */
class MoneyTypeArchTest {

    @Test
    void noFloatOrDoubleOnEntityDtoOrServiceFields() {
        ArchRule rule = noFields()
                .that().areDeclaredInClassesThat().resideInAnyPackage("..entity..", "..dto..", "..service..")
                .should().haveRawType(double.class)
                .orShould().haveRawType(float.class)
                .orShould().haveRawType(Double.class)
                .orShould().haveRawType(Float.class)
                .because("money is BigDecimal, never a binary floating-point type")
                .allowEmptyShould(true); // no entity/dto/service classes exist yet in week 1

        rule.check(new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.finbase"));
    }
}
