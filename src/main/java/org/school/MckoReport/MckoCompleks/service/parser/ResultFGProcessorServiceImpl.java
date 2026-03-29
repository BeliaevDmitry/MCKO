package org.school.MckoReport.MckoCompleks.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.school.MckoReport.MckoCompleks.expextion.ProcessingException;
import org.school.MckoReport.MckoCompleks.model.StudentResultFGData;
import org.school.MckoReport.MckoCompleks.service.parser.ResultFGProcessorService;
import org.school.MckoReport.MckoCompleks.util.DateNormalizerUtil;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ResultFGProcessorServiceImpl implements ResultFGProcessorService {

    private static final String COMPARISON_EQUAL = "равно";
    private static final String COMPARISON_HIGHER = "выше";
    private static final String COMPARISON_LOWER = "ниже";
    private static final String COMPARISON_UNKNOWN = "неизвестно";


    @Override
    public List<StudentResultFGData> extractStudentsResultFG(Path path) {
        List<StudentResultFGData> allResults = new ArrayList<>();

        try (PDDocument document = PDDocument.load(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            Map<String, String> headerInfo = extractHeaderInfo(text);
            String date = headerInfo.get("date");
            String subject = headerInfo.get("subject");
            String className = normalizeClassName(headerInfo.get("className"));
            String school = headerInfo.get("school");
            date = DateNormalizerUtil.normalizeDate(date);

            validateHeader(path, date, subject, className);

            List<StudentResultFGData> studentResults = extractStudentResults(text,
                    className, subject, date, school);

            BenchmarkPercents benchmarkPercents = extractBenchmarkPercents(text);
            applyBenchmarkComparisons(studentResults, benchmarkPercents);

            log.info("Найдено записей студентов: {}", studentResults.size());
            allResults.addAll(studentResults);

        } catch (IOException e) {
            throw new ProcessingException("Ошибка чтения PDF ФГ: " + path.getFileName(), e);
        }
        return allResults;
    }

    private void validateHeader(Path path, String date, String subject, String className) {
        List<String> missing = new ArrayList<>();
        if (!DateNormalizerUtil.isValidDate(date)) {
            missing.add("дата");
        }
        if (subject == null || subject.trim().isEmpty()) {
            missing.add("предмет");
        }
        if (className == null || className.trim().isEmpty()) {
            missing.add("класс");
        }

        if (!missing.isEmpty()) {
            throw new ProcessingException(
                    "Файл ФГ не прошел валидацию: " +
                            String.join(", ", missing) +
                            " (" + path.getFileName() + ")"
            );
        }
    }

    private static Map<String, String> extractHeaderInfo(String text) {
        Map<String, String> info = new HashMap<>();
        info.put("date", "");
        info.put("subject", "");
        info.put("className", "");
        info.put("school", "");

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

                // Ищем школу
                Pattern schoolPattern = Pattern.compile("Школа:\\s*(.*?)(?=\\s*$|\\s*[Кк]ласс:|\\s*[Пп]редмет:|$)", Pattern.CASE_INSENSITIVE);
                Matcher schoolMatcher = schoolPattern.matcher(line);
                if (schoolMatcher.find()) {
                    info.put("school", schoolMatcher.group(1).trim());
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

    private String normalizeClassName(String className) {
        if (className == null || className.isEmpty()) {
            return className;
        }

        // Регулярное выражение для поиска позиции между цифрами и буквами
        // или между буквами и цифрами (для двустороннего преобразования)
        return className.replaceAll("(?<=\\d)(?=\\p{L})|(?<=\\p{L})(?=\\d)", "-");
    }

    private static List<StudentResultFGData> extractStudentResults(String text,
                                                                   String className,
                                                                   String subject,
                                                                   String date,
                                                                   String school) {
        List<StudentResultFGData> results = new ArrayList<>();
        String[] lines = text.split("\n");

        boolean inResultsSection = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // Находим начало таблицы с результатами
            if (line.matches("\\d+\\s+\\d{4}-\\d{4}\\s+\\d+.*") ||
                    (line.matches(".*\\d{4}-\\d{4}.*") && (line.contains("+")
                            || line.contains("-") || line.contains("N")))) {
                inResultsSection = true;
            }

            // Ищем строку с заголовками таблицы
            if (line.contains("Фамилия, имя") || line.contains("№ уч.")) {
                inResultsSection = false; // Это заголовок, еще не данные
            }

            if (inResultsSection && !line.isEmpty()) {
                // Пытаемся распарсить строку с данными студента
                StudentResultFGData result = parseStudentResultLine(line, className, subject, date, school);
                if (result != null) {
                    results.add(result);
                }
            }

            // Альтернативный поиск: строка начинается с номера и содержит код
            if (!inResultsSection && line.matches("^\\d+\\s+\\d{4}-\\d{4}.*")) {
                StudentResultFGData result = parseStudentResultLine(line, className, subject, date, school);
                if (result != null) {
                    results.add(result);
                }
            }
        }

        // Если не нашли данные обычным способом, пытаемся альтернативным парсингом
        if (results.isEmpty()) {
            results = alternativeParse(text, className, subject, date, school);
        }

        return results;
    }

    private static StudentResultFGData parseStudentResultLine(String line,
                                                              String className,
                                                              String subject,
                                                              String date,
                                                              String school) {
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
            Pattern sectionPattern =
                    Pattern.compile("Раздел\\s*1\\s*(\\d{1,3})%\\s*Раздел\\s*2\\s*(\\d{1,3})%\\s*Раздел\\s*3\\s*(\\d{1,3})%");
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
            StudentResultFGData result = new StudentResultFGData();
            result.setCode(code);
            result.setClassName(className);
            result.setSubject(subject);
            result.setDate(date);
            result.setSchoolYear(DateNormalizerUtil.calculateSchoolYear(date));
            result.setSchool(school);
            result.setOverallPercent(overallPercent.isEmpty() ? "0" : overallPercent);
            result.setMasteryLevel(masteryLevel.isEmpty() ? "Не определен" : masteryLevel);
            result.setSection1Percent(section1Percent.isEmpty() ? "0" : section1Percent);
            result.setSection2Percent(section2Percent.isEmpty() ? "0" : section2Percent);
            result.setSection3Percent(section3Percent.isEmpty() ? "0" : section3Percent);


            return result;

        } catch (Exception e) {
            log.warn("Ошибка парсинга строки результата ФГ: {}", line);
            return null;
        }
    }

    private static List<StudentResultFGData> alternativeParse(String text,
                                                              String className,
                                                              String subject,
                                                              String date,
                                                              String school) {
        List<StudentResultFGData> results = new ArrayList<>();

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
                        String[] levelKeywords = {"Повышенный", "Базовый",
                                "Ниже базового", "Высокий", "Средний", "Низкий"};
                        for (String level : levelKeywords) {
                            if (line.contains(level)) {
                                masteryLevel = level;
                                break;
                            }
                        }

                        // Создаем объект
                        StudentResultFGData result = new StudentResultFGData();
                        result.setCode(code);
                        result.setClassName(className);
                        result.setSubject(subject);
                        result.setDate(date);
                        result.setSchool(school);
                        result.setSchoolYear(DateNormalizerUtil.calculateSchoolYear(date));
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

    private BenchmarkPercents extractBenchmarkPercents(String text) {
        Pattern pattern = Pattern.compile(
                "Средний\\s+%\\s+выполнения\\s+(?:диагн\\.\\s+работы|теста):\\s*(\\d+)%\\s*(\\d+)%",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                Integer classPercent = Integer.parseInt(matcher.group(1));
                Integer cityPercent = Integer.parseInt(matcher.group(2));
                return new BenchmarkPercents(classPercent, cityPercent);
            } catch (NumberFormatException e) {
                log.warn("Не удалось распарсить проценты класса/города: {} {}", matcher.group(1), matcher.group(2));
            }
        }
        return new BenchmarkPercents(null, null);
    }

    private void applyBenchmarkComparisons(List<StudentResultFGData> studentResults, BenchmarkPercents benchmarkPercents) {
        for (StudentResultFGData student : studentResults) {
            student.setClassPercent(benchmarkPercents.classPercent());
            student.setCityPercent(benchmarkPercents.cityPercent());

            Integer studentPercent = parseStudentPercent(student.getOverallPercent());
            student.setClassComparison(comparePercent(studentPercent, benchmarkPercents.classPercent()));
            student.setCityComparison(comparePercent(studentPercent, benchmarkPercents.cityPercent()));
        }
    }

    private Integer parseStudentPercent(String overallPercent) {
        if (overallPercent == null || overallPercent.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(overallPercent.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String comparePercent(Integer studentPercent, Integer benchmarkPercent) {
        if (studentPercent == null || benchmarkPercent == null) {
            return COMPARISON_UNKNOWN;
        }
        if (studentPercent.equals(benchmarkPercent)) {
            return COMPARISON_EQUAL;
        }
        return studentPercent > benchmarkPercent ? COMPARISON_HIGHER : COMPARISON_LOWER;
    }

    private record BenchmarkPercents(Integer classPercent, Integer cityPercent) {
    }
}
