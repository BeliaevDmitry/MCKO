package org.school.MckoReport.MckoCompleks.service.orchestration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.school.MckoReport.MckoCompleks.Config.AppConfig;
import org.school.MckoReport.MckoCompleks.dto.CombinedResultData;
import org.school.MckoReport.MckoCompleks.expextion.ProcessingException;
import org.school.MckoReport.MckoCompleks.model.*;
import org.school.MckoReport.MckoCompleks.repository.ListStudentDataRepository;
import org.school.MckoReport.MckoCompleks.repository.OtherDiagnosticDataRepository;
import org.school.MckoReport.MckoCompleks.repository.StudentResultDataRepository;
import org.school.MckoReport.MckoCompleks.repository.StudentResultFGDataRepository;
import org.school.MckoReport.MckoCompleks.service.file.FindFilesService;
import org.school.MckoReport.MckoCompleks.service.parser.ListProcessingService;
import org.school.MckoReport.MckoCompleks.service.parser.OtherDiagnosticParserService;
import org.school.MckoReport.MckoCompleks.service.parser.ResultFGProcessorService;
import org.school.MckoReport.MckoCompleks.service.parser.ResultProcessorService;
import org.school.MckoReport.MckoCompleks.service.report.DataCombinationService;
import org.school.MckoReport.MckoCompleks.service.report.ExcelExportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class GeneralService {

    private final FindFilesService findFilesService;
    private final ListProcessingService listProcessingService;
    private final ListStudentDataRepository listStudentDataRepository;
    private final StudentResultDataRepository studentResultDataRepository;
    private final StudentResultFGDataRepository studentResultFGDataRepository;
    private final ResultFGProcessorService resultFGProcessorService;
    private final ResultProcessorService resultProcessorService;
    private final DataCombinationService dataCombinationService;
    private final ExcelExportService excelExportService;
    private final OtherDiagnosticParserService otherDiagnosticParserService;
    private final OtherDiagnosticDataRepository otherDiagnosticDataRepository;

    public void processListCod() {
        log.info("Начало обработки для {} школ", AppConfig.SCHOOLS.size());

        int totalProcessed = 0;
        int totalFailed = 0;
        List<Path> successfullyProcessedArchives = new ArrayList<>();

        for (String schoolName : AppConfig.SCHOOLS) {
            try {
                // 1. Формируем путь для школы
                String folderPath = AppConfig.FOLDER_PATCH.replace("{школа}", schoolName);
                log.info("Обработка школы: {} (путь: {})", schoolName, folderPath);

                // 2. Получаем список файлов
                List<ArchiveEntry> filesList = findFilesService.findFilesInArchives(
                        Path.of(folderPath)
                );

                log.info("Найдено архивных записей: {}", filesList.size());

                if (filesList.isEmpty()) {
                    log.warn("Для школы {} не найдено файлов", schoolName);
                    continue;
                }

                // 3. Классифицируем файлы
                Map<String, List<ArchiveEntry>> dispatchArchive =
                        findFilesService.dispatchArchiveProcessing(filesList);

                List<ArchiveEntry> codeListFiles = dispatchArchive.getOrDefault("CODE_LISTS",
                        Collections.emptyList());

                log.info("Файлов со списками кодов: {}", codeListFiles.size());

                // 4. Обрабатываем каждый файл
                for (ArchiveEntry fileEntry : codeListFiles) {
                    try {
                        List<ListStudentData> students =
                                listProcessingService.extractStudentsCodFromArchive(fileEntry);

                        if (!students.isEmpty()) {
                            applySchoolName(students, schoolName);
                            // Сохраняем пакетами если нужно
                            if (AppConfig.BATCH_SIZE > 0 && students.size() > AppConfig.BATCH_SIZE) {
                                saveInBatches(students, AppConfig.BATCH_SIZE);
                            } else {
                                listStudentDataRepository.saveAll(students);
                            }

                            totalProcessed += students.size();

                            // Добавляем архив в список успешно обработанных
                            Path archivePath = fileEntry.getArchivePath();
                            if (!successfullyProcessedArchives.contains(archivePath)) {
                                successfullyProcessedArchives.add(archivePath);
                            }

                            log.info("Файл {} обработан успешно, сохранено {} студентов",
                                    fileEntry.getEntryPath(), students.size());
                        } else {
                            log.warn("Файл {} не содержит данных о студентах",
                                    fileEntry.getEntryPath());
                        }

                    } catch (ProcessingException e) {
                        totalFailed++;
                        log.error("Файл {} не обработан: {}", fileEntry.getEntryPath(), e.getMessage());
                        // Продолжаем с другими файлами
                    }
                }

            } catch (Exception e) {
                log.error("Ошибка обработки для школы {}: {}", schoolName, e.getMessage(), e);
                throw new RuntimeException("Ошибка обработки школы " + schoolName, e);
            }
        }

        // 5. Перемещаем архивы если включено
        if (AppConfig.ENABLE_MOVE && !successfullyProcessedArchives.isEmpty()) {
            try {
                boolean moved = findFilesService.moveToSubjectFolder(successfullyProcessedArchives);
                if (moved) {
                    log.info("✅ Успешно перемещено {} архивов", successfullyProcessedArchives.size());
                } else {
                    log.warn("⚠ Не все архивы удалось переместить");
                }
            } catch (Exception e) {
                log.error("❌ Ошибка при перемещении файлов: {}", e.getMessage());
                // НЕ откатываем транзакцию БД
            }
        }

        log.info("=".repeat(50));
        log.info("📊 ИТОГИ ОБРАБОТКИ:");
        log.info("  🏫 Обработано школ: {}", AppConfig.SCHOOLS.size());
        log.info("  ✅ Успешных файлов: {}", totalProcessed);
        log.info("  ❌ Ошибок: {}", totalFailed);
        log.info("  📦 Перемещено архивов: {}", successfullyProcessedArchives.size());
        log.info("=".repeat(50));
    }

    public void processFGResult() {
        log.info("Начало обработки для {} школ", AppConfig.SCHOOLS.size());

        int totalProcessed = 0;
        int totalFailed = 0;
        List<Path> successfullyProcessed = new ArrayList<>();

        for (String schoolName : AppConfig.SCHOOLS) {
            try {
                // 1. Формируем путь для школы
                String folderPath = AppConfig.FOLDER_PATCH.replace("{школа}", schoolName);
                log.info("Обработка школы: {} (путь: {})", schoolName, folderPath);

                // 2. Получаем список файлов
                List<Path> filesList = findFilesService.findRegularFiles(
                        Path.of(folderPath)
                );


                log.info("Найдено  записей: {}", filesList.size());

                if (filesList.isEmpty()) {
                    log.warn("Для школы {} не найдено файлов", schoolName);
                    continue;
                }

                // 3. Классифицируем файлы
                Map<String, List<Path>> dispatch =
                        findFilesService.dispatchProcessing(filesList);

                List<Path> resultFiles = dispatch.getOrDefault(FileCategory.FG_PDF_RESULTS.name(),
                        Collections.emptyList());

                log.info("Файлов ФГ: {}", resultFiles.size());

                // 4. Обрабатываем каждый файл
                for (Path path : resultFiles) {
                    try {
                        List<StudentResultFGData> resultFG =
                                resultFGProcessorService.extractStudentsResultFG(path);

                        if (!resultFG.isEmpty()) {
                            applySchoolNameToFG(resultFG, schoolName);
                            // Сохраняем пакетами если нужно
                            if (AppConfig.BATCH_SIZE > 0 && resultFG.size() > AppConfig.BATCH_SIZE) {
                                saveInBatchesFG(resultFG, AppConfig.BATCH_SIZE);
                            } else {
                                studentResultFGDataRepository.saveAll(resultFG);
                            }

                            totalProcessed += resultFG.size();

                            // Добавляем в список успешно обработанных

                            if (!successfullyProcessed.contains(path)) {
                                successfullyProcessed.add(path);
                            }

                            log.info("Файл {} обработан успешно, сохранено {} студентов",
                                    path.getFileName(), resultFG.size());
                        } else {
                            log.warn("Файл {} не содержит данных о студентах",
                                    path.getFileName());
                        }

                    } catch (ProcessingException e) {
                        totalFailed++;
                        log.error("Файл {} не обработан: {}", path.getFileName(), e.getMessage());
                        // Продолжаем с другими файлами
                    }
                }

            } catch (Exception e) {
                log.error("Ошибка обработки для школы {}: {}", schoolName, e.getMessage(), e);
                throw new RuntimeException("Ошибка обработки школы " + schoolName, e);
            }
        }

        // 5. Перемещаем если включено
        if (AppConfig.ENABLE_MOVE && !successfullyProcessed.isEmpty()) {
            try {
                boolean moved = findFilesService.moveToSubjectFolder(successfullyProcessed);
                if (moved) {
                    log.info("✅ Успешно перемещено {} файлов ФГ", successfullyProcessed.size());
                } else {
                    log.warn("⚠ Не все файлы ФГ удалось переместить");
                }
            } catch (Exception e) {
                log.error("❌ Ошибка при перемещении файлов ФГ: {}", e.getMessage());
                // НЕ откатываем транзакцию БД
            }
        }

        log.info("=".repeat(50));
        log.info("📊 ИТОГИ ОБРАБОТКИ:");
        log.info("  🏫 Обработано школ: {}", AppConfig.SCHOOLS.size());
        log.info("  ✅ Успешных файлов: {}", totalProcessed);
        log.info("  ❌ Ошибок: {}", totalFailed);
        log.info("  📦 Перемещено файлов ФГ: {}", successfullyProcessed.size());
        log.info("=".repeat(50));
    }

    public void processResult() {
        log.info("Начало обработки для {} школ", AppConfig.SCHOOLS.size());

        int totalProcessed = 0;
        int totalFailed = 0;
        List<Path> successfullyProcessed = new ArrayList<>();

        for (String schoolName : AppConfig.SCHOOLS) {
            try {
                // 1. Формируем путь для школы
                String folderPath = AppConfig.FOLDER_PATCH.replace("{школа}", schoolName);
                log.info("Обработка школы: {} (путь: {})", schoolName, folderPath);

                // 2. Получаем список файлов
                List<Path> filesList = findFilesService.findRegularFiles(
                        Path.of(folderPath)
                );

                log.info("Найдено  записей: {}", filesList.size());

                if (filesList.isEmpty()) {
                    log.warn("Для школы {} не найдено файлов", schoolName);
                    continue;
                }

                // 3. Классифицируем файлы
                Map<String, List<Path>> dispatch =
                        findFilesService.dispatchProcessing(filesList);

                List<Path> resultFiles = dispatch.getOrDefault(FileCategory.EXCEL_RESULTS.name(),
                        Collections.emptyList());

                log.info("Файлов с результатами: {}", resultFiles.size());

                // 4. Обрабатываем каждый файл
                for (Path path : resultFiles) {
                    try {
                        List<StudentResultData> result =
                                resultProcessorService.extractStudentsResult(path);

                        if (!result.isEmpty()) {
                            applySchoolNameToResults(result, schoolName);
                            // Сохраняем пакетами если нужно
                            if (AppConfig.BATCH_SIZE > 0 && result.size() > AppConfig.BATCH_SIZE) {
                                saveInBatchesResult(result, AppConfig.BATCH_SIZE);
                            } else {
                                studentResultDataRepository.saveAll(result);
                            }

                            totalProcessed += result.size();

                            // Добавляем в список успешно обработанных

                            if (!successfullyProcessed.contains(path)) {
                                successfullyProcessed.add(path);
                            }

                            log.info("Файл {} обработан успешно, сохранено {} студентов",
                                    path.getFileName(), result.size());
                        } else {
                            log.warn("Файл {} не содержит данных о студентах",
                                    path.getFileName());
                        }

                    } catch (ProcessingException e) {
                        totalFailed++;
                        log.error("Файл {} не обработан: {}", path.getFileName(), e.getMessage());
                        // Продолжаем с другими файлами
                    } catch (Exception e) {
                        totalFailed++;
                        log.error("Критическая ошибка при обработке файла {}", path, e);
                    }
                }
            } catch (Exception e) {
                log.error("Ошибка обработки для школы {}: {}", schoolName, e.getMessage(), e);
                throw new RuntimeException("Ошибка обработки школы " + schoolName, e);
            }
        }

        // 5. Перемещаем если включено
        if (AppConfig.ENABLE_MOVE && !successfullyProcessed.isEmpty()) {
            try {
                boolean moved = findFilesService.moveToSubjectFolder(successfullyProcessed);
                if (moved) {
                    log.info("✅ Успешно перемещено {} результатов XLXS", successfullyProcessed.size());
                } else {
                    log.warn("⚠ Не все результаты XLXS удалось переместить");
                }
            } catch (Exception e) {
                log.error("❌ Ошибка при перемещении результатов XLXS: {}", e.getMessage());
                // НЕ откатываем транзакцию БД
            }
        }

        log.info("=".repeat(50));
        log.info("📊 ИТОГИ ОБРАБОТКИ:");
        log.info("  🏫 Обработано школ: {}", AppConfig.SCHOOLS.size());
        log.info("  ✅ Успешных файлов: {}", totalProcessed);
        log.info("  ❌ Ошибок: {}", totalFailed);
        log.info("  📦 Перемещено результатов XLXS: {}", successfullyProcessed.size());
        log.info("=".repeat(50));
    }

    private void applySchoolName(List<ListStudentData> students, String schoolName) {
        for (ListStudentData student : students) {
            student.setSchool(schoolName);
        }
    }

    private void applySchoolNameToFG(List<StudentResultFGData> results, String schoolName) {
        for (StudentResultFGData result : results) {
            result.setSchool(schoolName);
        }
    }

    private void applySchoolNameToResults(List<StudentResultData> results, String schoolName) {
        for (StudentResultData result : results) {
            result.setSchool(schoolName);
        }
    }

    private void saveInBatches(List<ListStudentData> students, int batchSize) {
        for (int i = 0; i < students.size(); i += batchSize) {
            int end = Math.min(students.size(), i + batchSize);
            List<ListStudentData> batch = students.subList(i, end);
            listStudentDataRepository.saveAll(batch);
            log.debug("Сохранен пакет {}-{} из {}", i + 1, end, students.size());
        }
    }

    private void saveInBatchesFG(List<StudentResultFGData> resultFGData, int batchSize) {
        for (int i = 0; i < resultFGData.size(); i += batchSize) {
            int end = Math.min(resultFGData.size(), i + batchSize);
            List<StudentResultFGData> batch = resultFGData.subList(i, end);
            studentResultFGDataRepository.saveAll(batch);
            log.debug("Сохранен пакет {}-{} из {}", i + 1, end, resultFGData.size());
        }
    }

    private void saveInBatchesResult(List<StudentResultData> resultData, int batchSize) {
        for (int i = 0; i < resultData.size(); i += batchSize) {
            int end = Math.min(resultData.size(), i + batchSize);
            List<StudentResultData> batch = resultData.subList(i, end);
            studentResultDataRepository.saveAll(batch);
            log.debug("Сохранен пакет {}-{} из {}", i + 1, end, resultData.size());
        }
    }

    /**
     * Создать объединенные отчеты Excel для каждой школы
     * (Аналог processResult() для отчетов)
     */
    @Transactional
    public void createSchoolReports() throws IOException {
        log.info("Начало создания отчетов для {} школ", AppConfig.SCHOOLS.size());

        int totalReportsCreated = 0;
        int totalFailed = 0;
        List<String> successfullyProcessed = new ArrayList<>();

        for (String schoolName : AppConfig.SCHOOLS) {
            log.info("Создание общего отчета для школы: {}", schoolName);


            // Получаем всех студентов школы
            List<ListStudentData> allStudents = listStudentDataRepository.findBySchool(schoolName);
            log.debug("длина allStudents {}", allStudents.size());
            if (allStudents.isEmpty()) {
                totalFailed++;
                log.warn("Нет студентов для школы {}, пропускаем создание отчета", schoolName);
                continue;
            }

            List<StudentResultData> allStudentResults = studentResultDataRepository.findBySchool(schoolName);
            log.debug("длина allStudentResults {}", allStudentResults.size());
            if (allStudentResults.isEmpty()) {
                totalFailed++;
                log.warn("Нет данных результатов для школы {}, пропускаем создание отчета", schoolName);
                continue;
            }

            List<StudentResultFGData> allStudentFGResults = studentResultFGDataRepository.findBySchool(schoolName);
            log.debug("длина allStudentFGResults {}", allStudentFGResults.size());
            if (allStudentFGResults.isEmpty()) {
                totalFailed++;
                log.warn("Нет FG-результатов для школы {}, пропускаем создание отчета", schoolName);
                continue;
            }

            List<OtherDiagnosticData> allOtherDiagnosticResults = otherDiagnosticDataRepository.findBySchool(schoolName);
            log.debug("длина allOtherDiagnosticResults {}", allOtherDiagnosticResults.size());

            // Создаем Map для быстрого поиска результатов по ключам
            Map<String, StudentResultData> resultDataMap = allStudentResults.stream()
                    .filter(result -> hasText(result.getCode()))
                    .collect(Collectors.toMap(
                            result -> buildKey(result.getCode(), result.getClassName(),
                                    result.getSubject(), result.getDate()),
                            result -> result,
                            (existing, replacement) -> existing // при дубликатах берем первый
                    ));

            Map<String, StudentResultData> resultDataByStudentNumberMap = allStudentResults.stream()
                    .filter(result -> result.getStudentNumber() != null)
                    .collect(Collectors.toMap(
                            result -> buildStudentNumberKey(result.getStudentNumber(), result.getClassName(),
                                    result.getSubject(), result.getDate()),
                            result -> result,
                            (existing, replacement) -> existing
                    ));

            Map<String, StudentResultFGData> fgDataMap = allStudentFGResults.stream()
                    .filter(fg -> hasText(fg.getCode()))
                    .collect(Collectors.toMap(
                            fg -> buildKey(fg.getCode(), fg.getClassName(),
                                    fg.getSubject(), fg.getDate()),
                            fg -> fg,
                            (existing, replacement) -> existing // при дубликатах берем первый
                    ));

            Map<String, OtherDiagnosticData> otherDiagnosticByKey = allOtherDiagnosticResults.stream()
                    .collect(Collectors.toMap(
                            diagnostic -> buildReportWorkKey(
                                    schoolName,
                                    diagnostic.getSubject(),
                                    diagnostic.getDate(),
                                    diagnostic.getClassName(),
                                    diagnostic.getSchoolYear()
                            ),
                            diagnostic -> diagnostic,
                            (existing, replacement) -> existing
                    ));

            Map<String, OtherDiagnosticData> otherDiagnosticByKeyWithoutYear = allOtherDiagnosticResults.stream()
                    .collect(Collectors.toMap(
                            diagnostic -> buildReportWorkKeyWithoutYear(
                                    schoolName,
                                    diagnostic.getSubject(),
                                    diagnostic.getDate(),
                                    diagnostic.getClassName()
                            ),
                            diagnostic -> diagnostic,
                            (existing, replacement) -> existing
                    ));

            // Собираем объединенные данные
            List<CombinedResultData> combinedResults = new ArrayList<>();
            log.debug("длина combinedResults {}", combinedResults.size());

            for (ListStudentData student : allStudents) {
                CombinedResultData combined = new CombinedResultData();

                // Копируем данные из ListStudentData
                combined.setNameFIO(student.getNameFIO());
                combined.setCode(student.getCode());
                combined.setClassName(student.getClassName());
                combined.setSubject(student.getSubject());
                combined.setDate(student.getDate());
                combined.setSchool(student.getSchool());
                combined.setSchoolYear(student.getSchoolYear());

                // Ищем соответствующие данные в StudentResultData
                StudentResultData resultData = null;
                if (hasText(student.getCode())) {
                    String resultKey = buildKey(student.getCode(), student.getClassName(),
                            student.getSubject(), student.getDate());
                    resultData = resultDataMap.get(resultKey);
                }
                if (resultData == null) {
                    resultData = resultDataByStudentNumberMap.get(
                            buildStudentNumberKey(student.getStudentNumber(), student.getClassName(),
                                    student.getSubject(), student.getDate())
                    );
                }

                String schoolYear = student.getSchoolYear();
                if (resultData != null && !hasText(schoolYear)) {
                    schoolYear = resultData.getSchoolYear();
                }

                if (resultData != null) {
                    // Копируем данные из StudentResultData
                    combined.setParallel(resultData.getParallel());
                    combined.setLetter(resultData.getLetter());
                    combined.setVariant(resultData.getVariant());
                    combined.setTaskScores(resultData.getTaskScores());
                    combined.setBall(resultData.getBall());
                    combined.setPercentCompleted(resultData.getPercentCompleted());
                    combined.setMark(resultData.getMark());
                    combined.setStudentNumber(resultData.getStudentNumber());
                    combined.setHasResultData(true);

                    // Обновляем className если он есть в resultData (более точный)
                    if (resultData.getClassName() != null && !resultData.getClassName().isEmpty()) {
                        combined.setClassName(resultData.getClassName());
                    }
                } else {
                    combined.setHasResultData(false);
                }

                // Ищем соответствующие данные в StudentResultFGData
                StudentResultFGData fgData = null;
                if (hasText(student.getCode())) {
                    String fgKey = buildKey(student.getCode(), student.getClassName(),
                            student.getSubject(), student.getDate());
                    fgData = fgDataMap.get(fgKey);
                }

                if (fgData != null && !hasText(schoolYear)) {
                    schoolYear = fgData.getSchoolYear();
                }
                combined.setSchoolYear(schoolYear);

                String classNameForLookup = combined.getClassName();
                OtherDiagnosticData diagnosticData = otherDiagnosticByKey.get(
                        buildReportWorkKey(
                                schoolName,
                                combined.getSubject(),
                                combined.getDate(),
                                classNameForLookup,
                                combined.getSchoolYear()
                        )
                );

                if (diagnosticData == null) {
                    diagnosticData = otherDiagnosticByKeyWithoutYear.get(
                            buildReportWorkKeyWithoutYear(
                                    schoolName,
                                    combined.getSubject(),
                                    combined.getDate(),
                                    classNameForLookup
                            )
                    );
                }

                if (diagnosticData != null) {
                    combined.setClassLevel(diagnosticData.getAvgPercent());
                    combined.setCityLevel(diagnosticData.getCityPercent());
                }

                if (fgData != null) {
                    // Копируем данные из StudentResultFGData
                    combined.setOverallPercent(fgData.getOverallPercent());
                    combined.setMasteryLevel(fgData.getMasteryLevel());
                    combined.setSection1Percent(fgData.getSection1Percent());
                    combined.setSection2Percent(fgData.getSection2Percent());
                    combined.setSection3Percent(fgData.getSection3Percent());
                    combined.setHasFGData(true);
                } else {
                    combined.setHasFGData(false);
                }

                combinedResults.add(combined);
            }

            log.debug("длина combinedResults перед передачей в генератор эксель {}", combinedResults.size());
            // Создаем Excel
            byte[] excelBytes = excelExportService.exportToExcel(
                    combinedResults,
                    allStudents,
                    allStudentResults,
                    allStudentFGResults,
                    allOtherDiagnosticResults
            );

            // Сохраняем
            String filePath = saveTotalReportFile(excelBytes, schoolName);

            if (filePath != null) {
                totalReportsCreated++;
                successfullyProcessed.add(schoolName);
                log.info("✅ Общий отчет для школы {} сохранен: {}", schoolName, filePath);
            } else {
                totalFailed++;
                log.error("❌ Не удалось сохранить общий отчет для школы {}", schoolName);
            }
        }

        // 5. Выводим итоги
        log.info("=".repeat(60));
        log.info("📋 ИТОГИ СОЗДАНИЯ ОТЧЕТОВ:");
        log.info("  🏫 Всего школ в конфиге: {}", AppConfig.SCHOOLS.size());
        log.info("  ✅ Успешно обработано школ: {}", successfullyProcessed.size());
        log.info("  ❌ Школ с ошибками: {}", totalFailed);
        log.info("  📄 Создано отчетов: {}", totalReportsCreated);

        if (!successfullyProcessed.isEmpty()) {
            log.info("  🎯 Успешно обработанные школы: {}", successfullyProcessed);
        }

        log.info("=".repeat(60));
    }

    // Вспомогательный метод для создания ключа
    private String buildKey(String code, String className, String subject, String date) {
        return String.format("%s|%s|%s|%s",
                code != null ? code : "",
                className != null ? className : "",
                subject != null ? subject : "",
                date != null ? date : ""
        );
    }

    private String buildStudentNumberKey(Integer studentNumber, String className, String subject, String date) {
        return String.format("%s|%s|%s|%s",
                studentNumber != null ? studentNumber : "",
                className != null ? className : "",
                subject != null ? subject : "",
                date != null ? date : ""
        );
    }

    private String buildReportWorkKey(String school, String subject, String date, String className, String schoolYear) {
        return String.format("%s|%s|%s|%s|%s",
                school != null ? school : "",
                subject != null ? subject : "",
                date != null ? date : "",
                className != null ? className : "",
                schoolYear != null ? schoolYear : ""
        );
    }

    private String buildReportWorkKeyWithoutYear(String school, String subject, String date, String className) {
        return String.format("%s|%s|%s|%s",
                school != null ? school : "",
                subject != null ? subject : "",
                date != null ? date : "",
                className != null ? className : ""
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * Сохранить общий отчет школы
     */
    private String saveTotalReportFile(byte[] excelData, String schoolName) {
        try {
            String reportsFolder = AppConfig.REPORTS_FOLDER.replace("{школа}", schoolName);
            Path folderPath = Paths.get(reportsFolder);

            if (!Files.exists(folderPath)) {
                Files.createDirectories(folderPath);
            }

            String cleanSchoolName = cleanFileName(schoolName);
            String fileName = String.format("ОБЩИЙ_отчет_%s.xlsx", cleanSchoolName);
            Path filePath = folderPath.resolve(fileName);

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(excelData);
            }

            return filePath.toString();

        } catch (Exception e) {
            log.error("Ошибка при сохранении общего отчета: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Очистка имени файла
     */
    private String cleanFileName(String text) {
        if (text == null) return "";
        return text.replaceAll("[^a-zA-Zа-яА-Я0-9]", "_")
                .toLowerCase()
                .replaceAll("_+", "_");
    }

    /**
     * Обрабатывает файлы других диагностик (PDF с _pm, не ФГ)
     */
    @Transactional
    public void processOtherDiagnostics() {
        log.info("Начало обработки других диагностик для {} школ", AppConfig.SCHOOLS.size());

        int totalProcessed = 0;
        int totalFailed = 0;
        List<Path> successfullyProcessed = new ArrayList<>();

        for (String schoolName : AppConfig.SCHOOLS) {
            try {
                // 1. Формируем путь для школы
                String folderPath = AppConfig.FOLDER_PATCH.replace("{школа}", schoolName);
                log.info("Обработка школы: {} (путь: {})", schoolName, folderPath);

                // 2. Получаем список обычных файлов (не архивы)
                List<Path> filesList = findFilesService.findRegularFiles(Path.of(folderPath));
                log.info("Найдено файлов: {}", filesList.size());

                if (filesList.isEmpty()) {
                    log.warn("Для школы {} не найдено файлов", schoolName);
                    continue;
                }

                // 3. Классифицируем файлы
                Map<String, List<Path>> dispatch = findFilesService.dispatchProcessing(filesList);
                List<Path> diagnosticFiles = dispatch.getOrDefault(FileCategory.OTHER_DIAGNOSTICS.name(), Collections.emptyList());
                log.info("Файлов других диагностик: {}", diagnosticFiles.size());

                // 4. Обрабатываем каждый файл
                for (Path path : diagnosticFiles) {
                    try {
                        List<OtherDiagnosticData> dataList = otherDiagnosticParserService.extractDiagnosticData(path);

                        if (!dataList.isEmpty()) {
                            applySchoolNameToOtherDiagnostics(dataList, schoolName);
                            // Сохраняем пакетами если нужно
                            if (AppConfig.BATCH_SIZE > 0 && dataList.size() > AppConfig.BATCH_SIZE) {
                                saveInBatchesOtherDiagnostic(dataList, AppConfig.BATCH_SIZE);
                            } else {
                                otherDiagnosticDataRepository.saveAll(dataList);
                            }

                            totalProcessed += dataList.size();

                            if (!successfullyProcessed.contains(path)) {
                                successfullyProcessed.add(path);
                            }

                            log.info("Файл {} обработан успешно, сохранено {} записей",
                                    path.getFileName(), dataList.size());
                        } else {
                            log.warn("Файл {} не содержит данных о диагностике", path.getFileName());
                        }

                    } catch (ProcessingException e) {
                        totalFailed++;
                        log.error("Файл {} не обработан. Причина парсинга: {}", path.getFileName(), e.getMessage(), e);
                    } catch (Exception e) {
                        totalFailed++;
                        log.error("Критическая ошибка при обработке файла {}: {}", path, e.getMessage(), e);
                    }
                }

            } catch (Exception e) {
                log.error("Ошибка обработки для школы {}: {}", schoolName, e.getMessage(), e);
                // Не бросаем исключение, чтобы продолжить обработку других школ
            }
        }

        // 5. Перемещаем если включено
        if (AppConfig.ENABLE_MOVE && !successfullyProcessed.isEmpty()) {
            try {
                boolean moved = findFilesService.moveToSubjectFolder(successfullyProcessed);
                if (moved) {
                    log.info("✅ Успешно перемещено {} файлов других диагностик", successfullyProcessed.size());
                } else {
                    log.warn("⚠ Не все файлы других диагностик удалось переместить");
                }
            } catch (Exception e) {
                log.error("❌ Ошибка при перемещении файлов других диагностик: {}", e.getMessage());
            }
        }

        log.info("=".repeat(50));
        log.info("📊 ИТОГИ ОБРАБОТКИ ДРУГИХ ДИАГНОСТИК:");
        log.info("  🏫 Обработано школ: {}", AppConfig.SCHOOLS.size());
        log.info("  ✅ Успешных записей: {}", totalProcessed);
        log.info("  ❌ Ошибок: {}", totalFailed);
        log.info("  📦 Перемещено файлов: {}", successfullyProcessed.size());
        log.info("=".repeat(50));
    }

    private void saveInBatchesOtherDiagnostic(List<OtherDiagnosticData> dataList, int batchSize) {
        for (int i = 0; i < dataList.size(); i += batchSize) {
            int end = Math.min(dataList.size(), i + batchSize);
            List<OtherDiagnosticData> batch = dataList.subList(i, end);
            otherDiagnosticDataRepository.saveAll(batch);
            log.debug("Сохранен пакет {}-{} из {}", i + 1, end, dataList.size());
        }
    }

    private void applySchoolNameToOtherDiagnostics(List<OtherDiagnosticData> dataList, String schoolName) {
        for (OtherDiagnosticData data : dataList) {
            data.setSchool(schoolName);
        }
    }
}
