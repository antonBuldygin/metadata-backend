package ru.company.metadatabackend.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.company.metadatabackend.AbstractIntegrationTest;
import ru.company.metadatabackend.service.rules.EntityRule;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuleRegistryTest extends AbstractIntegrationTest {

    @Autowired
    private RuleRegistry ruleRegistry;

    @Test
    void registryContainsTestRule() {
        // Проверяем, что тестовое правило (созданное в src/test/java/.../TestBusinessRule.java)
        // зарегистрировалось автоматически
        EntityRule rule = ruleRegistry.getRule("test_ruled_table");
        assertThat(rule).isNotNull();
    }

    @Test
    void testRuleCanThrowException() {
        EntityRule rule = ruleRegistry.getRule("test_ruled_table");

        Map<String, Object> record = Map.of("amount", -100);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> rule.beforeCreate(record, null));

        assertThat(ex.getMessage()).contains("Отрицательное значение");
    }

    @Test
    void testRulePassesForValidData() throws Exception {
        EntityRule rule = ruleRegistry.getRule("test_ruled_table");
        Map<String, Object> record = Map.of("amount", 100);

        // Не должно бросить исключение
        rule.beforeCreate(record, null);
    }
}