package org.school.MckoReport.MckoCompleks.util;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class DateNormalizerUtil {

    private static final Map<String, Integer> MONTH_NUMBERS = new HashMap<>();
    private static final Map<String, Integer> ENGLISH_MONTHS = new HashMap<>();

    static {
        // Русские названия месяцев
        MONTH_NUMBERS.put("января", 1); MONTH_NUMBERS.put("янв", 1);
        MONTH_NUMBERS.put("февраля", 2); MONTH_NUMBERS.put("фев", 2);
        MONTH_NUMBERS.put("марта", 3); MONTH_NUMBERS.put("мар", 3);
        MONTH_NUMBERS.put("апреля", 4); MONTH_NUMBERS.put("апр", 4);
        MONTH_NUMBERS.put("мая", 5); MONTH_NUMBERS.put("май", 5);
        MONTH_NUMBERS.put("июня", 6); MONTH_NUMBERS.put("июн", 6);
        MONTH_NUMBERS.put("июля", 7); MONTH_NUMBERS.put("июл", 7);
        MONTH_NUMBERS.put("августа", 8); MONTH_NUMBERS.put("авг", 8);
        MONTH_NUMBERS.put("сентября", 9); MONTH_NUMBERS.put("сен", 9);
        MONTH_NUMBERS.put("октября", 10); MONTH_NUMBERS.put("окт", 10);
        MONTH_NUMBERS.put("ноября", 11); MONTH_NUMBERS.put("ноя", 11);
        MONTH_NUMBERS.put("декабря", 12); MONTH_NUMBERS.put("дек", 12);

        // Английские названия месяцев
        ENGLISH_MONTHS.put("january", 1); ENGLISH_MONTHS.put("jan", 1);
        ENGLISH_MONTHS.put("february", 2); ENGLISH_MONTHS.put("feb", 2);
        ENGLISH_MONTHS.put("march", 3); ENGLISH_MONTHS.put("mar", 3);
        ENGLISH_MONTHS.put("april", 4); ENGLISH_MONTHS.put("apr", 4);
        ENGLISH_MONTHS.put("may", 5);
        ENGLISH_MONTHS.put("june", 6); ENGLISH_MONTHS.put("jun", 6);
        ENGLISH_MONTHS.put("july", 7); ENGLISH_MONTHS.put("jul", 7);
        ENGLISH_MONTHS.put("august", 8); ENGLISH_MONTHS.put("aug", 8);
        ENGLISH_MONTHS.put("september", 9); ENGLISH_MONTHS.put("sep", 9);
        ENGLISH_MONTHS.put("october", 10); ENGLISH_MONTHS.put("oct", 10);
        ENGLISH_MONTHS.put("november", 11); ENGLISH_MONTHS.put("nov", 11);
        ENGLISH_MONTHS.put("december", 12); ENGLISH_MONTHS.put("dec", 12);
    }

    private static final String TARGET_FORMAT = "dd.MM.yyyy";
    private static final DateTimeFormatter TARGET_FORMATTER =
            DateTimeFormatter.ofPattern(TARGET_FORMAT);

    /**
     * Основной метод для нормализации даты
     */
    public static String normalizeDateWithFileFallback(String rawDate, String filePath) {
        if (rawDate == null || rawDate.trim().isEmpty()) {
            String dateFromPath = extractExplicitDateFromFilePath(filePath);
            return dateFromPath != null ? dateFromPath : LocalDate.now().format(TARGET_FORMATTER);
        }

        String trimmedDate = rawDate.trim();
        log.debug("Нормализация даты: '{}' для файла: {}", trimmedDate, filePath);

        // 1. Пробуем нормализовать с учетом года из пути файла
        String yearFromPath = extractYearFromFilePath(filePath);
        if (yearFromPath != null) {
            String normalizedWithYear = normalizeDateWithYear(trimmedDate, yearFromPath);
            if (isValidDate(normalizedWithYear)) {
                log.debug("Успешно нормализовано с годом из пути ({}): {}", yearFromPath, normalizedWithYear);
                return normalizedWithYear;
            }
        }

        // 2. Пробуем извлечь полную дату из пути
        String dateFromPath = extractExplicitDateFromFilePath(filePath);
        if (dateFromPath != null && isValidDate(dateFromPath)) {
            log.debug("Используется дата из пути файла: {}", dateFromPath);
            return dateFromPath;
        }

        // 3. Пробуем нормализовать как есть (может уже содержать год)
        String normalized = normalizeDate(trimmedDate);
        if (isValidDate(normalized)) {
            return normalized;
        }

        // 4. Используем текущую дату
        String currentDate = LocalDate.now().format(TARGET_FORMATTER);
        log.debug("Используется текущая дата: {}", currentDate);
        return currentDate;
    }

    /**
     * Извлекает год из пути файла
     */
    private static String extractYearFromFilePath(String filePath) {
        if (filePath == null) return null;

        log.debug("Извлечение года из пути: {}", filePath);

        // Сначала ищем явные даты в формате дд-мм-гг или дд.мм.гг
        Pattern datePattern = Pattern.compile("(\\d{1,2})[-.](\\d{1,2})[-.](\\d{2,4})");
        Matcher dateMatcher = datePattern.matcher(filePath);
        while (dateMatcher.find()) {
            String yearStr = dateMatcher.group(3);
            if (yearStr.length() == 4) {
                // Проверяем, что это реальный год (между 2000 и текущим+1 годом)
                try {
                    int year = Integer.parseInt(yearStr);
                    int currentYear = LocalDate.now().getYear();
                    if (year >= 2000 && year <= currentYear + 1) {
                        log.debug("Найден год из даты: {} -> {}", dateMatcher.group(), yearStr);
                        return yearStr;
                    }
                } catch (Exception e) {
                    // ignore
                }
            } else if (yearStr.length() == 2) {
                try {
                    int shortYear = Integer.parseInt(yearStr);
                    if (shortYear >= 0 && shortYear <= 99) {
                        String year = "20" + yearStr;
                        log.debug("Найден год из короткой даты: {} -> {}", dateMatcher.group(), year);
                        return year;
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        // Ищем паттерны типа dec25
        Pattern monthYearPattern = Pattern.compile("([a-z]{3})(\\d{2})", Pattern.CASE_INSENSITIVE);
        Matcher monthYearMatcher = monthYearPattern.matcher(filePath);
        while (monthYearMatcher.find()) {
            String monthStr = monthYearMatcher.group(1).toLowerCase();
            String yearShort = monthYearMatcher.group(2);

            if (ENGLISH_MONTHS.containsKey(monthStr)) {
                try {
                    int shortYear = Integer.parseInt(yearShort);
                    if (shortYear >= 0 && shortYear <= 99) {
                        String year = "20" + yearShort;
                        log.debug("Найден год из формата месяц+год: {} -> {}",
                                monthYearMatcher.group(), year);
                        return year;
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        // Ищем четырехзначные числа, которые могут быть годами
        Pattern fourDigitPattern = Pattern.compile("\\D(\\d{4})\\D");
        Matcher fourDigitMatcher = fourDigitPattern.matcher(filePath);
        while (fourDigitMatcher.find()) {
            String yearStr = fourDigitMatcher.group(1);
            try {
                int year = Integer.parseInt(yearStr);
                int currentYear = LocalDate.now().getYear();
                // Проверяем, что это разумный год (не 9116, а что-то между 2000 и текущим+1)
                if (year >= 2000 && year <= currentYear + 1) {
                    log.debug("Найден четырехзначный год: {}", yearStr);
                    return yearStr;
                }
            } catch (Exception e) {
                // ignore
            }
        }

        // Ищем двузначные годы в конце слов
        Pattern shortYearPattern = Pattern.compile("(?:\\D|^)(\\d{2})(?:\\D|$)");
        Matcher shortYearMatcher = shortYearPattern.matcher(filePath);
        while (shortYearMatcher.find()) {
            String yearShort = shortYearMatcher.group(1);
            try {
                int shortYear = Integer.parseInt(yearShort);
                if (shortYear >= 0 && shortYear <= 99) {
                    String year = "20" + yearShort;
                    log.debug("Найден двузначный год: {} -> {}", yearShort, year);
                    return year;
                }
            } catch (Exception e) {
                // ignore
            }
        }

        log.debug("Год в пути не найден");
        return null;
    }

    /**
     * Извлекает дату из пути файла
     */
    private static String extractExplicitDateFromFilePath(String filePath) {
        if (filePath == null) {
            return null;
        }

        log.debug("Извлечение даты из пути: {}", filePath);

        // Паттерны для поиска дат в пути
        Pattern[] patterns = {
                // dec25 (месяц+год) - проверяем валидность года
                Pattern.compile("([a-z]{3})(\\d{2})", Pattern.CASE_INSENSITIVE),
                // 15-05-25 или 15.05.25
                Pattern.compile("(\\d{1,2})[-\\.](\\d{1,2})[-\\.](\\d{2})(?![\\d])"),
                // 25-11-2025 или 25.11.2025
                Pattern.compile("(\\d{1,2})[-\\.](\\d{1,2})[-\\.](\\d{4})(?![\\d])"),
                // 10_December_2025
                Pattern.compile("(\\d{1,2})_([a-zA-Z]+)_(\\d{4})", Pattern.CASE_INSENSITIVE)
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(filePath);
            while (matcher.find()) {
                try {
                    String patternStr = pattern.pattern();

                    // Обработка dec25
                    if (patternStr.contains("[a-z]{3})(\\d{2})")) {
                        String monthStr = matcher.group(1).toLowerCase();
                        String yearShort = matcher.group(2);

                        // Проверяем валидность года
                        int shortYear = Integer.parseInt(yearShort);
                        if (shortYear < 0 || shortYear > 99) continue;

                        String year = "20" + yearShort;
                        int yearInt = Integer.parseInt(year);
                        int currentYear = LocalDate.now().getYear();

                        // Проверяем, что год в разумных пределах
                        if (yearInt < 2000 || yearInt > currentYear + 1) continue;

                        Integer month = ENGLISH_MONTHS.get(monthStr);
                        if (month != null) {
                            String result = "01." + String.format("%02d", month) + "." + year;
                            log.debug("Найдена дата в формате dec25: {} -> {}",
                                    matcher.group(), result);
                            return result;
                        }
                    }
                    // Обработка 10_December_2025
                    else if (patternStr.contains("_")) {
                        String day = String.format("%02d", Integer.parseInt(matcher.group(1)));
                        String monthStr = matcher.group(2).toLowerCase();
                        String year = matcher.group(3);

                        // Проверяем валидность года
                        int yearInt = Integer.parseInt(year);
                        int currentYear = LocalDate.now().getYear();
                        if (yearInt < 2000 || yearInt > currentYear + 1) continue;

                        Integer month = ENGLISH_MONTHS.get(monthStr);
                        if (month != null) {
                            String result = day + "." + String.format("%02d", month) + "." + year;
                            log.debug("Найдена дата в формате 10_December_2025: {} -> {}",
                                    matcher.group(), result);
                            return result;
                        }
                    }
                    // Обработка числовых форматов
                    else {
                        String day = String.format("%02d", Integer.parseInt(matcher.group(1)));
                        String month = String.format("%02d", Integer.parseInt(matcher.group(2)));
                        String year = matcher.group(3);

                        if (year.length() == 2) {
                            year = "20" + year;
                        }

                        // Проверяем валидность года
                        int yearInt = Integer.parseInt(year);
                        int currentYear = LocalDate.now().getYear();
                        if (yearInt < 2000 || yearInt > currentYear + 1) {
                            log.debug("Пропускаем невалидный год: {}", year);
                            continue;
                        }

                        String result = day + "." + month + "." + year;
                        log.debug("Найдена числовая дата: {} -> {}", matcher.group(), result);
                        return result;
                    }
                } catch (Exception e) {
                    log.debug("Ошибка извлечения даты из пути: {}", e.getMessage());
                }
            }
        }

        // Если явную дату не нашли, возвращаем null
        log.debug("Дата в пути не найдена");
        return null;
    }

    /**
     * Нормализует дату с указанным годом
     */
    private static String normalizeDateWithYear(String rawDate, String year) {
        if (rawDate == null || year == null) return null;

        String cleaned = cleanDateText(rawDate);
        log.debug("Нормализация с годом {}: '{}'", year, cleaned);

        // Если дата уже содержит год, нормализуем как есть
        if (containsYear(cleaned)) {
            return normalizeDate(cleaned);
        }

        // Для дат без года создаем версию с указанным годом
        String dateWithYear = createDateWithYear(cleaned, year);
        log.debug("Дата с добавленным годом: '{}' -> '{}'", cleaned, dateWithYear);

        // Нормализуем
        String normalized = normalizeDateInternal(dateWithYear, year);

        if (isValidDate(normalized)) {
            return normalized;
        }

        return null;
    }

    /**
     * Создает дату с указанным годом
     */
    private static String createDateWithYear(String dateStr, String year) {
        if (dateStr == null || year == null) return dateStr;

        String lower = dateStr.toLowerCase();

        if (containsYear(lower)) {
            return dateStr;
        }

        // 15мая -> 15мая2025г.
        if (lower.matches("\\d{1,2}[а-яё]+")) {
            return dateStr + year + "г.";
        }

        // 15 мая -> 15 мая 2025г.
        if (lower.matches("\\d{1,2}\\s+[а-яё]+")) {
            return dateStr + " " + year + "г.";
        }

        // май -> май 2025г.
        if (lower.matches("[а-яё]+")) {
            return dateStr + " " + year + "г.";
        }

        // 15.05 -> 15.05.2025
        if (lower.matches("\\d{1,2}\\.\\d{1,2}")) {
            return dateStr + "." + year;
        }

        // 15-05 -> 15-05-2025
        if (lower.matches("\\d{1,2}-\\d{1,2}")) {
            return dateStr + "-" + year;
        }

        return dateStr;
    }

    /**
     * Внутренний метод нормализации с возможностью указания года по умолчанию
     */
    private static String normalizeDateInternal(String dateStr, String defaultYear) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        String cleaned = cleanDateText(dateStr);
        log.debug("Внутренняя нормализация: '{}' (год по умолчанию: {})", cleaned, defaultYear);

        // 1. Уже нормализованный формат
        if (cleaned.matches("\\d{1,2}\\.\\d{1,2}\\.\\d{4}")) {
            return formatToDDMMYYYY(cleaned);
        }

        // 2. Формат с разделителями
        if (cleaned.matches("\\d{1,2}[-/]\\d{1,2}[-/]\\d{4}")) {
            return parseDashSeparatedDate(cleaned);
        }

        if (cleaned.matches("\\d{1,2}[-/]\\d{1,2}[-/]\\d{2}")) {
            return parseShortDashSeparatedDate(cleaned);
        }

        // 3. Русские текстовые даты с годом
        Matcher matcher = Pattern.compile("(\\d{1,2})([а-яА-ЯёЁ]+)(\\d{4})").matcher(cleaned);
        if (matcher.find()) {
            String day = String.format("%02d", Integer.parseInt(matcher.group(1)));
            String monthName = matcher.group(2).toLowerCase();
            String year = matcher.group(3);
            Integer month = MONTH_NUMBERS.get(monthName);
            if (month != null) {
                return day + "." + String.format("%02d", month) + "." + year;
            }
        }

        // 4. Русские текстовые даты без года - используем defaultYear
        matcher = Pattern.compile("(\\d{1,2})([а-яА-ЯёЁ]+)").matcher(cleaned);
        if (matcher.find()) {
            String day = String.format("%02d", Integer.parseInt(matcher.group(1)));
            String monthName = matcher.group(2).toLowerCase();
            Integer month = MONTH_NUMBERS.get(monthName);
            if (month != null) {
                String year = (defaultYear != null) ? defaultYear : String.valueOf(LocalDate.now().getYear());
                return day + "." + String.format("%02d", month) + "." + year;
            }
        }

        // 5. Русские даты с пробелами и годом
        matcher = Pattern.compile("(\\d{1,2})\\s+([а-яА-ЯёЁ]+)\\s+(\\d{4})").matcher(cleaned);
        if (matcher.find()) {
            String day = String.format("%02d", Integer.parseInt(matcher.group(1)));
            String monthName = matcher.group(2).toLowerCase();
            String year = matcher.group(3);
            Integer month = MONTH_NUMBERS.get(monthName);
            if (month != null) {
                return day + "." + String.format("%02d", month) + "." + year;
            }
        }

        // 6. Русские даты с пробелами без года
        matcher = Pattern.compile("(\\d{1,2})\\s+([а-яА-ЯёЁ]+)").matcher(cleaned);
        if (matcher.find()) {
            String day = String.format("%02d", Integer.parseInt(matcher.group(1)));
            String monthName = matcher.group(2).toLowerCase();
            Integer month = MONTH_NUMBERS.get(monthName);
            if (month != null) {
                String year = (defaultYear != null) ? defaultYear : String.valueOf(LocalDate.now().getYear());
                return day + "." + String.format("%02d", month) + "." + year;
            }
        }

        // 7. Английские даты
        String englishDate = parseEnglishDate(cleaned);
        if (englishDate != null) {
            return englishDate;
        }

        // 8. Только месяц и год
        matcher = Pattern.compile("([а-яА-ЯёЁ]+)\\s+(\\d{4})").matcher(cleaned);
        if (matcher.find()) {
            String monthName = matcher.group(1).toLowerCase();
            String year = matcher.group(2);
            Integer month = MONTH_NUMBERS.get(monthName);
            if (month != null) {
                return "01." + String.format("%02d", month) + "." + year;
            }
        }

        // 9. Месяц.год
        if (cleaned.matches("\\d{1,2}\\.\\d{4}")) {
            try {
                String[] parts = cleaned.split("\\.");
                String month = String.format("%02d", Integer.parseInt(parts[0]));
                String year = parts[1];
                return "01." + month + "." + year;
            } catch (Exception e) {
                // ignore
            }
        }

        return cleaned;
    }

    /**
     * Публичный метод нормализации (для обратной совместимости)
     */
    public static String normalizeDate(String dateStr) {
        return normalizeDateInternal(dateStr, null);
    }

    public static String normalizeDatePreferFile(String rawDate, String filePath) {
        String dateFromPath = extractExplicitDateFromFilePath(filePath);
        if (dateFromPath != null && isValidDate(dateFromPath)) {
            return dateFromPath;
        }
        return normalizeDateWithFileFallback(rawDate, filePath);
    }

    public static String calculateSchoolYear(String normalizedDate) {
        if (!isValidDate(normalizedDate)) {
            return null;
        }

        try {
            LocalDate date = LocalDate.parse(normalizedDate, TARGET_FORMATTER);
            int startYear = date.getMonthValue() >= 9 ? date.getYear() : date.getYear() - 1;
            return startYear + "/" + (startYear + 1);
        } catch (DateTimeParseException e) {
            log.debug("Не удалось вычислить учебный год для даты: {}", normalizedDate);
            return null;
        }
    }

    /**
     * Парсит английские даты
     */
    private static String parseEnglishDate(String dateStr) {
        dateStr = dateStr.replace(",", "").toLowerCase();
        String[] parts = dateStr.split("\\s+");

        if (parts.length >= 3) {
            try {
                String dayStr, monthStr, yearStr;

                if (ENGLISH_MONTHS.containsKey(parts[0])) {
                    monthStr = parts[0];
                    dayStr = parts[1];
                    yearStr = parts[2];
                } else {
                    dayStr = parts[0];
                    monthStr = parts[1];
                    yearStr = parts[2];
                }

                Integer month = ENGLISH_MONTHS.get(monthStr);
                if (month != null) {
                    String day = String.format("%02d", Integer.parseInt(dayStr));
                    return day + "." + String.format("%02d", month) + "." + yearStr;
                }
            } catch (Exception e) {
                log.debug("Ошибка парсинга английской даты: {}", dateStr);
            }
        }

        return null;
    }

    /**
     * Очищает текст даты от лишних слов
     */
    private static String cleanDateText(String text) {
        if (text == null) return "";

        String cleaned = text
                .split("(?i)\\s*(?:класс|class|дата|date)\\s*:")[0]
                .replaceAll("(?i)^\\s*(?:дата|date)\\s*:?\\s*", "")
                .replaceAll("(?i)\\bгода\\b", "")
                .replaceAll("[г\\.\\s]+$", "")
                .replace('–', '-')
                .replace('—', '-')
                .replaceAll("\\s+", " ")
                .trim();

        // Пример: "10-11 ноября 2025" -> берем первую дату диапазона: "10 ноября 2025"
        Matcher textRangeMatcher = Pattern.compile(
                "^(\\d{1,2})\\s*-\\s*\\d{1,2}\\s+([а-яА-ЯёЁ]+)\\s+(\\d{4})$"
        ).matcher(cleaned);
        if (textRangeMatcher.find()) {
            return textRangeMatcher.group(1) + " " +
                    textRangeMatcher.group(2) + " " +
                    textRangeMatcher.group(3);
        }

        // Пример: "10-11.11.2025" -> "10.11.2025"
        Matcher numericRangeMatcher = Pattern.compile(
                "^(\\d{1,2})\\s*-\\s*\\d{1,2}[\\./-](\\d{1,2})[\\./-](\\d{2,4})$"
        ).matcher(cleaned);
        if (numericRangeMatcher.find()) {
            String year = numericRangeMatcher.group(3);
            if (year.length() == 2) {
                year = "20" + year;
            }
            return numericRangeMatcher.group(1) + "." + numericRangeMatcher.group(2) + "." + year;
        }

        // Если в строке есть лишний текст, берём первую найденную дату
        Matcher explicitDateMatcher = Pattern.compile("(\\d{1,2}[\\./-]\\d{1,2}[\\./-]\\d{2,4})").matcher(cleaned);
        if (explicitDateMatcher.find()) {
            return explicitDateMatcher.group(1);
        }

        return cleaned;
    }

    /**
     * Проверяет, содержит ли строка год
     */
    private static boolean containsYear(String str) {
        return str != null && str.matches(".*\\d{4}.*");
    }

    /**
     * Форматирует дату в dd.MM.yyyy
     */
    private static String formatToDDMMYYYY(String date) {
        try {
            String[] parts = date.split("\\.");
            if (parts.length == 3) {
                String day = String.format("%02d", Integer.parseInt(parts[0]));
                String month = String.format("%02d", Integer.parseInt(parts[1]));
                return day + "." + month + "." + parts[2];
            }
        } catch (Exception e) {
            log.debug("Ошибка форматирования даты: {}", date);
        }
        return date;
    }

    /**
     * Парсит даты с разделителями
     */
    private static String parseDashSeparatedDate(String date) {
        try {
            String[] parts = date.split("[-/]");
            if (parts.length == 3) {
                String day = String.format("%02d", Integer.parseInt(parts[0]));
                String month = String.format("%02d", Integer.parseInt(parts[1]));
                return day + "." + month + "." + parts[2];
            }
        } catch (Exception e) {
            log.debug("Ошибка парсинга даты с разделителями: {}", date);
        }
        return date;
    }

    private static String parseShortDashSeparatedDate(String date) {
        try {
            String[] parts = date.split("[-/]");
            if (parts.length == 3) {
                String day = String.format("%02d", Integer.parseInt(parts[0]));
                String month = String.format("%02d", Integer.parseInt(parts[1]));
                String year = "20" + parts[2];
                return day + "." + month + "." + year;
            }
        } catch (Exception e) {
            log.debug("Ошибка парсинга короткой даты: {}", date);
        }
        return date;
    }

    /**
     * Проверяет валидность даты
     */
    public static boolean isValidDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return false;
        }

        try {
            LocalDate.parse(dateStr, TARGET_FORMATTER);
            return true;
        } catch (DateTimeParseException e) {
            log.debug("Неверная дата: '{}'", dateStr);
            return false;
        }
    }
}
