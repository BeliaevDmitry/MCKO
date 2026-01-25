package org.school.MckoReport;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class PdfProcessor {

    private static boolean DEBUG = false; // Установите true для отладки

    public static void main(String[] args) {
        // Укажите путь к папке с PDF файлами
        String folderPath = "C:\\Users\\dimah\\Yandex.Disk\\ГБОУ №7\\МЦКО\\на обработку\\списки";
        // Укажите путь для сохранения Excel файла
        String outputExcelPath = "C:\\Users\\dimah\\Yandex.Disk\\ГБОУ №7\\МЦКО\\ФИО_код.xlsx";

        try {
            List<StudentData> allStudents = new ArrayList<>();

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
                    processPdfFile(pdfPath.toFile(), allStudents);
                } catch (Exception e) {
                    System.err.println("Ошибка при обработке файла: " + pdfPath);
                    e.printStackTrace();
                }
            }

            System.out.println("\n==========================================");
            System.out.println("Всего извлечено записей: " + allStudents.size());

            // Создаем Excel файл
            createExcelFile(allStudents, outputExcelPath);
            System.out.println("Обработка завершена. Результат сохранен в: " + outputExcelPath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processPdfFile(File pdfFile, List<StudentData> allStudents) throws IOException {
        String fileName = pdfFile.getName();
        String date = extractDateFromFileName(fileName);

        if (DEBUG) {
            System.out.println("  Имя файла: " + fileName);
            System.out.println("  Извлеченная дата: " + date);
        }

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            if (DEBUG) {
                System.out.println("  Первые 300 символов текста PDF:");
                System.out.println(text.substring(0, Math.min(300, text.length())));
                System.out.println("  ---");
            }

            // Извлекаем данные из текста PDF
            String className = extractClass(text, fileName);
            String subject = extractSubject(text, fileName);

            if (DEBUG) {
                System.out.println("  Класс: " + (className.isEmpty() ? "НЕ НАЙДЕН" : className));
                System.out.println("  Предмет: " + (subject.isEmpty() ? "НЕ НАЙДЕН" : subject));
            }

            // Извлекаем ФИО и коды студентов
            List<StudentData> students = extractStudents(text, className, subject, date);

            System.out.println("  Найдено студентов: " + students.size());
            allStudents.addAll(students);

            if (DEBUG && students.size() > 0) {
                System.out.println("  Первые 3 записи:");
                for (int i = 0; i < Math.min(3, students.size()); i++) {
                    StudentData s = students.get(i);
                    System.out.println("    " + s.getFullName() + " | " + s.getCode());
                }
            }
        }
    }

    private static String extractDateFromFileName(String fileName) {
        // Убираем расширение .pdf
        fileName = fileName.replace(".pdf", "").replace(".PDF", "");

        // Разделяем по нижнему подчеркиванию
        String[] parts = fileName.split("_");

        // Ищем часть, содержащую дату (обычно содержит месяцы или паттерн типа "11-12")
        for (String part : parts) {
            // Проверяем, содержит ли часть название месяца
            if (part.matches(".*\\d+.*") && (
                    part.contains("янв") || part.contains("фев") || part.contains("мар") ||
                            part.contains("апр") || part.contains("мая") || part.contains("июн") ||
                            part.contains("июл") || part.contains("авг") || part.contains("сен") ||
                            part.contains("окт") || part.contains("ноя") || part.contains("дек") ||
                            part.contains("Янв") || part.contains("Фев") || part.contains("Мар") ||
                            part.contains("Апр") || part.contains("Май") || part.contains("Июн") ||
                            part.contains("Июл") || part.contains("Авг") || part.contains("Сен") ||
                            part.contains("Окт") || part.contains("Ноя") || part.contains("Дек"))) {
                return part;
            }

            // Ищем паттерн типа "11-12" или "11-12ноября"
            if (part.matches("\\d{1,2}[-–—]\\d{1,2}.*")) {
                return part;
            }
        }

        return "дата не определена";
    }

    private static String extractClass(String text, String fileName) {
        // Сначала пробуем извлечь класс из имени файла
        String classFromFileName = extractClassFromFileName(fileName);
        if (!classFromFileName.isEmpty()) {
            return classFromFileName;
        }

        // Паттерны для поиска класса в тексте
        String[] patterns = {
                "Класс[\\s:]*([\\d]+[-–—\\s]*[А-Яа-яЁё№]+)",
                "класс[\\s:]*([\\d]+[-–—\\s]*[А-Яа-яЁё№]+)",
                "([\\d]+[-–—\\s]*[А-Яа-яЁё]+)\\s*[Кк]ласс",
                "Класс\\s*№?\\s*([\\d\\-А-Яа-яЁё]+)",
                "([\\d]+[-–—][А-Яа-яЁё])\\b"
        };

        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String found = matcher.group(1).trim().replace("№", "").replace(" ", "");
                return normalizeClass(found);
            }
        }

        // Поиск по строкам
        String[] lines = text.split("\n");
        for (int i = 0; i < Math.min(20, lines.length); i++) {
            String line = lines[i].trim();

            if (line.toLowerCase().contains("класс") && line.matches(".*\\d+.*")) {
                Pattern classPattern = Pattern.compile("([\\d]+[-–—\\s]*[А-Яа-яЁё]+)");
                Matcher classMatcher = classPattern.matcher(line);
                if (classMatcher.find()) {
                    String found = classMatcher.group(1).trim();
                    return normalizeClass(found);
                }
            }
        }

        return "";
    }

    private static String extractClassFromFileName(String fileName) {
        fileName = fileName.replace(".pdf", "").replace(".PDF", "");
        String[] parts = fileName.split("_");

        for (String part : parts) {
            // Ищем паттерн "5-В", "5-В" и т.д.
            if (part.matches("[\\d]+[-–—\\s]*[А-Яа-яЁё]+")) {
                return normalizeClass(part);
            }

            // Ищем паттерн с цифрой и буквой
            if (part.matches(".*[\\d]+[А-Яа-яЁё].*")) {
                Pattern p = Pattern.compile("([\\d]+)[-–—\\s]*([А-Яа-яЁё]+)");
                Matcher m = p.matcher(part);
                if (m.find()) {
                    return m.group(1) + "-" + m.group(2);
                }
            }
        }

        return "";
    }

    private static String normalizeClass(String className) {
        if (className == null || className.isEmpty()) return "";

        String normalized = className.trim()
                .replace(" ", "")
                .replace("–", "-")
                .replace("—", "-")
                .replace("№", "")
                .replace("класс", "")
                .replace("Класс", "")
                .replace(":", "")
                .replace(".", "");

        // Если нет дефиса, но есть цифра и буква подряд (например "5В")
        if (!normalized.contains("-") && normalized.matches(".*\\d[А-Яа-яЁё].*")) {
            normalized = normalized.replaceAll("([\\d])([А-Яа-яЁё])", "$1-$2");
        }

        return normalized.toUpperCase();
    }

    private static String extractSubject(String text, String fileName) {
        // Сначала пробуем извлечь из имени файла
        String subjectFromFileName = extractSubjectFromFileName(fileName);
        if (!subjectFromFileName.isEmpty()) {
            return subjectFromFileName;
        }

        // Ищем в тексте после информации о классе
        String subjectFromText = extractSubjectFromText(text);
        if (!subjectFromText.isEmpty()) {
            return subjectFromText;
        }

        // Ищем по ключевым словам в тексте
        String subjectByKeywords = extractSubjectByKeywords(text);
        if (!subjectByKeywords.isEmpty()) {
            return subjectByKeywords;
        }

        return "Предмет не указан";
    }

    private static String extractSubjectFromFileName(String fileName) {
        fileName = fileName.replace(".pdf", "").replace(".PDF", "");
        String[] parts = fileName.split("_");

        // Ищем часть, которая не является номером, классом, датой или служебным словом
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];

            // Пропускаем числовые части (9116)
            if (part.matches("\\d+")) continue;

            // Пропускаем классы (5-В, 5-В и т.д.)
            if (part.matches("[\\d]+[-–—\\s]*[А-Яа-яЁё]+")) continue;

            // Пропускаем даты
            if (part.matches(".*\\d+.*") && (
                    part.contains("янв") || part.contains("фев") || part.contains("мар") ||
                            part.contains("апр") || part.contains("мая") || part.contains("июн") ||
                            part.contains("июл") || part.contains("авг") || part.contains("сен") ||
                            part.contains("окт") || part.contains("ноя") || part.contains("дек") ||
                            part.matches("\\d{1,2}[-–—]\\d{1,2}.*"))) continue;

            // Пропускаем служебные слова
            if (part.equalsIgnoreCase("список") ||
                    part.equalsIgnoreCase("кодов") ||
                    part.equalsIgnoreCase("участников")) continue;

            // Если часть содержит кириллицу и не слишком короткая
            if (part.matches(".*[А-Яа-яЁё].*") && part.length() > 1) {
                return expandSubjectAbbreviation(part);
            }
        }

        return "";
    }

    private static String extractSubjectFromText(String text) {
        String[] lines = text.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // Если находим строку с классом
            if ((line.toLowerCase().contains("класс") && line.matches(".*\\d+.*")) ||
                    line.matches("Класс:.*")) {

                // Смотрим следующие строки
                for (int j = 1; j <= 3; j++) {
                    if (i + j < lines.length) {
                        String nextLine = lines[i + j].trim();

                        if (!nextLine.isEmpty() &&
                                !nextLine.toLowerCase().contains("фио") &&
                                !nextLine.toLowerCase().contains("код") &&
                                !nextLine.toLowerCase().contains("участник") &&
                                !nextLine.matches("\\d{4}-\\d{4}") &&
                                !nextLine.matches(".*[Кк]ласс.*")) {

                            if (!nextLine.equalsIgnoreCase("список") &&
                                    !nextLine.equalsIgnoreCase("кодов") &&
                                    nextLine.length() > 2) {
                                return expandSubjectAbbreviation(nextLine);
                            }
                        }
                    }
                }
            }
        }

        return "";
    }

    private static String extractSubjectByKeywords(String text) {
        // Словарь предметов и их ключевых слов
        Map<String, String[]> subjectKeywords = new HashMap<>();
        subjectKeywords.put("Математика", new String[]{"математика", "мат", "МАТ", "Math", "math"});
        subjectKeywords.put("Русский язык", new String[]{"русский", "русск", "русс", "РЯ", "русский язык"});
        subjectKeywords.put("Литература", new String[]{"литература", "лит-ра", "лит", "Лит"});
        subjectKeywords.put("Английский язык", new String[]{"английский", "англ", "анг", "English", "english"});
        subjectKeywords.put("История", new String[]{"история", "ист", "Hist", "hist"});
        subjectKeywords.put("Обществознание", new String[]{"обществознание", "общ", "общество"});
        subjectKeywords.put("География", new String[]{"география", "геогр", "гео"});
        subjectKeywords.put("Биология", new String[]{"биология", "биол", "био"});
        subjectKeywords.put("Физика", new String[]{"физика", "физ"});
        subjectKeywords.put("Химия", new String[]{"химия", "хим"});
        subjectKeywords.put("Информатика", new String[]{"информатика", "инф", "информац", "ИКТ"});
        subjectKeywords.put("ОБЖ", new String[]{"ОБЖ", "обж", "основы безопасности"});
        subjectKeywords.put("Технология", new String[]{"технология", "техн", "труд"});
        subjectKeywords.put("Физкультура", new String[]{"физкультура", "физ-ра", "физра"});
        subjectKeywords.put("ИЗО", new String[]{"ИЗО", "изо", "изобразительное"});
        subjectKeywords.put("Музыка", new String[]{"музыка", "муз"});
        subjectKeywords.put("Функциональная грамотность", new String[]{"функциональная", "функц", "ФКГ", "фкг", "грамотность"});
        subjectKeywords.put("Чтение", new String[]{"чтение", "читательская"});

        // Ищем ключевые слова в тексте
        for (Map.Entry<String, String[]> entry : subjectKeywords.entrySet()) {
            String subjectName = entry.getKey();
            String[] keywords = entry.getValue();

            for (String keyword : keywords) {
                if (text.toLowerCase().contains(keyword.toLowerCase())) {
                    return subjectName;
                }
            }
        }

        return "";
    }

    private static String expandSubjectAbbreviation(String abbreviation) {
        // Расшифровка аббревиатур
        Map<String, String> abbreviationMap = new HashMap<>();
        abbreviationMap.put("ФКГ", "Функциональная грамотность");
        abbreviationMap.put("фкг", "Функциональная грамотность");
        abbreviationMap.put("МАТ", "Математика");
        abbreviationMap.put("мат", "Математика");
        abbreviationMap.put("РЯ", "Русский язык");
        abbreviationMap.put("ря", "Русский язык");
        abbreviationMap.put("ЛИТ", "Литература");
        abbreviationMap.put("лит", "Литература");
        abbreviationMap.put("АНГЛ", "Английский язык");
        abbreviationMap.put("англ", "Английский язык");
        abbreviationMap.put("ИСТ", "История");
        abbreviationMap.put("ист", "История");
        abbreviationMap.put("ОБЩ", "Обществознание");
        abbreviationMap.put("общ", "Обществознание");
        abbreviationMap.put("ГЕО", "География");
        abbreviationMap.put("гео", "География");
        abbreviationMap.put("БИО", "Биология");
        abbreviationMap.put("био", "Биология");
        abbreviationMap.put("ФИЗ", "Физика");
        abbreviationMap.put("физ", "Физика");
        abbreviationMap.put("ХИМ", "Химия");
        abbreviationMap.put("хим", "Химия");
        abbreviationMap.put("ИНФ", "Информатика");
        abbreviationMap.put("инф", "Информатика");
        abbreviationMap.put("ИКТ", "Информатика");
        abbreviationMap.put("икт", "Информатика");

        if (abbreviationMap.containsKey(abbreviation)) {
            return abbreviationMap.get(abbreviation);
        }

        return normalizeSubjectName(abbreviation);
    }

    private static String normalizeSubjectName(String subject) {
        if (subject == null || subject.isEmpty()) {
            return "Предмет не указан";
        }

        String normalized = subject.trim();

        if (normalized.length() > 1) {
            normalized = normalized.substring(0, 1).toUpperCase() +
                    normalized.substring(1).toLowerCase();
        }

        return normalized;
    }

    private static List<StudentData> extractStudents(String text, String className,
                                                     String subject, String date) {
        List<StudentData> students = new ArrayList<>();
        String[] lines = text.split("\n");

        boolean inStudentSection = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // Находим начало раздела со студентами
            if (line.contains("ФИО обучающегося") ||
                    line.contains("ФИО участника") ||
                    (line.contains("ФИО") && line.contains("Код"))) {
                inStudentSection = true;
                if (DEBUG) System.out.println("  Начало списка студентов в строке: " + (i+1));
                continue;
            }

            if (inStudentSection) {
                // Пропускаем пустые строки и служебные строки
                if (line.isEmpty() ||
                        line.equals("Код") ||
                        line.equals("участника") ||
                        line.equals("обучающегося") ||
                        line.equals("Код участника") ||
                        line.equals("участника") ||
                        line.contains("ФИО") && line.contains("Код")) {
                    continue;
                }

                // Разделяем строку на ФИО и код
                // Ищем код в формате 9116-0092
                Pattern codePattern = Pattern.compile("(\\d{4}-\\d{4})$");
                Matcher matcher = codePattern.matcher(line);

                if (matcher.find()) {
                    String code = matcher.group(1);
                    String name = line.substring(0, matcher.start()).trim();

                    // Фильтруем некорректные имена
                    if (!name.isEmpty() &&
                            !name.equals("участника") &&
                            !name.equals("обучающегося") &&
                            !name.toLowerCase().contains("фио") &&
                            !name.toLowerCase().contains("код") &&
                            name.contains(" ") &&
                            name.matches(".*[А-ЯЁ][а-яё]+.*[А-ЯЁ][а-яё]+.*")) {

                        StudentData student = new StudentData();
                        student.setFullName(name);
                        student.setCode(code);
                        student.setClassName(className);
                        student.setSubject(subject);
                        student.setDate(date);
                        students.add(student);
                    }
                } else {
                    // Если код не найден в текущей строке, проверяем следующую строку
                    if (i + 1 < lines.length) {
                        String nextLine = lines[i + 1].trim();
                        matcher = codePattern.matcher(nextLine);

                        if (matcher.find()) {
                            String code = matcher.group(1);

                            // Проверяем текущую строку на наличие ФИО
                            if (!line.isEmpty() &&
                                    !line.equals("Код") &&
                                    !line.equals("участника") &&
                                    !line.toLowerCase().contains("фио") &&
                                    !line.toLowerCase().contains("код") &&
                                    line.contains(" ") &&
                                    line.matches(".*[А-ЯЁ][а-яё]+.*[А-ЯЁ][а-яё]+.*")) {

                                StudentData student = new StudentData();
                                student.setFullName(line);
                                student.setCode(code);
                                student.setClassName(className);
                                student.setSubject(subject);
                                student.setDate(date);
                                students.add(student);
                            }
                        }
                    }
                }
            }
        }

        return students;
    }

    private static void createExcelFile(List<StudentData> students, String outputPath) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Список участников");

        // Стиль для заголовков
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short)12);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setBorderBottom(BorderStyle.THIN);

        // Создаем заголовки
        Row headerRow = sheet.createRow(0);
        String[] headers = {"№", "ФИО", "Класс", "Код", "Предмет", "Дата"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Заполняем данные
        int rowNum = 1;
        for (int i = 0; i < students.size(); i++) {
            StudentData student = students.get(i);
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(i + 1); // Номер по порядку
            row.createCell(1).setCellValue(student.getFullName());
            row.createCell(2).setCellValue(student.getClassName());
            row.createCell(3).setCellValue(student.getCode());
            row.createCell(4).setCellValue(student.getSubject());
            row.createCell(5).setCellValue(student.getDate());
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

    // Внутренний класс для хранения данных о студенте
    static class StudentData {
        private String fullName;
        private String className;
        private String code;
        private String subject;
        private String date;

        // Геттеры и сеттеры
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }

        @Override
        public String toString() {
            return String.format("%s | %s | %s | %s | %s",
                    fullName, className, code, subject, date);
        }
    }
}