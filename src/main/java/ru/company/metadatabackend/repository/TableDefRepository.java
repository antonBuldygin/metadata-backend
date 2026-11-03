package ru.company.metadatabackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.company.metadatabackend.entity.TableDef;


@Repository
public interface TableDefRepository extends JpaRepository<TableDef, Long> {
}