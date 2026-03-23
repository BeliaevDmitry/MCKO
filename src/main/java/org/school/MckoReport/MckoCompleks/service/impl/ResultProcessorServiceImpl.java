package org.school.MckoReport.MckoCompleks.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.school.MckoReport.MckoCompleks.model.StudentResultData;
import org.school.MckoReport.MckoCompleks.service.ResultProcessorService;
import org.school.MckoReport.MckoCompleks.util.DateNormalizerUtil;
import org.school.MckoReport.MckoCompleks.util.TaskScoresConverter;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ResultProcessorServiceImpl implements ResultProcessorService {

    private final TaskScoresConverter taskScoresConverter;

    @Override
    public List<StudentResultData> extractStudentsResult(Path patch) {
        File fileResult = new File(String.valueOf(patch));
        List<StudentResultData> allResults = new ArrayList<>();

        try (FileInputStream file = new FileInputStream(fileResult);
             Workbook workbook = new XSSFWorkbook(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            ColumnIndexes indexes = null;

            for (Row row : sheet) {
                if (row.getRowNum() < 2) {
                    // Пропускаем первые 2 строки (метаданные и заголовки)
                    if (row.getRowNum() == 1) {
                        // Вторая строка (индекс 1) содержит заголовки
                        indexes = validateAndGetIndexes(row);
                    }
                    continue;
                }

                // Проверяем, что строка не пустая
                if (row.getLastCellNum() <= 0) {
                    continue;
                }

                try {
                    // 2-3) Парсим результаты заданий в Map
                    Map<String, Integer> taskScores = parseTaskResults(row, indexes);

                    // 4) Собираем остальные данные
                    StudentResultData studentData = collectOtherData(row, indexes, taskScores);

                    // Проверяем, что есть минимальные данные для сохранения
                    if (studentData.getSchool() != null &&
                            studentData.getCode() != null &&
                            studentData.getBall() != null) {
                        allResults.add(studentData);
                    }

                } catch (Exception e) {
                    System.err.println("Ошибка обработки строки " + row.getRowNum() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }

            return allResults;

        } catch (Exception e) {
            throw new RuntimeException("Ошибка при обработке файла: " + patch, e);
        }
    }

    // Индексы для фиксированных колонок (будут определены при анализе заголовка)
    private static class ColumnIndexes {
        int schoolIdx = -1;
        int parallelIdx = -1;
        int letterIdx = -1;
        int subjectIdx = -1;
        int dateIdx = -1;
        int nameIdx = -1;
        int studentNumberIdx = -1;
        int variantIdx = -1;
        int codeIdx = -1;
        int ballIdx = -1;
        int percentIdx = -1;
        int markIdx = -1;
        List<Integer> taskColumnsStartIdx = new ArrayList<>();

        // Обязательные колонки (без codeIdx и markIdx)
        boolean hasRequiredColumns() {
            return schoolIdx >= 0 && parallelIdx >= 0 && letterIdx >= 0
                    && subjectIdx >= 0 && dateIdx >= 0 && nameIdx >= 0
                    && variantIdx >= 0 && ballIdx >= 0 && percentIdx >= 0;
        }
    }

    /**
     * 1) Проверяет структуру файла и возвращает индексы колонок
     * ФИКС: правильно обрабатывает типы ячеек
     */
    private ColumnIndexes validateAndGetIndexes(Row headerRow) {
        ColumnIndexes indexes = new ColumnIndexes();

        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null) continue;
            String cellValue = getCellValueAsString(cell);
            if (cellValue == null) continue;
            cellValue = cellValue.trim();

            switch (cellValue) {
                case "Школа": indexes.schoolIdx = i; break;
                case "Параллель": indexes.parallelIdx = i; break;
                case "Буква": indexes.letterIdx = i; break;
                case "Предмет": indexes.subjectIdx = i; break;
                case "Дата": indexes.dateIdx = i; break;
                case "Фамилия, имя": indexes.nameIdx = i; break;
                case "№ уч.": indexes.studentNumberIdx = i; break;
                case "Вариант": indexes.variantIdx = i; break;
                case "Код диагн.": indexes.codeIdx = i; break;
                case "Балл": indexes.ballIdx = i; break;
                case "% вып.": indexes.percentIdx = i; break;
                case "Отметка": indexes.markIdx = i; break;
            }
        }

        // Определяем индексы заданий (между Код диагн. и Балл)
        if (indexes.codeIdx >= 0 && indexes.ballIdx >= 0) {
            for (int i = indexes.codeIdx + 1; i < indexes.ballIdx; i++) {
                Cell cell = headerRow.getCell(i);
                if (cell != null) {
                    String cellValue = getCellValueAsString(cell);
                    if (cellValue != null && !cellValue.trim().isEmpty()) {
                        if (!cellValue.equals("Балл") && !cellValue.equals("% вып.") && !cellValue.equals("Отметка")) {
                            indexes.taskColumnsStartIdx.add(i);
                        }
                    }
                }
            }
        }

        // Проверка обязательных колонок
        if (!indexes.hasRequiredColumns()) {
            StringBuilder missing = new StringBuilder();
            if (indexes.schoolIdx == -1) missing.append("Школа, ");
            if (indexes.parallelIdx == -1) missing.append("Параллель, ");
            if (indexes.letterIdx == -1) missing.append("Буква, ");
            if (indexes.subjectIdx == -1) missing.append("Предмет, ");
            if (indexes.dateIdx == -1) missing.append("Дата, ");
            if (indexes.nameIdx == -1) missing.append("Фамилия, имя, ");
            if (indexes.variantIdx == -1) missing.append("Вариант, ");
            if (indexes.ballIdx == -1) missing.append("Балл, ");
            if (indexes.percentIdx == -1) missing.append("% вып., ");
            if (missing.length() > 0) {
                missing.setLength(missing.length() - 2);
                throw new IllegalArgumentException("Файл имеет неверную структуру. Отсутствуют обязательные колонки: " + missing);
            }
        }

        return indexes;
    }

    /**
     * 3) Парсит результаты заданий в Map<String, Integer>
     * Где ключ - номер задания (например, "1.1.К1"), значение - балл
     */
    private Map<String, Integer> parseTaskResults(Row row, ColumnIndexes indexes) {
        Map<String, Integer> taskScores = new LinkedHashMap<>(); // сохраняем порядок
        Row headerRow = row.getSheet().getRow(1); // заголовки во второй строке

        for (int colIdx : indexes.taskColumnsStartIdx) {
            Cell cell = row.getCell(colIdx);
            if (cell == null) continue;

            // Получаем значение балла
            Integer score = parseTaskScore(cell);
            if (score == null) {
                continue; // пропускаем пустые
            }

            // Получаем название задания из заголовка
            Cell headerCell = headerRow.getCell(colIdx);
            String taskName = getCellValueAsString(headerCell);
            if (taskName == null || taskName.trim().isEmpty()) {
                continue;
            }

            // Форматируем название задания
            taskName = formatTaskName(taskName.trim());

            // Сохраняем в Map
            taskScores.put(taskName, score);
        }

        return taskScores;
    }

    /**
     * Парсит балл за задание из ячейки
     * Поддерживает форматы: "2", "3+", "1-", "N" и т.д.
     */
    private Integer parseTaskScore(Cell cell) {
        if (cell == null) return null;

        try {
            // В зависимости от типа ячейки
            switch (cell.getCellType()) {
                case NUMERIC:
                    double value = cell.getNumericCellValue();
                    return (int) Math.round(value);

                case STRING:
                    String strValue = cell.getStringCellValue().trim();
                    return parseScoreFromString(strValue);

                case FORMULA:
                    try {
                        double numValue = cell.getNumericCellValue();
                        return (int) Math.round(numValue);
                    } catch (Exception e) {
                        String strFormulaValue = cell.getStringCellValue();
                        return parseScoreFromString(strFormulaValue);
                    }

                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Парсит балл из строки с учетом особых символов
     */
    private Integer parseScoreFromString(String strValue) {
        if (strValue == null || strValue.isEmpty()) {
            return null;
        }

        strValue = strValue.trim();

        // Обработка специальных значений
        if (strValue.equalsIgnoreCase("N")) {
            return 0; // или null, в зависимости от требований
        }

        // Обработка значений с плюсами/минусами: "3+", "2-"
        if (strValue.matches("\\d+[+-]?")) {
            // Извлекаем числовую часть
            String numericPart = strValue.replaceAll("[+-]", "");
            try {
                return Integer.parseInt(numericPart);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        // Пробуем распарсить как число
        try {
            return Integer.parseInt(strValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Форматирует название задания
     */
    private String formatTaskName(String taskName) {
        if (taskName == null) return "";

        // Убираем лишние пробелы
        taskName = taskName.trim();

        // Заменяем запятые на точки (если в заголовке "1,2" вместо "1.2")
        taskName = taskName.replace(',', '.');

        // Убираем лишние пробелы вокруг точек
        taskName = taskName.replaceAll("\\s*\\.\\s*", ".");

        // Если начинается с цифры и точки, можно добавить "к" для единообразия
        if (taskName.matches("^\\d+\\.\\d+.*")) {
            // Можно раскомментировать, если нужно добавить "к"
            // taskName = "к" + taskName;
        }

        return taskName;
    }

    /**
     * 4) Собираем остальные данные
     */
    private StudentResultData collectOtherData(Row row, ColumnIndexes indexes, Map<String, Integer> taskScores) {
        StudentResultData data = new StudentResultData();

        // Парсим фиксированные поля
        data.setSchool(getCellStringValue(row.getCell(indexes.schoolIdx)));
        data.setParallel(getCellIntValue(row.getCell(indexes.parallelIdx)));
        data.setLetter(getCellStringValue(row.getCell(indexes.letterIdx)));
        data.setSubject(getCellStringValue(row.getCell(indexes.subjectIdx)));
        String  date = getCellStringValue(row.getCell(indexes.dateIdx));
        String dateNormal = DateNormalizerUtil.normalizeDate(date);
        data.setDate(dateNormal);

        // Номер ученика
        String studentNum = getCellValueAsString(row.getCell(indexes.studentNumberIdx));
        if (studentNum != null && !studentNum.isEmpty()) {
            try {
                data.setStudentNumber(Integer.parseInt(studentNum.trim()));
            } catch (NumberFormatException e) {
                // Если не число, оставляем null
            }
        }

        data.setVariant(getCellIntValue(row.getCell(indexes.variantIdx)));
        data.setCode(getCellStringValue(row.getCell(indexes.codeIdx)));
        data.setBall(getCellIntValue(row.getCell(indexes.ballIdx)));
        data.setPercentCompleted(getCellIntValue(row.getCell(indexes.percentIdx)));
        data.setMark(getCellIntValue(row.getCell(indexes.markIdx)));

        // Сохраняем результаты заданий как JSON через конвертер
        data.setTaskScores(taskScoresConverter.mapToJson(taskScores));

        return data;
    }



    /**
     * Получает значение ячейки как строку (универсальный метод)
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;

        try {
            CellType cellType = cell.getCellType();

            // Для формул сначала проверяем тип результата
            if (cellType == CellType.FORMULA) {
                try {
                    // Пытаемся получить числовое значение
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e1) {
                    try {
                        // Пытаемся получить строковое значение
                        return cell.getStringCellValue();
                    } catch (Exception e2) {
                        return "";
                    }
                }
            }

            // Для остальных типов
            switch (cellType) {
                case STRING:
                    return cell.getStringCellValue();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue().toString();
                    } else {
                        double value = cell.getNumericCellValue();
                        // Проверяем, целое ли число
                        if (value == Math.floor(value)) {
                            return String.valueOf((int) value);
                        } else {
                            return String.valueOf(value);
                        }
                    }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case BLANK:
                    return "";
                default:
                    return "";
            }
        } catch (Exception e) {
            // В случае ошибки возвращаем пустую строку
            System.err.println("Ошибка чтения ячейки: " + e.getMessage());
            return "";
        }
    }



    /**
     * Вспомогательные методы для чтения значений ячеек
     */
    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;

        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return String.valueOf((int) cell.getNumericCellValue());
            } else if (cell.getCellType() == CellType.STRING) {
                return cell.getStringCellValue().trim();
            } else if (cell.getCellType() == CellType.BOOLEAN) {
                return String.valueOf(cell.getBooleanCellValue());
            }
        } catch (Exception e) {
            // В случае ошибки возвращаем пустую строку
        }
        return "";
    }

    private Integer getCellIntValue(Cell cell) {
        if (cell == null) return null;

        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (int) cell.getNumericCellValue();
            } else if (cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue().trim();
                if (!value.isEmpty()) {
                    return Integer.parseInt(value);
                }
            }
        } catch (Exception e) {
            // В случае ошибки возвращаем null
        }
        return null;
    }
}