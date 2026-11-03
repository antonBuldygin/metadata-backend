package ru.company.metadatabackend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.company.metadatabackend.entity.FieldDef;
import ru.company.metadatabackend.entity.TableDef;
import ru.company.metadatabackend.repository.FieldDefRepository;
import ru.company.metadatabackend.repository.TableDefRepository;
import ru.company.metadatabackend.service.RuleRegistry;
import ru.company.metadatabackend.service.rules.EntityRule;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    @SuppressWarnings("SqlResolve") // <-- Добавьте эту строку
    @GetMapping("/{tableName}")
    public List<Map<String, Object>> getRecords(@PathVariable String tableName) {
        validateTable(tableName);
        return jdbcTemplate.queryForList("SELECT * FROM v_" + tableName);
    }

    @PostMapping("/{tableName}")
    public void createRecord(@PathVariable String tableName, @RequestBody Map<String, Object> payload) {
        validateTable(tableName);
        TableDef table = getTable(tableName);
        List<FieldDef> fields = getFields(table);

        // 1. Выполняем Java-правило BEFORE_CREATE
        EntityRule rule = ruleRegistry.getRule(tableName);
        if (rule != null) {
            try { rule.beforeCreate(payload, null); }
            catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Правило не пройдено: " + e.getMessage()); }
        }

        // 2. Сборка SQL для INSERT
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        List<Object> params = new ArrayList<>();
        payload.forEach((k, v) -> {
            columns.append(k).append(",");
            values.append("?,");
            params.add(convertValue(v, fields, k));
        });
        columns.setLength(columns.length() - 1);
        values.setLength(values.length() - 1);

        String sql = "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + values + ")";
        try {
            jdbcTemplate.update(sql, params.toArray());
        } catch (Exception e) {
            e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка БД: " + e.getMessage());
        }
    }

    @PutMapping("/{tableName}/{id}")
    public void updateRecord(@PathVariable String tableName, @PathVariable Long id, @RequestBody Map<String, Object> payload) {
        validateTable(tableName);
        TableDef table = getTable(tableName);
        List<FieldDef> fields = getFields(table);

        // 1. Получаем текущую версию из БД
        Long currentVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM " + tableName + " WHERE id = ?", Long.class, id);

        Long clientVersion = Long.parseLong(payload.get("version").toString());
        if (currentVersion == null || !currentVersion.equals(clientVersion)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Запись была изменена другим пользователем. Обновите страницу.");
        }

        // 2. Выполняем Java-правило BEFORE_UPDATE
        EntityRule rule = ruleRegistry.getRule(tableName);
        if (rule != null) {
            try {
                Map<String, Object> oldRecord = jdbcTemplate.queryForMap("SELECT * FROM " + tableName + " WHERE id = ?", id);
                rule.beforeUpdate(payload, oldRecord);
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Правило не пройдено: " + e.getMessage());
            }
        }

        // 3. Сборка SQL для UPDATE
        StringBuilder setClause = new StringBuilder();
        List<Object> params = new ArrayList<>();

        payload.forEach((k, v) -> {
            if (!k.equals("id") && !k.equals("version") && !k.equals("created_at") && !k.equals("updated_at")) {
                setClause.append(k).append(" = ?, ");
                params.add(convertValue(v, fields, k));
            }
        });

        // Добавляем системные поля
        setClause.append("version = version + 1, updated_at = CURRENT_TIMESTAMP");

        String sql = "UPDATE " + tableName + " SET " + setClause + " WHERE id = ? AND version = ?";
        params.add(id);
        params.add(clientVersion);

        try {
            int rows = jdbcTemplate.update(sql, params.toArray());
            if (rows == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Конфликт версий при обновлении.");
            }
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

    // Вспомогательные методы
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

    private Object convertValue(Object raw, List<FieldDef> fields, String fieldName) {
        if (raw == null) return null;
        String str = raw.toString().trim();
        if (str.isEmpty()) return null;

        FieldDef field = fields.stream().filter(f -> f.getSystemName().equals(fieldName)).findFirst().orElse(null);
        if (field == null) return str; // Системное поле (version, id и т.д.)

        try {
            return switch (field.getType()) {
                case INTEGER -> Integer.parseInt(str);
                case LONG, RELATION -> Long.parseLong(str);
                case DECIMAL -> new BigDecimal(str);
                case BOOLEAN -> Boolean.parseBoolean(str);
                case DATE -> java.time.LocalDate.parse(str);
                case DATETIME -> java.time.LocalDateTime.parse(str);
                case STRING, TEXT -> str;
            };
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверный формат данных для поля " + fieldName);
        }
    }
}