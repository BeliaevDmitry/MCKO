package org.school.MckoReport.MckoCompleks.repository;

import org.school.MckoReport.MckoCompleks.model.MckoCombinedReportData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MckoCombinedReportDataRepository extends JpaRepository<MckoCombinedReportData, Long> {
    List<MckoCombinedReportData> findByRowKeyIn(List<String> rowKeys);
}
