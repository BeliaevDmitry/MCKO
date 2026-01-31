package org.school.MckoReport.MckoCompleks.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
public class FileDateUtils {

    /**
     * Получает дату создания файла
     *
     * @param filePath путь к файлу
     * @return дата создания или null если не удалось получить
     */
    public static LocalDateTime getCreationDate(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            log.warn("Путь к файлу пустой");
            return null;
        }

        try {
            Path path = Paths.get(filePath);

            if (!Files.exists(path)) {
                log.warn("Файл не существует: {}", filePath);
                return null;
            }

            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            FileTime creationTime = attrs.creationTime();

            return LocalDateTime.ofInstant(
                    creationTime.toInstant(),
                    ZoneId.systemDefault()
            );

        } catch (IOException e) {
            log.error("Ошибка при чтении атрибутов файла {}: {}", filePath, e.getMessage());
            return null;
        } catch (SecurityException e) {
            log.error("Нет прав доступа к файлу {}: {}", filePath, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Неожиданная ошибка при получении даты создания файла {}: {}",
                    filePath, e.getMessage());
            return null;
        }
    }

    /**
     * Получает дату последнего изменения файла
     */
    public static LocalDateTime getModificationDate(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return null;
        }

        try {
            Path path = Paths.get(filePath);

            if (!Files.exists(path)) {
                log.warn("Файл не существует: {}", filePath);
                return null;
            }

            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            FileTime modifiedTime = attrs.lastModifiedTime();

            return LocalDateTime.ofInstant(
                    modifiedTime.toInstant(),
                    ZoneId.systemDefault()
            );

        } catch (Exception e) {
            log.error("Ошибка при получении даты изменения файла {}: {}", filePath, e.getMessage());
            return null;
        }
    }

    /**
     * Получает дату создания в строковом формате
     */
    public static String getCreationDateString(String filePath, String pattern) {
        LocalDateTime date = getCreationDate(filePath);
        if (date != null) {
            try {
                return date.format(DateTimeFormatter.ofPattern(pattern));
            } catch (Exception e) {
                log.warn("Ошибка форматирования даты по шаблону '{}': {}", pattern, e.getMessage());
            }
        }
        return null;
    }

    /**
     * Получает дату создания в формате dd.MM.yyyy
     */
    public static String getCreationDateFormatted(String filePath) {
        return getCreationDateString(filePath, "dd.MM.yyyy");
    }

    /**
     * Получает дату создания в формате dd.MM.yyyy HH:mm:ss
     */
    public static String getCreationDateWithTime(String filePath) {
        return getCreationDateString(filePath, "dd.MM.yyyy HH:mm:ss");
    }

    /**
     * Получает год создания файла
     */
    public static Integer getCreationYear(String filePath) {
        LocalDateTime date = getCreationDate(filePath);
        return date != null ? date.getYear() : null;
    }

    /**
     * Получает год создания файла в виде строки
     */
    public static String getCreationYearString(String filePath) {
        Integer year = getCreationYear(filePath);
        return year != null ? String.valueOf(year) : null;
    }

    /**
     * Получает месяц создания файла (1-12)
     */
    public static Integer getCreationMonth(String filePath) {
        LocalDateTime date = getCreationDate(filePath);
        return date != null ? date.getMonthValue() : null;
    }

    /**
     * Получает день создания файла (1-31)
     */
    public static Integer getCreationDay(String filePath) {
        LocalDateTime date = getCreationDate(filePath);
        return date != null ? date.getDayOfMonth() : null;
    }

    /**
     * Проверяет, можно ли получить дату создания файла
     */
    public static boolean canGetCreationDate(String filePath) {
        return getCreationDate(filePath) != null;
    }

    /**
     * Альтернативный метод: получает дату создания или последнего изменения
     * (на некоторых системах creationTime может быть равен modificationTime)
     */
    public static LocalDateTime getFileDate(String filePath) {
        LocalDateTime creationDate = getCreationDate(filePath);
        if (creationDate != null) {
            return creationDate;
        }

        // Fallback на дату изменения
        return getModificationDate(filePath);
    }

    /**
     * Получает timestamp создания файла в миллисекундах
     */
    public static Long getCreationTimestamp(String filePath) {
        LocalDateTime date = getCreationDate(filePath);
        if (date != null) {
            return date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        return null;
    }

    /**
     * Сравнивает даты создания двух файлов
     */
    public static int compareByCreationDate(String filePath1, String filePath2) {
        LocalDateTime date1 = getCreationDate(filePath1);
        LocalDateTime date2 = getCreationDate(filePath2);

        if (date1 == null && date2 == null) return 0;
        if (date1 == null) return -1;
        if (date2 == null) return 1;

        return date1.compareTo(date2);
    }

    /**
     * Получает возраст файла в днях
     */
    public static Long getFileAgeInDays(String filePath) {
        LocalDateTime creationDate = getCreationDate(filePath);
        if (creationDate == null) {
            return null;
        }

        Instant fileInstant = creationDate.atZone(ZoneId.systemDefault()).toInstant();
        Instant nowInstant = Instant.now();

        long diffMillis = nowInstant.toEpochMilli() - fileInstant.toEpochMilli();
        return diffMillis / (1000 * 60 * 60 * 24);
    }
}