package org.school.MckoReport.MckoCompleks.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.school.MckoReport.MckoCompleks.Config.AppConfig;
import org.school.MckoReport.MckoCompleks.expextion.ProcessingException;
import org.school.MckoReport.MckoCompleks.model.ArchiveEntry;
import org.school.MckoReport.MckoCompleks.model.ListStudentData;
import org.school.MckoReport.MckoCompleks.repository.ListStudentDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class GeneralService {

    private final FindFilesService findFilesService;
    private final ListProcessingService listProcessingService;
    private final ListStudentDataRepository listStudentDataRepository;

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
                        listProcessingService.dispatchArchiveProcessing(filesList);

                List<ArchiveEntry> codeListFiles = dispatchArchive.getOrDefault("CODE_LISTS",
                        Collections.emptyList());

                log.info("Файлов со списками кодов: {}", codeListFiles.size());

                // 4. Обрабатываем каждый файл
                for (ArchiveEntry fileEntry : codeListFiles) {
                    try {
                        List<ListStudentData> students =
                                listProcessingService.extractStudentsCodFromArchive(fileEntry);

                        if (!students.isEmpty()) {
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
        if (AppConfig.ENABLE_ARCHIVE_MOVE && !successfullyProcessedArchives.isEmpty()) {
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

    private void saveInBatches(List<ListStudentData> students, int batchSize) {
        for (int i = 0; i < students.size(); i += batchSize) {
            int end = Math.min(students.size(), i + batchSize);
            List<ListStudentData> batch = students.subList(i, end);
            listStudentDataRepository.saveAll(batch);
            log.debug("Сохранен пакет {}-{} из {}", i + 1, end, students.size());
        }
    }
}