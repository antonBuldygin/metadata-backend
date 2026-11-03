package ru.company.metadatabackend.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import ru.company.metadatabackend.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class TableControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        // Удаляем созданные в тестах таблицы
        jdbcTemplate.execute("DROP TABLE IF EXISTS test_orders CASCADE");
        jdbcTemplate.execute("DROP VIEW IF EXISTS v_test_orders CASCADE");

        jdbcTemplate.execute("DROP TABLE IF EXISTS test_products CASCADE");
        jdbcTemplate.execute("DROP VIEW IF EXISTS v_test_products CASCADE");

        // Очищаем метаданные
        jdbcTemplate.update("DELETE FROM field_def WHERE table_id IN (SELECT id FROM table_def WHERE system_name IN ('test_orders', 'test_products'))");
        jdbcTemplate.update("DELETE FROM table_def WHERE system_name IN ('test_orders', 'test_products')");
    }

    @Test
    @DisplayName("Создание таблицы генерирует физическую таблицу и вьюху")
    void createTableGeneratesDdl() throws Exception {
        String json = """
            {"systemName": "test_orders", "displayName": "Тестовые заказы"}
            """;

        mockMvc.perform(post("/api/tables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemName").value("test_orders"));

        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'test_orders'",
                Integer.class);
        assertThat(tableCount).isEqualTo(1);

        Integer viewCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.views WHERE table_name = 'v_test_orders'",
                Integer.class);
        assertThat(viewCount).isEqualTo(1);

        Integer columnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'test_orders' " +
                        "AND column_name IN ('id', 'version', 'created_at', 'updated_at')",
                Integer.class);
        assertThat(columnCount).isEqualTo(4);
    }

    @Test
    @DisplayName("Создание таблицы с невалидным именем отклоняется с кодом 400")
    void createTableInvalidName() throws Exception {
        String json = """
            {"systemName": "123-invalid!", "displayName": "Плохое имя"}
            """;

        mockMvc.perform(post("/api/tables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    @SuppressWarnings("SqlResolve")
    @DisplayName("Создание поля добавляет колонку в БД и обновляет вьюху")
    void createFieldUpdatesSchema() throws Exception {
        mockMvc.perform(post("/api/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"systemName": "test_products", "displayName": "Товары"}
                    """));

        Long tableId = jdbcTemplate.queryForObject(
                "SELECT id FROM table_def WHERE system_name = 'test_products'", Long.class);

        mockMvc.perform(post("/api/tables/" + tableId + "/fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                        "systemName": "price",
                        "displayName": "Цена",
                        "type": "DECIMAL",
                        "visibleOnDetails": true,
                        "visibleOnList": true
                    }
                    """))
                .andExpect(status().isOk());

        Integer columnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_name = 'test_products' AND column_name = 'price'",
                Integer.class);
        assertThat(columnCount).isEqualTo(1);

        assertDoesNotThrow(() ->
                jdbcTemplate.queryForList("SELECT price FROM v_test_products LIMIT 1"));
    }
}