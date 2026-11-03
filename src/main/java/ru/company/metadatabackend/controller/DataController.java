package ru.company.metadatabackend.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.company.metadatabackend.entity.FieldDef;
import ru.company.metadatabackend.entity.FieldType;
import ru.company.metadatabackend.entity.TableDef;
import ru.company.metadatabackend.repository.FieldDefRepository;
import ru.company.metadatabackend.repository.TableDefRepository;
import ru.company.metadatabackend.service.RuleRegistry;
import ru.company.metadatabackend.service.rules.EntityRule;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/data")
@CrossOrigin(origins = "http://localhost:3000")
public class DataController {

    private final JdbcTemplate jdbcTemplate;
    private final TableDefRepository tableRepo;
    private final FieldDefRepository fieldRepo;
    private final RuleRegistry ruleRegistry;

    public DataController(JdbcTemplate jdbcTemplate, TableDefRepository tableRepo, FieldDefRepository fieldRepo, RuleRegistry ruleRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableRepo = tableRepo;
        this.fieldRepo = fieldRepo;
        this.ruleRegistry = ruleRegistry;
    }

    @GetMapping("/{tableName}")
    public List<Map<String, Object>> getRecords(@PathVariable String tableName) {
        validateTable(tableName);
        TableDef table = getTable(tableName);
        List<FieldDef> fields = getFields(table);

        StringBuilder select = new StringBuilder("SELECT t.* ");
        StringBuilder joins = new StringBuilder();
        Map<String, String> aliases = new HashMap<>();

        for (FieldDef field : fields) {
            if (field.getType() == FieldType.RELATION) {
                String finalColumn = buildRelationJoin(field, "t", tableName, joins, aliases, 1);
                if (finalColumn != null) {
                    select.append(", ").append(finalColumn)
                            .append(" AS ").append(field.getSystemName()).append("_display ");
                }
            }
        }

        String finalSql = select.toString() + " FROM v_" + tableName + " t " + joins.toString();
        log.info("Выполняется SQL-запрос: {}", finalSql);

        try {
            return jdbcTemplate.queryForList(finalSql);
        } catch (Exception e) {
            log.error("Ошибка выполнения запроса: {}", finalSql, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка БД: " + e.getMessage());
        }
    }

    private String buildRelationJoin(FieldDef field, String sourceAlias, String sourceTable,
                                     StringBuilder joins, Map<String, String> aliases, int depth) {
        if (depth > 5) return null; // Защита от бесконечной рекурсии

        String targetTableName = field.getTargetTableName();
        if (targetTableName == null) return null;

        String alias = "rel_" + depth + "_" + field.getSystemName();

        if (!aliases.containsKey(alias)) {
            joins.append(" LEFT JOIN v_").append(targetTableName).append(" ").append(alias)
                    .append(" ON ").append(sourceAlias).append(".").append(field.getSystemName())
                    .append(" = ").append(alias).append(".id ");
            aliases.put(alias, targetTableName);
        }

        // ИСПРАВЛЕНИЕ: используем final-переменную для лямбды
        final String targetDisplayName = field.getTargetDisplayField() != null ? field.getTargetDisplayField() : "id";

        TableDef targetTable = getTable(targetTableName);
        List<FieldDef> targetFields = getFields(targetTable);

        Optional<FieldDef> displayFieldDef = targetFields.stream()
                .filter(f -> f.getSystemName().equalsIgnoreCase(targetDisplayName))
                .findFirst();

        if (displayFieldDef.isPresent() && displayFieldDef.get().getType() == FieldType.RELATION) {
            return buildRelationJoin(displayFieldDef.get(), alias, targetTableName, joins, aliases, depth + 1);
        } else {
            return alias + "." + targetDisplayName;
        }
    }

    @PostMapping("/{tableName}")
    public void createRecord(@PathVariable String tableName, @RequestBody Map<String, Object> payload) {
        validateTable(tableName);
        TableDef table = getTable(tableName);
        List<FieldDef> fields = getFields(table);

        EntityRule rule = ruleRegistry.getRule(tableName);
        if (rule != null) {
            try { rule.beforeCreate(payload, null); }
            catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Правило не пройдено: " + e.getMessage()); }
        }

        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        List<Object> params = new ArrayList<>();

        for (FieldDef field : fields) {
            // ИСПРАВЛЕНИЕ: убрали лямбду, используем обычный цикл
            Object val = null;
            boolean found = false;
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(field.getSystemName())) {
                    val = entry.getValue();
                    found = true;
                    break;
                }
            }

            if (found) {
                columns.append(field.getSystemName()).append(",");
                values.append("?,");
                params.add(convertValue(val, field.getType()));
            }
        }

        if (columns.length() == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Нет данных");
        columns.setLength(columns.length() - 1);
        values.setLength(values.length() - 1);

        String sql = "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + values + ")";
        try {
            jdbcTemplate.update(sql, params.toArray());
        } catch (Exception e) {
            log.error("Ошибка при вставке", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка БД: " + e.getMessage());
        }
    }

    @PutMapping("/{tableName}/{id}")
    public void updateRecord(@PathVariable String tableName, @PathVariable Long id, @RequestBody Map<String, Object> payload) {
        validateTable(tableName);
        TableDef table = getTable(tableName);
        List<FieldDef> fields = getFields(table);

        Long currentVersion = jdbcTemplate.queryForObject("SELECT version FROM " + tableName + " WHERE id = ?", Long.class, id);
        Long clientVersion = Long.parseLong(payload.get("version").toString());
        if (currentVersion == null || !currentVersion.equals(clientVersion)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Запись была изменена другим пользователем.");
        }

        EntityRule rule = ruleRegistry.getRule(tableName);
        if (rule != null) {
            try {
                Map<String, Object> oldRecord = jdbcTemplate.queryForMap("SELECT * FROM " + tableName + " WHERE id = ?", id);
                rule.beforeUpdate(payload, oldRecord);
            } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Правило не пройдено: " + e.getMessage()); }
        }

        StringBuilder setClause = new StringBuilder();
        List<Object> params = new ArrayList<>();

        for (FieldDef field : fields) {
            // ИСПРАВЛЕНИЕ: убрали лямбду, используем обычный цикл
            Object val = null;
            boolean found = false;
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(field.getSystemName())) {
                    val = entry.getValue();
                    found = true;
                    break;
                }
            }

            if (found) {
                setClause.append(field.getSystemName()).append(" = ?, ");
                params.add(convertValue(val, field.getType()));
            }
        }

        if (setClause.length() == 0) return;
        setClause.append("version = version + 1, updated_at = CURRENT_TIMESTAMP");

        String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE id = ? AND version = ?";
        params.add(id);
        params.add(clientVersion);

        try {
            int rows = jdbcTemplate.update(sql, params.toArray());
            if (rows == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Конфликт версий.");
        } catch (Exception e) {
            if (e instanceof ResponseStatusException) throw (ResponseStatusException) e;
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка БД: " + e.getMessage());
        }
    }

    @DeleteMapping("/{tableName}/{id}")
    public void deleteRecord(@PathVariable String tableName, @PathVariable Long id) {
        validateTable(tableName);
        jdbcTemplate.update("DELETE FROM " + tableName + " WHERE id = ?", id);
    }

    private void validateTable(String tableName) {
        if (tableRepo.findAll().stream().noneMatch(t -> t.getSystemName().equals(tableName))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Таблица не найдена");
        }
    }

    private TableDef getTable(String tableName) {
        return tableRepo.findAll().stream().filter(t -> t.getSystemName().equals(tableName)).findFirst().orElseThrow();
    }

    private List<FieldDef> getFields(TableDef table) {
        return fieldRepo.findAll().stream().filter(f -> f.getTable().getId().equals(table.getId())).toList();
    }

    private Object convertValue(Object raw, FieldType type) {
        if (raw == null) return null;
        String str = raw.toString().trim();
        if (str.isEmpty()) return null;
        try {
            return switch (type) {
                case INTEGER -> Integer.parseInt(str);
                case LONG, RELATION -> Long.parseLong(str);
                case DECIMAL -> new BigDecimal(str);
                case BOOLEAN -> Boolean.parseBoolean(str);
                case DATE -> java.time.LocalDate.parse(str);
                case DATETIME -> java.time.LocalDateTime.parse(str);
                case STRING, TEXT -> str;
            };
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверный формат данных: " + str);
        }
    }
}