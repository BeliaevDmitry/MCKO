package org.school.MckoReport;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class ResultsPdfProcessor {

    private static boolean DEBUG = true; // Установите true для отладки

    public static void main(String[] args) {
        // Укажите путь к папке с PDF файлами результатов
        String folderPath = "C:\\Users\\dimah\\Yandex.Disk\\ГБОУ №7\\МЦКО\\на обработку\\результаты";
        // Укажите путь для сохранения Excel файла
        String outputExcelPath = "C:\\Users\\dimah\\Yandex.Disk\\ГБОУ №7\\МЦКО\\КОД_результат.xlsx";

        try {
            List<StudentResultData> allResults = new ArrayList<>();

            System.out.println("Начало обработки PDF файлов из папки: " + folderPath);
            System.out.println("==========================================");

            // Получаем список всех PDF файлов в папке
            List<Path> pdfFiles = new ArrayList<>();
            try (var paths = Files.walk(Paths.get(folderPath))) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().toLowerCase().endsWith(".pdf"))
                        .forEach(pdfFiles::add);
            }

            System.out.println("Найдено PDF файлов: " + pdfFiles.size());

            // Обрабатываем каждый PDF файл
            for (Path pdfPath : pdfFiles) {
                try {
                    System.out.println("\nОбработка файла: " + pdfPath.getFileName());
                    processResultPdfFile(pdfPath.toFile(), allResults);
                } catch (Exception e) {
                    System.err.println("Ошибка при обработке файла: " + pdfPath);
                    e.printStackTrace();
                }
            }

            System.out.println("\n==========================================");
            System.out.println("Всего извлечено записей: " + allResults.size());

            // Создаем Excel файла
            createResultsExcelFile(allResults, outputExcelPath);
            System.out.println("Обработка завершена. Результат сохранен в: " + outputExcelPath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processResultPdfFile(File pdfFile, List<StudentResultData> allResults) throws IOException {
        String fileName = pdfFile.getName();

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            if (DEBUG) {
                System.out.println("  Первые 500 символов текста PDF:");
                System.out.println(text.substring(0, Math.min(500, text.length())));
                System.out.println("  ---");
            }

            // Извлекаем общую информацию из заголовка
            Map<String, String> headerInfo = extractHeaderInfo(text);
            String date = headerInfo.get("date");
            String subject = headerInfo.get("subject");
            String className = headerInfo.get("className");

            if (DEBUG) {
                System.out.println("  Дата: " + date);
                System.out.println("  Предмет: " + subject);
                System.out.println("  Класс: " + className);
            }

            // Извлекаем данные студентов
            List<StudentResultData> studentResults = extractStudentResults(text, className, subject, date);

            System.out.println("  Найдено записей студентов: " + studentResults.size());
            allResults.addAll(studentResults);

            if (DEBUG && studentResults.size() > 0) {
                System.out.println("  Первые 3 записи:");
                for (int i = 0; i < Math.min(3, studentResults.size()); i++) {
                    StudentResultData s = studentResults.get(i);
                    System.out.println("    Код: " + s.getCode() +
                            " | Всего %: " + s.getOverallPercent() +
                            " | Уровень: " + s.getMasteryLevel() +
                            " | Раздел1: " + s.getSection1Percent() +
                            " | Раздел2: " + s.getSection2Percent() +
                            " | Раздел3: " + s.getSection3Percent());
                }
            }
        }
    }

    private static Map<String, String> extractHeaderInfo(String text) {
        Map<String, String> info = new HashMap<>();
        info.put("date", "");
        info.put("subject", "");
        info.put("className", "");

        String[] lines = text.split("\n");

        for (String line : lines) {
            line = line.trim();

            // Ищем дату в формате "Дата: 11-12 ноября 2025г."
            if (line.contains("Дата:")) {
                Pattern datePattern = Pattern.compile("Дата:\\s*(.+?)\\s*(?=Функциональная|Округ:|$)");
                Matcher matcher = datePattern.matcher(line);
                if (matcher.find()) {
                    info.put("date", matcher.group(1).trim());
                }

                // Ищем предмет
                if (line.contains("Функциональная грамотность")) {
                    info.put("subject", "Функциональная грамотность");
                } else {
                    // Ищем другие предметы
                    Pattern subjectPattern = Pattern.compile("Дата:.+?\\s+(.+?)\\s+Округ:");
                    Matcher subjectMatcher = subjectPattern.matcher(line);
                    if (subjectMatcher.find()) {
                        info.put("subject", subjectMatcher.group(1).trim());
                    }
                }

                // Ищем класс
                Pattern classPattern = Pattern.compile("Класс:\\s*(\\d+[А-Яа-яЁё])");
                Matcher classMatcher = classPattern.matcher(line);
                if (classMatcher.find()) {
                    info.put("className", classMatcher.group(1).trim());
                }
            }
        }

        // Если не нашли в одной строке, ищем отдельно
        if (info.get("className").isEmpty()) {
            for (String line : lines) {
                Pattern classPattern = Pattern.compile("Класс:\\s*(\\d+[А-Яа-яЁё])");
                Matcher classMatcher = classPattern.matcher(line);
                if (classMatcher.find()) {
                    info.put("className", classMatcher.group(1).trim());
                    break;
                }
            }
        }

        return info;
    }

    private static List<StudentResultData> extractStudentResults(String text,
                                                                 String className,
                                                                 String subject,
                                                                 String date) {
        List<StudentResultData> results = new ArrayList<>();
        String[] lines = text.split("\n");

        boolean inResultsSection = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // Находим начало таблицы с результатами
            if (line.matches("\\d+\\s+\\d{4}-\\d{4}\\s+\\d+.*") ||
                    (line.matches(".*\\d{4}-\\d{4}.*") && (line.contains("+") || line.contains("-") || line.contains("N")))) {
                inResultsSection = true;
            }

            // Ищем строку с заголовками таблицы
            if (line.contains("Фамилия, имя") || line.contains("№ уч.")) {
                inResultsSection = false; // Это заголовок, еще не данные
            }

            if (inResultsSection && !line.isEmpty()) {
                // Пытаемся распарсить строку с данными студента
                StudentResultData result = parseStudentResultLine(line, className, subject, date);
                if (result != null) {
                    results.add(result);
                }
            }

            // Альтернативный поиск: строка начинается с номера и содержит код
            if (!inResultsSection && line.matches("^\\d+\\s+\\d{4}-\\d{4}.*")) {
                StudentResultData result = parseStudentResultLine(line, className, subject, date);
                if (result != null) {
                    results.add(result);
                }
            }
        }

        // Если не нашли данные обычным способом, пытаемся альтернативным парсингом
        if (results.isEmpty()) {
            results = alternativeParse(text, className, subject, date);
        }

        return results;
    }

    private static StudentResultData parseStudentResultLine(String line,
                                                            String className,
                                                            String subject,
                                                            String date) {
        try {
            // Удаляем лишние пробелы
            line = line.replaceAll("\\s+", " ").trim();

            // Пропускаем строки, которые не содержат код
            if (!line.matches(".*\\d{4}-\\d{4}.*")) {
                return null;
            }

            // Разбиваем строку на части
            String[] parts = line.split("\\s+");
            if (parts.length < 5) {
                return null;
            }

            // Ищем код участника
            String code = "";
            for (String part : parts) {
                if (part.matches("\\d{4}-\\d{4}")) {
                    code = part;
                    break;
                }
            }

            if (code.isEmpty()) {
                return null;
            }

            // Ищем проценты выполнения (только числа без %)
            List<String> percents = new ArrayList<>();
            Pattern percentPattern = Pattern.compile("(\\d{1,3})%");
            Matcher percentMatcher = percentPattern.matcher(line);

            while (percentMatcher.find()) {
                // Сохраняем только число без знака %
                percents.add(percentMatcher.group(1));
            }

            // Ищем общий процент выполнения (Всех)
            String overallPercent = "";
            // Ищем паттерн, где процент стоит перед словом "Всех" или сразу после блока с ответами
            Pattern overallPattern = Pattern.compile("(\\d{1,3})\\s*%\\s*Всех");
            Matcher overallMatcher = overallPattern.matcher(line);

            if (overallMatcher.find()) {
                overallPercent = overallMatcher.group(1); // Сохраняем только число
            } else if (!percents.isEmpty()) {
                // Если не нашли явно, берем первый процент в строке (обычно это общий процент)
                overallPercent = percents.get(0);
            }

            // Ищем проценты по разделам
            String section1Percent = "";
            String section2Percent = "";
            String section3Percent = "";

            if (percents.size() >= 7) {
                // Пытаемся определить, какие проценты соответствуют разделам
                // В примере: 17 81% 92% 63% 86% 100% 88% 33% 75% 71% 100%
                // 81% - общий, затем уровни, затем разделы

                // Вариант 1: последние 3 процента - это разделы
                section1Percent = percents.get(percents.size() - 3);
                section2Percent = percents.get(percents.size() - 2);
                section3Percent = percents.get(percents.size() - 1);
            }

            // Альтернативный поиск: ищем после слов "Раздел"
            Pattern sectionPattern = Pattern.compile("Раздел\\s*1\\s*(\\d{1,3})%\\s*Раздел\\s*2\\s*(\\d{1,3})%\\s*Раздел\\s*3\\s*(\\d{1,3})%");
            Matcher sectionMatcher = sectionPattern.matcher(line);
            if (sectionMatcher.find()) {
                section1Percent = sectionMatcher.group(1);
                section2Percent = sectionMatcher.group(2);
                section3Percent = sectionMatcher.group(3);
            }

            // Ищем уровень овладения УУД
            String masteryLevel = "";
            String[] levelKeywords = {"Повышенный", "Базовый", "Ниже базового", "Высокий", "Средний", "Низкий"};
            for (String level : levelKeywords) {
                if (line.contains(level)) {
                    masteryLevel = level;
                    break;
                }
            }

            // Если уровень не найден, ищем в конце строки
            if (masteryLevel.isEmpty()) {
                String[] words = line.split("\\s+");
                if (words.length > 0) {
                    String lastWord = words[words.length - 1];
                    for (String level : levelKeywords) {
                        if (lastWord.equals(level) || lastWord.contains(level)) {
                            masteryLevel = level;
                            break;
                        }
                    }
                }
            }

            // Создаем объект с результатами
            StudentResultData result = new StudentResultData();
            result.setCode(code);
            result.setClassName(className);
            result.setSubject(subject);
            result.setDate(date);
            result.setOverallPercent(overallPercent.isEmpty() ? "0" : overallPercent);
            result.setMasteryLevel(masteryLevel.isEmpty() ? "Не определен" : masteryLevel);
            result.setSection1Percent(section1Percent.isEmpty() ? "0" : section1Percent);
            result.setSection2Percent(section2Percent.isEmpty() ? "0" : section2Percent);
            result.setSection3Percent(section3Percent.isEmpty() ? "0" : section3Percent);

            if (DEBUG) {
                System.out.println("  Распарсено: Код=" + code +
                        ", Общий %=" + overallPercent +
                        ", Уровень=" + masteryLevel +
                        ", Разделы=" + section1Percent + "/" + section2Percent + "/" + section3Percent);
            }

            return result;

        } catch (Exception e) {
            if (DEBUG) {
                System.err.println("Ошибка парсинга строки: " + line);
                e.printStackTrace();
            }
            return null;
        }
    }

    private static List<StudentResultData> alternativeParse(String text,
                                                            String className,
                                                            String subject,
                                                            String date) {
        List<StudentResultData> results = new ArrayList<>();

        // Разбиваем текст на строки
        String[] lines = text.split("\n");

        for (String line : lines) {
            line = line.trim();

            // Ищем строки, содержащие код участника и результаты
            if (line.matches(".*\\d{4}-\\d{4}.*")) {
                // Пропускаем заголовки
                if (line.contains("Код участника") ||
                        line.contains("Фамилия, имя") ||
                        line.contains("№ уч.") ||
                        line.contains("Всех Уровень") ||
                        line.contains("% выполнения")) {
                    continue;
                }

                // Пытаемся разобрать строку как данные студента
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    // Ищем код в формате 9116-0195
                    String code = "";
                    for (String part : parts) {
                        if (part.matches("\\d{4}-\\d{4}")) {
                            code = part;
                            break;
                        }
                    }

                    if (!code.isEmpty()) {
                        // Ищем все проценты в строке (только числа без %)
                        Pattern percentPattern = Pattern.compile("(\\d{1,3})%");
                        Matcher percentMatcher = percentPattern.matcher(line);
                        List<String> percents = new ArrayList<>();

                        while (percentMatcher.find()) {
                            percents.add(percentMatcher.group(1)); // Сохраняем только число
                        }

                        // Общий процент (первый процент в строке)
                        String overallPercent = percents.isEmpty() ? "0" : percents.get(0);

                        // Проценты по разделам (последние 3 процента)
                        String section1Percent = "0";
                        String section2Percent = "0";
                        String section3Percent = "0";

                        if (percents.size() >= 3) {
                            section1Percent = percents.get(percents.size() - 3);
                            section2Percent = percents.get(percents.size() - 2);
                            section3Percent = percents.get(percents.size() - 1);
                        }

                        // Ищем уровень овладения
                        String masteryLevel = "";
                        String[] levelKeywords = {"Повышенный", "Базовый", "Ниже базового", "Высокий", "Средний", "Низкий"};
                        for (String level : levelKeywords) {
                            if (line.contains(level)) {
                                masteryLevel = level;
                                break;
                            }
                        }

                        // Создаем объект
                        StudentResultData result = new StudentResultData();
                        result.setCode(code);
                        result.setClassName(className);
                        result.setSubject(subject);
                        result.setDate(date);
                        result.setOverallPercent(overallPercent);
                        result.setMasteryLevel(masteryLevel.isEmpty() ? "Не определен" : masteryLevel);
                        result.setSection1Percent(section1Percent);
                        result.setSection2Percent(section2Percent);
                        result.setSection3Percent(section3Percent);

                        results.add(result);
                    }
                }
            }
        }

        return results;
    }

    private static void createResultsExcelFile(List<StudentResultData> results, String outputPath) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Результаты участников");

        // Стиль для заголовков
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short)12);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setBorderBottom(BorderStyle.THIN);

        // Создаем заголовки (без % в названиях)
        Row headerRow = sheet.createRow(0);
        String[] headers = {
                "№",
                "Код участника",
                "Класс",
                "Предмет",
                "Дата проведения",
                "% выполнения заданий (Всех)",
                "% выполнения заданий по разделу 1",
                "% выполнения заданий по разделу 2",
                "% выполнения заданий по разделу 3",
                "Уровень овладения УУД"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Заполняем данные (проценты как числа)
        int rowNum = 1;
        for (int i = 0; i < results.size(); i++) {
            StudentResultData result = results.get(i);
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(i + 1); // Номер по порядку

            // Код участника
            Cell codeCell = row.createCell(1);
            codeCell.setCellValue(result.getCode());

            // Класс
            row.createCell(2).setCellValue(result.getClassName());

            // Предмет
            row.createCell(3).setCellValue(result.getSubject());

            // Дата проведения
            row.createCell(4).setCellValue(result.getDate());

            // % выполнения заданий (Всех) - как число
            Cell overallCell = row.createCell(5);
            try {
                if (!result.getOverallPercent().isEmpty() && !result.getOverallPercent().equals("0")) {
                    overallCell.setCellValue(Double.parseDouble(result.getOverallPercent()));
                } else {
                    overallCell.setCellValue(0);
                }
            } catch (NumberFormatException e) {
                overallCell.setCellValue(result.getOverallPercent());
            }

            // % выполнения заданий по разделу 1 - как число
            Cell section1Cell = row.createCell(6);
            try {
                if (!result.getSection1Percent().isEmpty() && !result.getSection1Percent().equals("0")) {
                    section1Cell.setCellValue(Double.parseDouble(result.getSection1Percent()));
                } else {
                    section1Cell.setCellValue(0);
                }
            } catch (NumberFormatException e) {
                section1Cell.setCellValue(result.getSection1Percent());
            }

            // % выполнения заданий по разделу 2 - как число
            Cell section2Cell = row.createCell(7);
            try {
                if (!result.getSection2Percent().isEmpty() && !result.getSection2Percent().equals("0")) {
                    section2Cell.setCellValue(Double.parseDouble(result.getSection2Percent()));
                } else {
                    section2Cell.setCellValue(0);
                }
            } catch (NumberFormatException e) {
                section2Cell.setCellValue(result.getSection2Percent());
            }

            // % выполнения заданий по разделу 3 - как число
            Cell section3Cell = row.createCell(8);
            try {
                if (!result.getSection3Percent().isEmpty() && !result.getSection3Percent().equals("0")) {
                    section3Cell.setCellValue(Double.parseDouble(result.getSection3Percent()));
                } else {
                    section3Cell.setCellValue(0);
                }
            } catch (NumberFormatException e) {
                section3Cell.setCellValue(result.getSection3Percent());
            }

            // Уровень овладения УУД
            row.createCell(9).setCellValue(result.getMasteryLevel());
        }

        // Стиль для числовых ячеек (процентов)
        CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setDataFormat(workbook.createDataFormat().getFormat("0"));

        // Применяем числовой стиль к колонкам с процентами
        for (int i = 5; i <= 8; i++) {
            for (int rowIdx = 1; rowIdx <= results.size(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row != null) {
                    Cell cell = row.getCell(i);
                    if (cell != null) {
                        cell.setCellStyle(numberStyle);
                    }
                }
            }
        }

        // Автоподбор ширины колонок
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
            // Устанавливаем минимальную ширину
            if (sheet.getColumnWidth(i) < 3000) {
                sheet.setColumnWidth(i, 3000);
            }
        }

        // Замораживаем строку с заголовками
        sheet.createFreezePane(0, 1);

        // Сохраняем файл
        try (FileOutputStream outputStream = new FileOutputStream(outputPath)) {
            workbook.write(outputStream);
        }
        workbook.close();
    }

    // Внутренний класс для хранения результатов студентов
    static class StudentResultData {
        private String code;
        private String className;
        private String subject;
        private String date;
        private String overallPercent; // храним как строку без %
        private String masteryLevel;
        private String section1Percent; // храним как строку без %
        private String section2Percent; // храним как строку без %
        private String section3Percent; // храним как строку без %

        // Геттеры и сеттеры
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }

        public String getOverallPercent() { return overallPercent; }
        public void setOverallPercent(String overallPercent) {
            // Убираем % если он есть
            this.overallPercent = overallPercent.replace("%", "");
        }

        public String getMasteryLevel() { return masteryLevel; }
        public void setMasteryLevel(String masteryLevel) { this.masteryLevel = masteryLevel; }

        public String getSection1Percent() { return section1Percent; }
        public void setSection1Percent(String section1Percent) {
            // Убираем % если он есть
            this.section1Percent = section1Percent.replace("%", "");
        }

        public String getSection2Percent() { return section2Percent; }
        public void setSection2Percent(String section2Percent) {
            // Убираем % если он есть
            this.section2Percent = section2Percent.replace("%", "");
        }

        public String getSection3Percent() { return section3Percent; }
        public void setSection3Percent(String section3Percent) {
            // Убираем % если он есть
            this.section3Percent = section3Percent.replace("%", "");
        }

        @Override
        public String toString() {
            return String.format("%s | %s | %s | %s | %s | %s | %s | %s | %s",
                    code, className, subject, date, overallPercent,
                    section1Percent, section2Percent, section3Percent, masteryLevel);
        }
    }
}