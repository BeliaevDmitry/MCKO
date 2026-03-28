package org.school.MckoReport.MckoCompleks.repository;

import org.school.MckoReport.MckoCompleks.model.OtherDiagnosticData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OtherDiagnosticDataRepository extends JpaRepository<OtherDiagnosticData, Long> {
    List<OtherDiagnosticData> findBySchool(String school);
    // можно добавить другие методы по необходимости
}