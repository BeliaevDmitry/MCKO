package org.school.MckoReport.MckoCompleks.repository;

import org.school.MckoReport.MckoCompleks.model.StudentResultData;
import org.school.MckoReport.MckoCompleks.model.StudentResultFGData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentResultFGDataRepository extends
        JpaRepository<StudentResultFGData, Long>,
        JpaSpecificationExecutor<StudentResultFGData> {
    List<StudentResultFGData> findBySchool(String school);
}
