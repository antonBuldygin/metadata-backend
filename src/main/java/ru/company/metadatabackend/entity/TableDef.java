package ru.company.metadatabackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "table_def")
public class TableDef {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String systemName;   // например "custom_orders"

    @Column(nullable = false)
    private String displayName;  // например "Заказы"

    @OneToMany(mappedBy = "table", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FieldDef> fields = new ArrayList<>();
}