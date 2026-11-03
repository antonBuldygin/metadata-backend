package ru.company.metadatabackend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.company.metadatabackend.entity.FieldDef;
import ru.company.metadatabackend.entity.TableDef;
import ru.company.metadatabackend.repository.FieldDefRepository;
import ru.company.metadatabackend.repository.TableDefRepository;

import java.util.List;

@RestController
@RequestMapping("/api/tables")
@CrossOrigin(origins = "http://localhost:3000")
public class TableController {

    private final TableDefRepository tableRepo;
    private final FieldDefRepository fieldRepo;
    private final JdbcTemplate jdbcTemplate; // <-- Добавили для работы с DDL

    public TableController(TableDefRepository tableRepo, FieldDefRepository fieldRepo, JdbcTemplate jdbcTemplate) {
        this.tableRepo = tableRepo;
        this.fieldRepo = fieldRepo;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<TableDef> getAllTables() {
        return tableRepo.findAll();
    }

    @PostMapping
    public TableDef createTable(@RequestBody TableDef table) {
        table.setSystemName(table.getSystemName().toLowerCase());
        TableDef savedTable = tableRepo.save(table);


        // ДОБАВЛЯЕМ СИСТЕМНЫЕ ПОЛЯ:
        // version - для оптимистичной блокировки
        // created_at, updated_at - для аудита
        // В метод createTable после сохранения метаданных:
        if (!savedTable.getSystemName().matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недопустимое системное имя таблицы. Используйте только буквы, цифры и _");
        }
        String sql = "CREATE TABLE IF NOT EXISTS " + savedTable.getSystemName() + " (" + "id SERIAL PRIMARY KEY, " +
                "version BIGINT DEFAULT 1, " + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        try {
            jdbcTemplate.execute(sql);
            String viewSql = "CREATE OR REPLACE VIEW v_" + savedTable.getSystemName() + " AS SELECT * FROM " +
                    savedTable.getSystemName();
            jdbcTemplate.execute(viewSql);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Ошибка создания таблицы в БД: " + e.getMessage());
        }

        return savedTable;
    }

    @GetMapping("/{id}")
    public TableDef getTableById(@PathVariable Long id) {
        return tableRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found"));
    }

    @GetMapping("/{id}/fields")
    public List<FieldDef> getFieldsByTableId(@PathVariable Long id) {
        return fieldRepo.findAll().stream().filter(f -> f.getTable().getId().equals(id)).toList();
    }

    @PostMapping("/{id}/fields")
    public FieldDef createField(@PathVariable Long id, @RequestBody FieldDef field) {
        TableDef table = tableRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found"));
        field.setSystemName(field.getSystemName().toLowerCase()); // <-- ДОБАВИТЬ
        field.setTable(table);

        // 1. Сохраняем метаданные поля
        FieldDef savedField = fieldRepo.save(field);

        // 2. Добавляем физическую колонку в таблицу БД
        String dbType = switch (field.getType()) {
            case STRING -> "VARCHAR(255)";
            case TEXT -> "TEXT";
            case INTEGER -> "INTEGER";
            case LONG -> "BIGINT";
            case DECIMAL -> "DECIMAL(10,2)";
            case BOOLEAN -> "BOOLEAN";
            case DATE -> "DATE";
            case DATETIME -> "TIMESTAMP";
            case RELATION -> "BIGINT"; // <-- ДОБАВИЛИ ЭТО
        };
        // В метод createField перед ALTER TABLE:
        if (!field.getSystemName().matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недопустимое системное имя поля. Используйте только буквы, цифры и _");
        }
        String sql =
                "ALTER TABLE " + table.getSystemName() + " ADD COLUMN " + savedField.getSystemName() + " " + dbType;
        try {
            jdbcTemplate.execute(sql);
            // Пересоздаем вьюху, чтобы она увидела новую колонку
            String viewSql =
                    "CREATE OR REPLACE VIEW v_" + table.getSystemName() + " AS SELECT * FROM " + table.getSystemName();
            jdbcTemplate.execute(viewSql);

        } catch (Exception e) {
            fieldRepo.delete(savedField);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Ошибка добавления колонки в БД: " + e.getMessage());
        }

        return savedField;


    }
    // 1. Обновление поля (только отображаемое имя и видимость)
    @PutMapping("/{tableId}/fields/{fieldId}")
    public FieldDef updateField(@PathVariable Long tableId, @PathVariable Long fieldId, @RequestBody FieldDef fieldData) {
        FieldDef existing = fieldRepo.findById(fieldId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Поле не найдено"));

        // Обновляем только разрешенные поля
        existing.setDisplayName(fieldData.getDisplayName());
        existing.setVisibleOnDetails(fieldData.isVisibleOnDetails());
        existing.setVisibleOnList(fieldData.isVisibleOnList());
        if (fieldData.getTargetDisplayField() != null) {
            existing.setTargetDisplayField(fieldData.getTargetDisplayField());
        }

        return fieldRepo.save(existing);
    }

    // 2. Удаление поля (DROP COLUMN в БД и удаление из метаданных)
    @DeleteMapping("/{tableId}/fields/{fieldId}")
    public void deleteField(@PathVariable Long tableId, @PathVariable Long fieldId) {
        FieldDef field = fieldRepo.findById(fieldId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Поле не найдено"));

        TableDef table = field.getTable();
        // Приводим имя колонки к нижнему регистру, чтобы соответствовать PostgreSQL
        String columnName = field.getSystemName().toLowerCase();

        try {
            // Используем DROP COLUMN IF EXISTS, чтобы избежать ошибки, если колонки уже нет
            jdbcTemplate.execute("ALTER TABLE " + table.getSystemName() + " DROP COLUMN IF EXISTS " + columnName);

            // Пересоздаем вьюху
            String viewSql = "CREATE OR REPLACE VIEW v_" + table.getSystemName() + " AS SELECT * FROM " + table.getSystemName();
            jdbcTemplate.execute(viewSql);

            // Удаляем метаданные
            fieldRepo.delete(field);

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка удаления колонки: " + e.getMessage());
        }
    }
}