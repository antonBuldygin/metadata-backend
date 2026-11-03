package ru.company.metadatabackend.service.rules;

import java.util.Map;

@EntityTable("test_ruled_table")
public class TestBusinessRule implements EntityRule {

    @Override
    public void beforeCreate(Map<String, Object> record, Map<String, Object> oldRecord) throws Exception {
        if (record.containsKey("amount")) {
            int amount = Integer.parseInt(record.get("amount").toString());
            if (amount < 0) {
                throw new RuntimeException("Отрицательное значение запрещено");
            }
        }
    }
}