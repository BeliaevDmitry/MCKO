package org.school.MckoReport.MckoCompleks.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.MckoReport.MckoCompleks.dto.CombinedResultData;
import org.school.MckoReport.MckoCompleks.model.ListStudentData;
import org.school.MckoReport.MckoCompleks.model.StudentResultData;
import org.school.MckoReport.MckoCompleks.model.StudentResultFGData;
import org.school.MckoReport.MckoCompleks.util.TaskScoresConverter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelExportService {

    private final TaskScoresConverter taskScoresConverter;

    /**
     * Создать Excel файл с двумя вкладками
     */
    public byte[] exportToExcel(List<CombinedResultData> data) throws IOException {
        return exportToExcel(data, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    /**
     * Создать Excel файл с листами результатов и сводкой по работам.
     */
    public byte[] exportToExcel(List<CombinedResultData> data,
                                List<ListStudentData> allStudents,
                                List<StudentResultData> allStudentResults,
                                List<StudentResultFGData> allStudentFGResults) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            log.debug("длина List<CombinedResultData> data в exportToExcel перед передачей в генератор эксель {}", data.size());
            // Стили для заголовков
            CellStyle headerStyle = createHeaderStyle(workbook);

            // Вкладка 1: Основные результаты
            createResultsSheet(workbook, data, headerStyle, "Результаты");

            // Вкладка 2: Функциональная грамотность
            createFGSheet(workbook, data, headerStyle, "Функциональная грамотность");

            Map<String, WorkSummary> workSummaryMap = buildWorkSummaryMap(allStudents, allStudentResults, allStudentFGResults);
            createAllWorksSheet(workbook, workSummaryMap, headerStyle, "Все работы");
            createMissingWorksSheet(workbook, workSummaryMap, headerStyle, "Незагруженные работы");

            // Записываем в массив байтов
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void createResultsSheet(Workbook workbook, List<CombinedResultData> data,
                                    CellStyle headerStyle, String sheetName) {

        Sheet sheet = workbook.createSheet(sheetName);

        // Создаем заголовки
        Row headerRow = sheet.createRow(0);
        String[] headers = {
                "ФИО", "Код ученика", "Класс", "Предмет", "Дата", "Школа",
                "Параллель", "Литера", "Вариант", "Балл", "Процент", "Оценка",
                "Номер ученика", "JSON баллы"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.autoSizeColumn(i);
        }

        // Заполняем данными
        int rowNum = 1;
        for (CombinedResultData record : data) {
            if (record.isHasResultData()) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(record.getNameFIO() != null ? record.getNameFIO() : "");
                row.createCell(1).setCellValue(record.getCode() != null ? record.getCode() : "");
                row.createCell(2).setCellValue(record.getClassName() != null ? record.getClassName() : "");
                row.createCell(3).setCellValue(record.getSubject() != null ? record.getSubject() : "");
                row.createCell(4).setCellValue(record.getDate() != null ? record.getDate() : "");
                row.createCell(5).setCellValue(record.getSchool() != null ? record.getSchool() : "");
                row.createCell(6).setCellValue(record.getParallel() != null ? record.getParallel().toString() : "");
                row.createCell(7).setCellValue(record.getLetter() != null ? record.getLetter() : "");
                row.createCell(8).setCellValue(record.getVariant() != null ? record.getVariant().toString() : "");
                row.createCell(9).setCellValue(record.getBall() != null ? record.getBall().toString() : "");
                row.createCell(10).setCellValue(record.getPercentCompleted() != null ? record.getPercentCompleted().toString() : "");
                row.createCell(11).setCellValue(record.getMark() != null ? record.getMark().toString() : "");
                row.createCell(12).setCellValue(record.getStudentNumber() != null ? record.getStudentNumber().toString() : "");
                row.createCell(13).setCellValue(record.getTaskScores() != null ? record.getTaskScores() : "");
            }
        }

        // Авторазмер колонок
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createFGSheet(Workbook workbook, List<CombinedResultData> data,
                               CellStyle headerStyle, String sheetName) {
        log.debug("длина List<CombinedResultData> data в createFGSheet перед передачей в генератор эксель {}", data.size());
        Sheet sheet = workbook.createSheet(sheetName);

        // Создаем заголовки
        Row headerRow = sheet.createRow(0);
        String[] headers = {
                "ФИО", "Код ученика", "Класс", "Предмет", "Дата", "Школа",
                "Общий процент", "Уровень освоения",
                "Раздел 1 %", "Раздел 2 %", "Раздел 3 %"
        };
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        log.debug("заголовки созданы");
        // Заполняем данными
        int rowNum = 1;
        for (CombinedResultData record : data) {
            if (record.isHasFGData()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(record.getNameFIO() != null ? record.getNameFIO() : "");
                row.createCell(1).setCellValue(record.getCode() != null ? record.getCode() : "");
                row.createCell(2).setCellValue(record.getClassName() != null ? record.getClassName() : "");
                row.createCell(3).setCellValue(record.getSubject() != null ? record.getSubject() : "");
                row.createCell(4).setCellValue(record.getDate() != null ? record.getDate() : "");
                row.createCell(5).setCellValue(record.getSchool() != null ? record.getSchool() : "");
                row.createCell(6).setCellValue(record.getOverallPercent() != null ? record.getOverallPercent() : "");
                row.createCell(7).setCellValue(record.getMasteryLevel() != null ? record.getMasteryLevel() : "");
                row.createCell(8).setCellValue(record.getSection1Percent() != null ? record.getSection1Percent() : "");
                row.createCell(9).setCellValue(record.getSection2Percent() != null ? record.getSection2Percent() : "");
                row.createCell(10).setCellValue(record.getSection3Percent() != null ? record.getSection3Percent() : "");
            }
        }
        log.debug("вышел из цикла");
        // Авторазмер колонок
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private Map<String, WorkSummary> buildWorkSummaryMap(List<ListStudentData> allStudents,
                                                         List<StudentResultData> allStudentResults,
                                                         List<StudentResultFGData> allStudentFGResults) {
        Map<String, WorkSummary> workSummaryMap = new LinkedHashMap<>();

        for (ListStudentData student : allStudents) {
            String key = buildWorkKey(student.getSchool(), student.getSubject(), student.getDate(), student.getClassName());
            WorkSummary summary = workSummaryMap.computeIfAbsent(key,
                    k -> new WorkSummary(student.getSchool(), student.getSubject(), student.getDate(), student.getClassName()));
            summary.childSheetRows++;
        }

        for (StudentResultData result : allStudentResults) {
            String key = buildWorkKey(result.getSchool(), result.getSubject(), result.getDate(), result.getClassName());
            WorkSummary summary = workSummaryMap.computeIfAbsent(key,
                    k -> new WorkSummary(result.getSchool(), result.getSubject(), result.getDate(), result.getClassName()));
            summary.resultRows++;
        }

        for (StudentResultFGData fgResult : allStudentFGResults) {
            String key = buildWorkKey(fgResult.getSchool(), fgResult.getSubject(), fgResult.getDate(), fgResult.getClassName());
            WorkSummary summary = workSummaryMap.computeIfAbsent(key,
                    k -> new WorkSummary(fgResult.getSchool(), fgResult.getSubject(), fgResult.getDate(), fgResult.getClassName()));
            summary.fgRows++;
        }

        return workSummaryMap;
    }

    private void createAllWorksSheet(Workbook workbook,
                                     Map<String, WorkSummary> workSummaryMap,
                                     CellStyle headerStyle,
                                     String sheetName) {
        Sheet sheet = workbook.createSheet(sheetName);
        Row headerRow = sheet.createRow(0);
        String[] headers = {
                "Школа", "Предмет", "Дата", "Класс",
                "Строк в листе детей", "Строк в результатах", "Строк в ФГ"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (WorkSummary summary : workSummaryMap.values()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(valueOrEmpty(summary.school));
            row.createCell(1).setCellValue(valueOrEmpty(summary.subject));
            row.createCell(2).setCellValue(valueOrEmpty(summary.date));
            row.createCell(3).setCellValue(valueOrEmpty(summary.className));
            row.createCell(4).setCellValue(summary.childSheetRows);
            row.createCell(5).setCellValue(summary.resultRows);
            row.createCell(6).setCellValue(summary.fgRows);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void createMissingWorksSheet(Workbook workbook,
                                         Map<String, WorkSummary> workSummaryMap,
                                         CellStyle headerStyle,
                                         String sheetName) {
        Sheet sheet = workbook.createSheet(sheetName);
        Row headerRow = sheet.createRow(0);
        String[] headers = {
                "Школа", "Предмет", "Дата", "Класс", "Проблема",
                "Строк в листе детей", "Строк в результатах", "Строк в ФГ"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (WorkSummary summary : workSummaryMap.values()) {
            List<String> problems = detectProblems(summary);
            if (problems.isEmpty()) {
                continue;
            }

            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(valueOrEmpty(summary.school));
            row.createCell(1).setCellValue(valueOrEmpty(summary.subject));
            row.createCell(2).setCellValue(valueOrEmpty(summary.date));
            row.createCell(3).setCellValue(valueOrEmpty(summary.className));
            row.createCell(4).setCellValue(String.join("; ", problems));
            row.createCell(5).setCellValue(summary.childSheetRows);
            row.createCell(6).setCellValue(summary.resultRows);
            row.createCell(7).setCellValue(summary.fgRows);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private List<String> detectProblems(WorkSummary summary) {
        List<String> problems = new ArrayList<>();

        if (summary.resultRows == 0) {
            problems.add("Не загружены результаты работы");
        }
        if (summary.childSheetRows == 0 && (summary.resultRows > 0 || summary.fgRows > 0)) {
            problems.add("Не загружен лист с данными детей");
        }

        return problems;
    }

    private String buildWorkKey(String school, String subject, String date, String className) {
        return String.format("%s|%s|%s|%s",
                valueOrEmpty(school),
                valueOrEmpty(subject),
                valueOrEmpty(date),
                valueOrEmpty(className));
    }

    private String valueOrEmpty(String value) {
        return value != null ? value : "";
    }

    private static class WorkSummary {
        private final String school;
        private final String subject;
        private final String date;
        private final String className;
        private int childSheetRows;
        private int resultRows;
        private int fgRows;

        private WorkSummary(String school, String subject, String date, String className) {
            this.school = school;
            this.subject = subject;
            this.date = date;
            this.className = className;
        }
    }
}
