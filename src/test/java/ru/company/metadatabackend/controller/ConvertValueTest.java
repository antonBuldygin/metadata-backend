package ru.company.metadatabackend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import ru.company.metadatabackend.entity.FieldType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ConvertValueTest {

    // Создаем объект контроллера "вручную" для тестирования чистых методов
    // Если convertValue private - используем рефлексию или делаем его package-private
    private final DataController controller = new DataController(null, null, null, null);

    @Test
    @DisplayName("Конвертация STRING")
    void convertString() {
        Object result = controller.convertValue("Привет", FieldType.STRING);
        assertEquals("Привет", result);
    }

    @Test
    @DisplayName("Конвертация INTEGER")
    void convertInteger() {
        assertEquals(123, controller.convertValue("123", FieldType.INTEGER));
    }

    @Test
    @DisplayName("Конвертация LONG")
    void convertLong() {
        assertEquals(9999999999L, controller.convertValue("9999999999", FieldType.LONG));
    }

    @Test
    @DisplayName("Конвертация DECIMAL")
    void convertDecimal() {
        BigDecimal result = (BigDecimal) controller.convertValue("123.45", FieldType.DECIMAL);
        assertEquals(new BigDecimal("123.45"), result);
    }

    @Test
    @DisplayName("Конвертация BOOLEAN")
    void convertBoolean() {
        assertEquals(true, controller.convertValue("true", FieldType.BOOLEAN));
        assertEquals(false, controller.convertValue("false", FieldType.BOOLEAN));
    }

    @Test
    @DisplayName("Конвертация DATE")
    void convertDate() {
        LocalDate result = (LocalDate) controller.convertValue("2024-01-15", FieldType.DATE);
        assertEquals(LocalDate.of(2024, 1, 15), result);
    }

    @Test
    @DisplayName("Конвертация DATETIME")
    void convertDateTime() {
        LocalDateTime result = (LocalDateTime) controller.convertValue("2024-01-15T10:30:00", FieldType.DATETIME);
        assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30), result);
    }

    @Test
    @DisplayName("Пустое значение возвращает null")
    void convertEmptyReturnsNull() {
        assertNull(controller.convertValue(null, FieldType.STRING));
        assertNull(controller.convertValue("", FieldType.STRING));
        assertNull(controller.convertValue("   ", FieldType.STRING));
    }

    @Test
    @DisplayName("Неверный формат бросает исключение")
    void convertInvalidThrows() {
        assertThrows(ResponseStatusException.class,
                () -> controller.convertValue("не_число", FieldType.INTEGER));
    }
}