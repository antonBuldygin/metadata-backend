package ru.company.metadatabackend.service.rules;

import java.util.Map;

public interface EntityRule {
    default void beforeCreate(Map<String, Object> record, Map<String, Object> oldRecord) throws Exception {}
    default void beforeUpdate(Map<String, Object> record, Map<String, Object> oldRecord) throws Exception {}
    default void beforeDelete(Map<String, Object> record, Map<String, Object> oldRecord) throws Exception {}
}