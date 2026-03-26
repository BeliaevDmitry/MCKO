package org.school.MckoReport.MckoCompleks.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.school.MckoReport.MckoCompleks.expextion.ProcessingException;
import org.school.MckoReport.MckoCompleks.model.ArchiveEntry;
import org.school.MckoReport.MckoCompleks.model.ListStudentData;
import org.school.MckoReport.MckoCompleks.service.ListProcessingService;
import org.school.MckoReport.MckoCompleks.util.DateNormalizerUtil;
import org.springframework.stereotype.Service;


import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ListProcessingServiceImpl implements ListProcessingService {

        /**
     * Метод принимает адреса файлов, обрабатывает только "Списки участников"
     * возвращает списки студентов и их коды для сопоставления
     *
     * @throws ProcessingException если файл не может быть обработан
     */
    @Override
    public List<ListStudentData> extractStudentsCodFromArchive(ArchiveEntry archiveEntry) {
        log.info("Начало обработки списка участников: {}", archiveEntry.getEntryPath());

        try {
            log.debug("Обработка файла: {}", archiveEntry.getEntryPath());

            // Получаем данные из файла в архиве
            byte[] fileContent = extractFileFromArchive(archiveEntry);
            if (fileContent == null || fileContent.length == 0) {
                throw new ProcessingException("Файл пуст или не найден: " + archiveEntry.getEntryPath());
            }

            // Обрабатываем PDF файл
            List<ListStudentData> students = processPdfFile(
                    fileContent,
                    archiveEntry.getEntryPath()
            );

            log.info("Обработка завершена. Найдено студентов: {}", students.size());
            return students;

        } catch (ProcessingException e) {
            log.error("Ошибка обработки файла {}: {}", archiveEntry.getEntryPath(), e.getMessage());
            throw e; // Пробрасываем дальше для @Transactional отката
        } catch (Exception e) {
            log.error("Неожиданная ошибка при обработке файла {}: {}",
                    archiveEntry.getEntryPath(), e.getMessage(), e);
            throw new ProcessingException("Ошибка обработки файла: " + archiveEntry.getEntryPath(), e);
        }
    }

    /**
     * Извлекает содержимое файла из архива
     */
    private byte[] extractFileFromArchive(ArchiveEntry archiveEntry) throws IOException {
        Path archivePath = archiveEntry.getArchivePath();
        String entryPath = archiveEntry.getEntryPath();

        log.debug("Извлечение файла из архива: {} -> {}",
                archivePath.getFileName(), entryPath);

        // Используем кодировку IBM866 (CP866), которая сработала при чтении списка файлов
        try (FileInputStream fis = new FileInputStream(archivePath.toFile());
             ZipInputStream zis = new ZipInputStream(fis, Charset.forName("IBM866"))) {

            ZipEntry zipEntry;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];

            while ((zipEntry = zis.getNextEntry()) != null) {
                String currentEntryName = zipEntry.getName();

                // Сравниваем имена файлов (ищем нужный файл)
                if (entryPath.equals(currentEntryName)) {
                    // Нашли нужный файл
                    int length;
                    while ((length = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, length);
                    }

                    byte[] content = baos.toByteArray();
                    zis.closeEntry();
                    baos.close();

                    if (content.length == 0) {
                        log.warn("Файл {} извлечен, но пустой", entryPath);
                    }

                    log.debug("Успешно извлечено: {} байт", content.length);
                    return content;
                }
                zis.closeEntry();
            }

            // Если не нашли по точному совпадению, ищем с учетом возможных различий
            return searchFileInArchive(archivePath, entryPath);

        } catch (Exception e) {
            log.error("Ошибка извлечения файла: {}", e.getMessage());
            throw new IOException("Не удалось извлечь файл " + entryPath +
                    " из архива: " + e.getMessage(), e);
        }
    }

    /**
     * Поиск файла в архиве с разными вариантами сравнения
     */
    private byte[] searchFileInArchive(Path archivePath, String targetEntryPath) throws IOException {
        // Пробуем разные кодировки
        Charset[] charsets = {
                Charset.forName("IBM866"),      // CP866 - DOS Russian
                Charset.forName("Windows-1251"), // Windows Cyrillic
                StandardCharsets.UTF_8,
                Charset.defaultCharset()
        };

        for (Charset charset : charsets) {
            try (FileInputStream fis = new FileInputStream(archivePath.toFile());
                 ZipInputStream zis = new ZipInputStream(fis, charset)) {

                ZipEntry zipEntry;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];

                while ((zipEntry = zis.getNextEntry()) != null) {
                    String currentEntryName = zipEntry.getName();

                    // Разные способы сравнения
                    if (isMatchingEntry(targetEntryPath, currentEntryName)) {
                        // Извлекаем файл
                        int length;
                        while ((length = zis.read(buffer)) > 0) {
                            baos.write(buffer, 0, length);
                        }

                        byte[] content = baos.toByteArray();
                        zis.closeEntry();
                        baos.close();

                        log.info("Найден файл с кодировкой {}: {} -> {}",
                                charset.name(), currentEntryName, targetEntryPath);

                        return content;
                    }
                    zis.closeEntry();
                }

            } catch (Exception e) {
                log.debug("Поиск с кодировкой {} не удался: {}", charset.name(), e.getMessage());
            }
        }

        throw new IOException("Файл " + targetEntryPath + " не найден в архиве");
    }

    /**
     * Сравнивает имена файлов с учетом возможных различий
     */
    private boolean isMatchingEntry(String targetPath, String currentPath) {
        // 1. Точное совпадение
        if (targetPath.equals(currentPath)) {
            return true;
        }

        // 2. Без учета регистра
        if (targetPath.equalsIgnoreCase(currentPath)) {
            return true;
        }

        // 3. Сравниваем только имена файлов (без пути)
        String targetFileName = getFileName(targetPath);
        String currentFileName = getFileName(currentPath);

        if (targetFileName.equalsIgnoreCase(currentFileName)) {
            return true;
        }

        // 4. Сравниваем нормализованные пути
        String normalizedTarget = normalizePath(targetPath);
        String normalizedCurrent = normalizePath(currentPath);

        return normalizedTarget.equals(normalizedCurrent);
    }

    /**
     * Извлекает имя файла из пути
     */
    private String getFileName(String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    /**
     * Нормализует путь (убирает лишние разделители, приводит к единому формату)
     */
    private String normalizePath(String path) {
        return path.replace('\\', '/')
                .replaceAll("/+", "/")
                .toLowerCase();
    }

    /**
     * Обрабатывает PDF файл и извлекает данные студентов
     */
    private List<ListStudentData> processPdfFile(byte[] pdfContent, String fileName) throws IOException {
        List<ListStudentData> students = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfContent)) {

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            // Извлекаем метаданные из имени файла
            String date = extractDateFromFileName(fileName);
            log.debug("Извлекаем метаданные из имени файла String date {}" +
                    "fileName {}", date, fileName);
            date =DateNormalizerUtil.normalizeDateWithFileFallback(date,fileName);
            log.debug("Извлекаем метаданные из имени файла String date " +
                    "после DateNormalizerUtil {}", date);


            // Извлекаем все метаданные
            Map<String, String> metadata = extractMetadataFromText(text);
            String school = metadata.getOrDefault("school", "");
            String className = metadata.getOrDefault("className", "");
            String subject = metadata.getOrDefault("subject", "");


            List<ListStudentData> rawStudents = extractStudentsFromText(text);

            for (ListStudentData rawStudent : rawStudents) {
                ListStudentData student = ListStudentData.builder()
                        .nameFIO(rawStudent.getNameFIO())
                        .code(rawStudent.getCode())
                        .studentNumber(rawStudent.getStudentNumber())
                        .className(className)
                        .subject(subject)
                        .date(date)
                        .school(school)
                        .schoolYear(DateNormalizerUtil.calculateSchoolYear(date))
                        .build();

                students.add(student);
            }
        }

        return students;
    }

    /**
     * Извлекает данные по фиксированному порядку строк
     */
    private Map<String, String> extractMetadataFromText(String text) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("school", "");
        metadata.put("className", "");
        metadata.put("subject", "");

        String[] lines = text.split("\n");
        List<String> cleanLines = new ArrayList<>();

        // Убираем пустые строки и лишние пробелы
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                cleanLines.add(trimmed);
            }
        }

        // Ищем школу (первое вхождение "ГБОУ Школа №")
        for (int i = 0; i < cleanLines.size(); i++) {
            String line = cleanLines.get(i);

            if (line.contains("ГБОУ Школа № ")) {
                metadata.put("school", line);

                // Школа найдена, теперь ищем класс
                for (int j = i + 1; j < Math.min(i + 5, cleanLines.size()); j++) {
                    String nextLine = cleanLines.get(j);

                    // Пропускаем "Оценка качества образования"
                    if (nextLine.contains("Оценка качества образования")) {
                        continue;
                    }

                    // Ищем строку с классом
                    if (nextLine.contains("Класс:")) {
                        String className = nextLine.replace("Класс:", "").trim();
                        metadata.put("className", normalizeClass(className));

                        // Предмет должен быть следующей непустой строкой после класса
                        if (j + 1 < cleanLines.size()) {
                            String subjectLine = cleanLines.get(j + 1);
                            // Проверяем, что это не заголовок таблицы
                            if (!subjectLine.contains("ФИО") &&
                                    !subjectLine.contains("Код")) {
                                metadata.put("subject", subjectLine);
                            }
                        }
                        break;
                    }
                }
                break;
            }
        }

        return metadata;
    }

    private String extractDateFromFileName(String fileName) {
        // Убираем расширение .pdf
        fileName = fileName.replace(".pdf", "").replace(".PDF", "");

        String[] parts = fileName.split("_");

        for (String part : parts) {
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

            if (part.matches("\\d{1,2}[-–—]\\d{1,2}.*")) {
                return part;
            }
        }

        return "дата не определена";
    }


    private String normalizeClass(String className) {
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

        if (!normalized.contains("-") && normalized.matches(".*\\d[А-Яа-яЁё].*")) {
            normalized = normalized.replaceAll("([\\d])([А-Яа-яЁё])", "$1-$2");
        }

        return normalized.toUpperCase();
    }

    private List<ListStudentData> extractStudentsFromText(String text) {
        List<ListStudentData> students = new ArrayList<>();
        String[] lines = text.split("\n");

        boolean inStudentSection = false;
        int studentCounter = 0; // счётчик студентов

        for (String s : lines) {
            String line = s.trim();

            if (line.contains("ФИО обучающегося") ||
                    line.contains("ФИО участника") ||
                    line.contains("ФИО учащегося") ||
                    (line.contains("ФИО") && line.contains("Код"))) {
                inStudentSection = true;
                continue;
            }

            if (inStudentSection) {
                if (line.isEmpty() ||
                        line.equals("Код") ||
                        line.equals("участника") ||
                        line.equals("обучающегося") ||
                        line.equals("Код участника") ||
                        line.equals("Номер учащегося") ||
                        line.contains("ФИО") && line.contains("Код")) {
                    continue;
                }

                Pattern codePattern = Pattern.compile("(\\d{4}-\\d{4})$");
                Matcher matcher = codePattern.matcher(line);

                if (matcher.find()) {
                    String code = matcher.group(1);
                    String name = line.substring(0, matcher.start()).trim();

                    if (!name.isEmpty() &&
                            !name.equals("участника") &&
                            !name.equals("обучающегося") &&
                            !name.toLowerCase().contains("фио") &&
                            !name.toLowerCase().contains("код") &&
                            name.contains(" ") &&
                            name.matches(".*[А-ЯЁ][а-яё]+.*[А-ЯЁ][а-яё]+.*")) {

                        studentCounter++;
                        ListStudentData student = new ListStudentData();
                        student.setNameFIO(name);
                        student.setCode(code);
                        students.add(student);
                        student.setStudentNumber(studentCounter);
                    }
                }
            }
        }
        return students;
    }
}
