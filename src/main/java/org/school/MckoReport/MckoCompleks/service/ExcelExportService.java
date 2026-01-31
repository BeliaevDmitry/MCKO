package org.school.MckoReport.MckoCompleks.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.MckoReport.MckoCompleks.dto.CombinedResultData;
import org.school.MckoReport.MckoCompleks.util.TaskScoresConverter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelExportService {

    private final TaskScoresConverter taskScoresConverter;

    /**
     * Создать Excel файл с двумя вкладками
     */
    public byte[] exportToExcel(List<CombinedResultData> data) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            log.debug("длина List<CombinedResultData> data в exportToExcel перед передачей в генератор эксель {}", data.size());
            // Стили для заголовков
            CellStyle headerStyle = createHeaderStyle(workbook);

            // Вкладка 1: Основные результаты
            createResultsSheet(workbook, data, headerStyle, "Результаты");

            // Вкладка 2: Функциональная грамотность
            createFGSheet(workbook, data, headerStyle, "Функциональная грамотность");

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
}