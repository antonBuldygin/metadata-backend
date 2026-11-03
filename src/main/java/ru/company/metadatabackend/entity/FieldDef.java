package ru.company.metadatabackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "field_def")
public class FieldDef {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "table_id")
    @JsonIgnore
    private TableDef table;

    @Column(nullable = false)
    private String systemName;

    @Column(nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldType type;

    private boolean visibleOnDetails;
    private boolean visibleOnList;

    // НОВЫЕ ПОЛЯ ДЛЯ СВЯЗЕЙ:
    private String targetTableName;  // Системное имя таблицы, с которой связываемся (например, "users")
    private String targetDisplayField; // Имя поля в той таблице, которое видит пользователь (например, "name")
}