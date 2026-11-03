package ru.company.metadatabackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import ru.company.metadatabackend.AbstractIntegrationTest;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class DataControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setupTable() throws Exception {
        // Полная очистка перед каждым тестом
        jdbcTemplate.execute("DROP TABLE IF EXISTS test_clients CASCADE");
        jdbcTemplate.execute("DROP VIEW IF EXISTS v_test_clients CASCADE");
        jdbcTemplate.update("DELETE FROM field_def WHERE table_id IN (SELECT id FROM table_def WHERE system_name = 'test_clients')");
        jdbcTemplate.update("DELETE FROM table_def WHERE system_name = 'test_clients'");

        // Создаем таблицу заново
        mockMvc.perform(post("/api/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"systemName": "test_clients", "displayName": "Клиенты"}
                    """));

        Long tableId = jdbcTemplate.queryForObject(
                "SELECT id FROM table_def WHERE system_name = 'test_clients'", Long.class);

        mockMvc.perform(post("/api/tables/" + tableId + "/fields")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"systemName": "full_name", "displayName": "ФИО", "type": "STRING",
                     "visibleOnDetails": true, "visibleOnList": true}
                    """));
    }

    @Test
    @DisplayName("Создание записи и её чтение")
    void createAndReadRecord() throws Exception {
        mockMvc.perform(post("/api/data/test_clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"full_name": "Иван Иванов"}
                    """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/data/test_clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].full_name").value("Иван Иванов"))
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].version").value(1));
    }

    @Test
    @DisplayName("Обновление записи увеличивает версию")
    void updateIncrementsVersion() throws Exception {
        mockMvc.perform(post("/api/data/test_clients")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"full_name": "Петр Петров"}
                    """));

        String response = mockMvc.perform(get("/api/data/test_clients"))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> records = objectMapper.readValue(response, List.class);
        Number id = (Number) records.get(0).get("id");

        mockMvc.perform(put("/api/data/test_clients/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"full_name": "Сидор Сидоров", "version": 1}
                    """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/data/test_clients"))
                .andExpect(jsonPath("$[0].full_name").value("Сидор Сидоров"))
                .andExpect(jsonPath("$[0].version").value(2));
    }

    @Test
    @DisplayName("Конфликт версий блокирует обновление")
    void optimisticLockingBlocksStaleUpdate() throws Exception {
        mockMvc.perform(post("/api/data/test_clients")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"full_name": "Оригинал"}
                    """));

        String response = mockMvc.perform(get("/api/data/test_clients"))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> records = objectMapper.readValue(response, List.class);
        Number id = (Number) records.get(0).get("id");

        mockMvc.perform(put("/api/data/test_clients/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"full_name": "Версия 2", "version": 1}
                    """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/data/test_clients/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"full_name": "Попытка перезаписи", "version": 1}
                    """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Удаление записи")
    void deleteRecord() throws Exception {
        mockMvc.perform(post("/api/data/test_clients")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"full_name": "На удаление"}
                    """));

        String response = mockMvc.perform(get("/api/data/test_clients"))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> records = objectMapper.readValue(response, List.class);
        Number id = (Number) records.get(0).get("id");

        mockMvc.perform(delete("/api/data/test_clients/" + id))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/data/test_clients"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Обращение к несуществующей таблице возвращает 404")
    void unknownTableReturns404() throws Exception {
        mockMvc.perform(get("/api/data/unknown_table"))
                .andExpect(status().isNotFound());
    }
}