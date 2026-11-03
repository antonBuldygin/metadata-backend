package ru.company.metadatabackend.service;

import org.springframework.stereotype.Service;
import ru.company.metadatabackend.service.rules.EntityRule;
import ru.company.metadatabackend.service.rules.EntityTable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RuleRegistry {
    private final Map<String, EntityRule> rules = new HashMap<>();

    public RuleRegistry(List<EntityRule> ruleBeans) {
        for (EntityRule rule : ruleBeans) {
            EntityTable annotation = rule.getClass().getAnnotation(EntityTable.class);
            if (annotation != null) {
                rules.put(annotation.value(), rule);
            }
        }
    }

    public EntityRule getRule(String tableName) {
        return rules.get(tableName);
    }
}