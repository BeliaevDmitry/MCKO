package org.school.MckoReport.MckoCompleks.service.report;

import lombok.RequiredArgsConstructor;
import org.school.MckoReport.MckoCompleks.dto.CombinedResultData;
import org.school.MckoReport.MckoCompleks.model.MckoCombinedReportData;
import org.school.MckoReport.MckoCompleks.repository.MckoCombinedReportDataRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CombinedReportPersistenceService {

    private final MckoCombinedReportDataRepository mckoCombinedReportDataRepository;

    public void upsertCombinedResults(List<CombinedResultData> combinedResults) {
        if (combinedResults == null || combinedResults.isEmpty()) {
            return;
        }

        List<String> rowKeys = combinedResults.stream()
                .map(this::buildRowKey)
                .collect(Collectors.toList());

        Map<String, MckoCombinedReportData> existingByRowKey = mckoCombinedReportDataRepository.findByRowKeyIn(rowKeys)
                .stream()
                .collect(Collectors.toMap(MckoCombinedReportData::getRowKey, entity -> entity));

        List<MckoCombinedReportData> toSave = new ArrayList<>();
        for (CombinedResultData combined : combinedResults) {
            String rowKey = buildRowKey(combined);
            MckoCombinedReportData entity = existingByRowKey.getOrDefault(rowKey, new MckoCombinedReportData());
            entity.setRowKey(rowKey);
            fillEntity(entity, combined);
            toSave.add(entity);
        }

        mckoCombinedReportDataRepository.saveAll(toSave);
    }

    private void fillEntity(MckoCombinedReportData entity, CombinedResultData combined) {
        entity.setSourceType(resolveSourceType(combined));
        entity.setNameFIO(combined.getNameFIO());
        entity.setCode(combined.getCode());
        entity.setClassName(combined.getClassName());
        entity.setSubject(combined.getSubject());
        entity.setDate(combined.getDate());
        entity.setSchool(combined.getSchool());
        entity.setSchoolYear(combined.getSchoolYear());
        entity.setClassLevel(combined.getClassLevel());
        entity.setCityLevel(combined.getCityLevel());

        entity.setParallel(combined.getParallel());
        entity.setLetter(combined.getLetter());
        entity.setVariant(combined.getVariant());
        entity.setBall(combined.getBall());
        entity.setPercentCompleted(resolvePercent(combined));
        entity.setGradeValue(resolveGrade(combined));
        entity.setStudentNumber(combined.getStudentNumber());
        entity.setTaskScores(combined.getTaskScores());
    }

    private Integer resolvePercent(CombinedResultData combined) {
        if (combined.getPercentCompleted() != null) {
            return combined.getPercentCompleted();
        }
        return parseInteger(combined.getOverallPercent());
    }

    private String resolveGrade(CombinedResultData combined) {
        if (combined.getMark() != null) {
            return String.valueOf(combined.getMark());
        }
        return combined.getMasteryLevel();
    }

    private String resolveSourceType(CombinedResultData combined) {
        if (combined.isHasResultData() && combined.isHasFGData()) {
            return "RESULT+FG";
        }
        if (combined.isHasResultData()) {
            return "RESULT";
        }
        if (combined.isHasFGData()) {
            return "FG";
        }
        return "NONE";
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String digitsOnly = value.replaceAll("[^0-9-]", "");
        if (digitsOnly.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(digitsOnly);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String buildRowKey(CombinedResultData combined) {
        return String.join("|",
                safe(combined.getSchool()),
                safe(combined.getSubject()),
                safe(combined.getDate()),
                safe(combined.getClassName()),
                safe(combined.getCode()),
                safe(combined.getStudentNumber() != null ? combined.getStudentNumber().toString() : null)
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
