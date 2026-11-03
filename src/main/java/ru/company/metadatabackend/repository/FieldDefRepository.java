package ru.company.metadatabackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.company.metadatabackend.entity.FieldDef;

@Repository
public interface FieldDefRepository extends JpaRepository<FieldDef, Long> {
}