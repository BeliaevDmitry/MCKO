package org.school.MckoReport.MckoCompleks.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.school.MckoReport.MckoCompleks.model.FileCategory;
import org.school.MckoReport.MckoCompleks.service.FindFilesService;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.school.MckoReport.MckoCompleks.model.ArchiveEntry;
import org.springframework.stereotype.Service;

import static org.school.MckoReport.MckoCompleks.Config.AppConfig.OUTPUT_FILE_PATCH;
import static org.school.MckoReport.MckoCompleks.Config.AppConfig.SCHOOLS;

@Slf4j
@Service
public class FindFilesServiceImpl implements FindFilesService {

    /**
     * Возвращает все обычные файлы (не архивы) рекурсивно
     */
    @Override
    public List<Path> findRegularFiles(Path folderPath) {

        if (!Files.exists(folderPath) || !Files.isDirectory(folderPath)) {
            log.warn("Directory does not exist or is not accessible: {}", folderPath);
            return Collections.emptyList();
        }

        List<Path> result = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(folderPath)) {
            result = walk.filter(Files::isRegularFile)
                    .filter(path -> !isZipFile(path)) // исключаем архивы
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Error walking through directory: {}", folderPath, e);
        }

        return result;
    }

    /**
     * Возвращает информацию о файлах внутри ZIP архивов
     * Каждый ArchiveEntry содержит путь к архиву и путь к файлу внутри архива
     */
    @Override
    public List<ArchiveEntry> findFilesInArchives(Path directory) {
        List<ArchiveEntry> allEntries = new ArrayList<>();

        if (!Files.exists(directory)) {
            log.error("Директория не существует: {}", directory);
            return allEntries;
        }

        if (!Files.isDirectory(directory)) {
            log.error("Путь не является директорией: {}", directory);
            return allEntries;
        }

        try {
            // Находим все ZIP файлы в директории
            List<Path> zipFiles = findZipFiles(directory);
            log.info("Найдено ZIP архивов в {}: {}", directory, zipFiles.size());

            for (Path zipPath : zipFiles) {
                log.info("Обработка архива: {}", zipPath.getFileName());

                try {
                    // Основной метод с правильной кодировкой для кириллицы
                    List<ArchiveEntry> entries = processZipWithCyrillic(zipPath);

                    if (!entries.isEmpty()) {
                        allEntries.addAll(entries);
                        log.info("  Успешно обработано: {} файлов", entries.size());

                        // Для отладки выводим первые 3 файла
                        if (entries.size() > 0) {
                            log.info("  Примеры файлов в архиве:");
                            for (int i = 0; i < Math.min(3, entries.size()); i++) {
                                log.info("    • {}", entries.get(i).getEntryPath());
                            }
                        }
                    } else {
                        log.warn("  Архив пустой или не содержит файлов");
                    }

                } catch (Exception e) {
                    log.error("  Ошибка обработки архива: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Ошибка при поиске файлов в архивах: {}", e.getMessage(), e);
        }

        log.info("Всего найдено файлов в архивах: {}", allEntries.size());
        return allEntries;
    }

    /**
     * Основной метод обработки ZIP с кириллицей
     */
    private List<ArchiveEntry> processZipWithCyrillic(Path zipPath) throws IOException {
        List<ArchiveEntry> entries = new ArrayList<>();
        File file = zipPath.toFile();

        if (!file.exists() || file.length() == 0) {
            throw new IOException("Файл не существует или пустой");
        }

        // Пробуем разные кодировки для кириллицы в порядке приоритета
        Charset[] charsets = {
                Charset.forName("CP866"),      // DOS Russian - самый вероятный для ZIP с кириллицей
                Charset.forName("Windows-1251"), // Windows Cyrillic
                StandardCharsets.UTF_8,       // UTF-8
                Charset.forName("KOI8-R"),      // KOI8-R
                Charset.defaultCharset()        // Системная по умолчанию
        };

        Exception lastError = null;

        for (Charset charset : charsets) {
            try {
                entries = readZipWithCharset(zipPath, charset);
                if (!entries.isEmpty()) {
                    log.debug("  Успешная кодировка: {}", charset.name());
                    return entries;
                }
            } catch (Exception e) {
                lastError = e;
                log.debug("  Кодировка {} не сработала: {}", charset.name(), e.getMessage());
            }
        }

        if (lastError != null) {
            throw new IOException("Не удалось прочитать архив ни с одной кодировкой. Последняя ошибка: " +
                    lastError.getMessage(), lastError);
        }

        return entries;
    }

    /**
     * Чтение ZIP с указанной кодировкой (исправленный метод)
     */
    private List<ArchiveEntry> readZipWithCharset(Path zipPath, Charset charset) throws IOException {
        List<ArchiveEntry> entries = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(zipPath.toFile());
             ZipInputStream zis = new ZipInputStream(fis, charset)) {

            byte[] buffer = new byte[1024];
            ZipEntry zipEntry;

            while ((zipEntry = zis.getNextEntry()) != null) {
                if (!zipEntry.isDirectory()) {
                    String entryName = zipEntry.getName();

                    // Проверяем, что имя корректное
                    if (isValidEntryName(entryName)) {
                        ArchiveEntry archiveEntry = new ArchiveEntry(
                                zipPath,
                                entryName,
                                zipEntry.getSize()
                        );
                        entries.add(archiveEntry);
                    } else {
                        log.debug("  Пропущен некорректный файл: {}", entryName);
                    }

                    // Читаем содержимое чтобы продвинуть поток
                    while (zis.read(buffer) > 0) {
                        // просто читаем, содержимое не нужно
                    }
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            // Если возникает ошибка с текущей кодировкой, пробуем исправить имена
            return tryFixCyrillicNames(zipPath, charset);
        }

        return entries;
    }

    /**
     * Пытается исправить имена файлов с кириллицей
     */
    private List<ArchiveEntry> tryFixCyrillicNames(Path zipPath, Charset originalCharset) throws IOException {
        List<ArchiveEntry> entries = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(zipPath.toFile());
             ZipInputStream zis = new ZipInputStream(fis, originalCharset)) {

            byte[] buffer = new byte[1024];
            ZipEntry zipEntry;

            while ((zipEntry = zis.getNextEntry()) != null) {
                if (!zipEntry.isDirectory()) {
                    String entryName = zipEntry.getName();

                    // Пробуем исправить кодировку если есть кракозябры
                    String fixedName = fixCyrillicEncoding(entryName, originalCharset);

                    if (isValidEntryName(fixedName)) {
                        ArchiveEntry archiveEntry = new ArchiveEntry(
                                zipPath,
                                fixedName,
                                zipEntry.getSize()
                        );
                        entries.add(archiveEntry);
                    }

                    // Читаем содержимое
                    while (zis.read(buffer) > 0) {
                        // просто читаем
                    }
                }
                zis.closeEntry();
            }
        }

        return entries;
    }

    /**
     * Исправляет кодировку кириллицы
     */
    private String fixCyrillicEncoding(String name, Charset originalCharset) {
        try {
            // Если имя содержит кракозябры (�), пробуем разные кодировки
            if (name.contains("�") || name.matches(".*[À-ßà-ÿ].*")) {
                // Пробуем конвертировать через разные кодировки
                byte[] bytes;

                if (originalCharset.name().equals("CP866")) {
                    bytes = name.getBytes(StandardCharsets.ISO_8859_1);
                    return new String(bytes, "CP866");
                } else if (originalCharset.name().equals("Windows-1251")) {
                    bytes = name.getBytes(StandardCharsets.ISO_8859_1);
                    return new String(bytes, "Windows-1251");
                }
            }
        } catch (Exception e) {
            log.debug("Не удалось исправить кодировку для: {}", name);
        }

        return name;
    }

    /**
     * Проверяет валидность имени файла
     */
    private boolean isValidEntryName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        // Проверяем на наличие русских букв или PDF расширения
        boolean hasCyrillic = name.matches(".*[А-Яа-яЁё].*");
        boolean isPdf = name.toLowerCase().endsWith(".pdf");
        boolean hasNormalChars = name.matches(".*[a-zA-Z0-9._\\-].*");

        return (hasCyrillic || isPdf) && hasNormalChars;
    }


    /**
     * Находит все ZIP файлы в директории
     */
    private List<Path> findZipFiles(Path directory) throws IOException {
        List<Path> zipFiles = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    String fileName = path.getFileName().toString().toLowerCase();
                    if (fileName.endsWith(".zip")) {
                        zipFiles.add(path);
                    }
                }
            }
        }

        return zipFiles;
    }


    @Override
    public boolean moveToSubjectFolder(List<Path> listPatchSuccessful) {
        if (listPatchSuccessful == null || listPatchSuccessful.isEmpty()) {
            log.info("Нет файлов для перемещения");
            return true;
        }

        boolean allMoved = true;
        List<Path> failedMoves = new ArrayList<>();

        for (Path sourcePath : listPatchSuccessful) {
            try {
                // Извлекаем информацию для формирования пути назначения
                String subject = extractSubjectFromPath(sourcePath);
                String school = extractSchoolFromPath(sourcePath);

                if (subject == null || school == null) {
                    log.warn("Не удалось определить предмет или школу для файла: {}", sourcePath);
                    failedMoves.add(sourcePath);
                    allMoved = false;
                    continue;
                }
                if (school.equals("7")) {
                    school = SCHOOLS.get(0);
                }
                // Формируем путь назначения
                String destinationPath = OUTPUT_FILE_PATCH
                        .replace("{школа}", school)
                        .replace("{предмет}", subject);

                Path destDir = Paths.get(destinationPath);

                // Создаем папку назначения если не существует
                if (!Files.exists(destDir)) {
                    Files.createDirectories(destDir);
                    log.info("Создана папка: {}", destDir);
                }

                // Формируем полный путь к файлу назначения
                Path destFile = destDir.resolve(sourcePath.getFileName());

                // Перемещаем файл
                Files.move(sourcePath, destFile, StandardCopyOption.REPLACE_EXISTING);
                log.info("Файл перемещен: {} -> {}", sourcePath, destFile);

            } catch (IOException e) {
                log.error("Ошибка при перемещении файла {}: {}", sourcePath, e.getMessage());
                failedMoves.add(sourcePath);
                allMoved = false;
            }
        }

        if (!failedMoves.isEmpty()) {
            log.warn("Не удалось переместить {} файлов: {}", failedMoves.size(), failedMoves);
        }

        return allMoved;
    }

    /**
     * Извлекает номер школы из пути файла
     */
    private String extractSchoolFromPath(Path path) {
        // Предполагаем, что путь содержит номер школы
        // Например: C:\Users\dimah\Yandex.Disk\ГБОУ №7\...

        String pathStr = path.toString();

        // Ищем паттерн "ГБОУ №" или "Школа №"
        String[] patterns = {
                "ГБОУ №\\s*(\\d+)",
                "Школа №\\s*(\\d+)",
                "№\\s*(\\d+)\\s*[Шш]кола",
                "school(\\d+)",
                "школа(\\d+)"
        };

        java.util.regex.Pattern pattern;
        java.util.regex.Matcher matcher;

        for (String patternStr : patterns) {
            pattern = java.util.regex.Pattern.compile(patternStr,
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            matcher = pattern.matcher(pathStr);
            if (matcher.find()) {
                return matcher.group(1); // возвращаем номер школы
            }
        }

        // Если не нашли, пробуем из имени файла (первые цифры)
        String fileName = path.getFileName().toString();
        pattern = java.util.regex.Pattern.compile("^(\\d{3,})_");
        matcher = pattern.matcher(fileName);
        if (matcher.find()) {
            return matcher.group(1);
        }

        log.warn("Не удалось определить школу из пути: {}", path);
        return null;
    }

    /**
     * Проверяет, является ли файл ZIP архивом
     */
    private boolean isZipFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".zip");
    }


    /**
     * Извлекает название предмета из пути файла
     */
    private String extractSubjectFromPath(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();

        // Пытаемся определить предмет по имени файла
        if (fileName.contains("фг") || fileName.contains("функциональн")) {
            return "Функциональная грамотность";
        } else if (fileName.contains("мат")) {
            return "Математика";
        } else if (fileName.contains("русск") || fileName.contains("ря")) {
            return "Русский язык";
        } else if (fileName.contains("англ")) {
            return "Английский язык";
        } else if (fileName.contains("физик")) {
            return "Физика";
        } else if (fileName.contains("хими")) {
            return "Химия";
        } else if (fileName.contains("биолог")) {
            return "Биология";
        } else if (fileName.contains("географ")) {
            return "География";
        } else if (fileName.contains("истори")) {
            return "История";
        } else if (fileName.contains("обществ")) {
            return "Обществознание";
        } else if (fileName.contains("информат") || fileName.contains("инф")) {
            return "Информатика";
        }

        // Если не нашли в имени файла, пробуем из родительской папки
        Path parent = path.getParent();
        if (parent != null) {
            String parentName = parent.getFileName().toString().toLowerCase();
            if (parentName.contains("фкг")) return "Функциональная грамотность";
            if (parentName.contains("мат")) return "Математика";
            // ... и т.д.
        }

        return "Другие предметы"; // fallback
    }

    /**
     * Анализирует имена файлов, возвращает необходимые адреса по типам:
     * 1. FG_PDF - файлы с "ФГ" в названии и PDF формат
     * 2. EXCEL - Excel файлы (.xlsx, .xls)
     * 3. OTHER - все остальные файлы
     *
     * @param pathsEntries список записей для обработки
     * @return карта результатов обработки по типам файлов
     */
    @Override
    public Map<String, List<Path>> dispatchProcessing(List<Path> pathsEntries) {
        Map<String, List<Path>> result = new HashMap<>();
        result.put(FileCategory.FG_PDF_RESULTS.name(), new ArrayList<>());   // PDF с "ФГ"
        result.put(FileCategory.EXCEL_RESULTS.name(), new ArrayList<>());    // Excel файлы
        result.put(FileCategory.OTHER.name(), new ArrayList<>());    // Все остальное

        if (pathsEntries == null || pathsEntries.isEmpty()) {
            log.info("Список путей для классификации пуст");
            return result;
        }

        // паттерн: минимум 4 цифры после _pm
        Pattern resultPattern = Pattern.compile(".*_pm\\d{4,}.*", Pattern.CASE_INSENSITIVE);

        log.info("Начало классификации {} файлов", pathsEntries.size());

        int fgPdfCount = 0;
        int excelCount = 0;
        int otherCount = 0;

        for (Path path : pathsEntries) {
            if (path == null) {
                continue;
            }

            String fileName = path.getFileName().toString();
            String lowerFileName = fileName.toLowerCase();

            try {
                if (!resultPattern.matcher(fileName).matches()) {
                    continue;
                }

                // 1. Проверяем существование файла
                if (!Files.exists(path)) {
                    log.warn("Файл не существует: {}", path);
                    result.get("OTHER").add(path);
                    otherCount++;
                    continue;
                }

                // 2. Классификация
                if (isFgPdfFile(lowerFileName, fileName)) {
                    result.get(FileCategory.FG_PDF_RESULTS.name()).add(path);
                    fgPdfCount++;
                    log.debug("FG_PDF: {}", fileName);
                } else if (isExcelFile(lowerFileName)) {
                    result.get(FileCategory.EXCEL_RESULTS.name()).add(path);
                    excelCount++;
                    log.debug("EXCEL: {}", fileName);
                } else {
                    result.get(FileCategory.OTHER.name()).add(path);
                    otherCount++;
                    log.debug("OTHER: {}", fileName);
                }

            } catch (Exception e) {
                log.error("Ошибка при обработке файла {}: {}", fileName, e.getMessage());
                result.get("OTHER").add(path);
                otherCount++;
            }
        }

        log.info("Классификация завершена:");
        log.info("  FG_PDF файлов: {}", fgPdfCount);
        log.info("  EXCEL файлов: {}", excelCount);
        log.info("  OTHER файлов: {}", otherCount);

        // Подробное логирование для категории OTHER
        logOtherFilesDetails(result.get("OTHER"));

        return result;
    }

    /**
     * Проверяет, является ли файл PDF с "ФГ" в названии
     */
    private boolean isFgPdfFile(String lowerFileName, String originalFileName) {
        // Проверяем расширение PDF
        if (!lowerFileName.endsWith(".pdf")) {
            return false;
        }

        // Проверяем наличие "ФГ" или "ФКГ" в любом регистре
        boolean containsFg = lowerFileName.contains("фг") ||
                originalFileName.contains("ФГ") ||
                lowerFileName.contains("фкг") ||
                originalFileName.contains("ФКГ");

        // Также проверяем полное название "функциональная грамотность"
        boolean containsFullName = lowerFileName.contains("функциональн");

        return containsFg || containsFullName;
    }

    /**
     * Проверяет, является ли файл Excel
     */
    private boolean isExcelFile(String lowerFileName) {
        return lowerFileName.endsWith(".xlsx") ||
                lowerFileName.endsWith(".xls");
    }

    /**
     * Подробное логирование файлов в категории OTHER
     */
    private void logOtherFilesDetails(List<Path> otherFiles) {
        if (otherFiles.isEmpty()) {
            log.info("В категорию OTHER не попало ни одного файла");
            return;
        }

        log.info("=== ПОДРОБНЫЙ ОТЧЕТ ПО КАТЕГОРИИ OTHER ===");
        log.info("Всего файлов в OTHER: {}", otherFiles.size());

        // Группируем по расширениям
        Map<String, List<Path>> byExtension = new HashMap<>();

        for (Path path : otherFiles) {
            String fileName = path.getFileName().toString();
            String extension = getFileExtension(fileName).toLowerCase();

            byExtension.computeIfAbsent(extension, k -> new ArrayList<>())
                    .add(path);
        }

        // Выводим статистику по расширениям
        log.info("Распределение по расширениям:");
        byExtension.forEach((ext, files) -> {
            log.info("  .{} : {} файлов", ext.isEmpty() ? "без расширения" : ext, files.size());

            // Для PDF файлов (которые не ФГ) выводим примеры
            if ("pdf".equals(ext) && !files.isEmpty()) {
                log.info("    Примеры PDF (не ФГ):");
                for (int i = 0; i < Math.min(3, files.size()); i++) {
                    log.info("      • {}", files.get(i).getFileName());
                }
            }
        });

        // Выводим все файлы OTHER если их немного
        if (otherFiles.size() <= 20) {
            log.info("Список всех файлов в OTHER:");
            for (Path path : otherFiles) {
                String fileName = path.getFileName().toString();
                String extension = getFileExtension(fileName);
                log.info("  • {} [.{}]", fileName, extension.isEmpty() ? "?" : extension);
            }
        } else {
            // Если много файлов - только первые 10
            log.info("Первые 10 файлов в OTHER:");
            for (int i = 0; i < Math.min(10, otherFiles.size()); i++) {
                String fileName = otherFiles.get(i).getFileName().toString();
                String extension = getFileExtension(fileName);
                log.info("  • {} [.{}]", fileName, extension.isEmpty() ? "?" : extension);
            }
        }

        log.info("========================================");
    }

    /**
     * Извлекает расширение файла
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1) : "";
    }

    /**
     * Дополнительная проверка: действительно ли файл PDF (по сигнатуре)
     */
    private boolean isActuallyPdf(Path path) {
        try {
            if (Files.size(path) < 4) {
                return false;
            }

            try (InputStream is = Files.newInputStream(path)) {
                byte[] header = new byte[4];
                if (is.read(header) == 4) {
                    // PDF сигнатура: %PDF
                    return header[0] == 0x25 && // %
                            header[1] == 0x50 && // P
                            header[2] == 0x44 && // D
                            header[3] == 0x46;   // F
                }
            }
        } catch (Exception e) {
            log.debug("Не удалось проверить сигнатуру файла: {}", e.getMessage());
        }
        return false;
    }

    public Map<String, List<ArchiveEntry>> dispatchArchiveProcessing(
            List<ArchiveEntry> archiveEntries) {

        Map<String, List<ArchiveEntry>> result = new HashMap<>();
        result.put("CODE_LISTS", new ArrayList<>());
        result.put("RESULTS", new ArrayList<>());

        // паттерн: минимум 4 цифры после _pm
        Pattern resultPattern = Pattern.compile(".*_pm\\d{4,}.*", Pattern.CASE_INSENSITIVE);

        for (ArchiveEntry entry : archiveEntries) {
            String fileName = entry.getEntryPath();
            String lowerFileName = fileName.toLowerCase();

            // 1. Списки кодов (PDF)
            if (lowerFileName.endsWith(".pdf") &&
                    (lowerFileName.contains("список кодов диагностик") ||
                            lowerFileName.contains("список кодов"))) {
                result.get("CODE_LISTS").add(entry);
            }
            // 2. Файлы с результатами (_pm + минимум 4 цифры)
            else if (resultPattern.matcher(fileName).matches()) {
                result.get("RESULTS").add(entry);
            }
            // 3. Остальные пропускаем
        }

        return result;
    }
}