package org.school.MckoReport.MckoCompleks.service.file;

import org.school.MckoReport.MckoCompleks.model.ArchiveEntry;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface FindFilesService {
    /**
     * Возвращает все обычные файлы (не архивы) рекурсивно
     * @param path адрес папки обработки
     *  Адрес к папке с файлами для работы берет из appConfig
     *  @return Получить список файлов для обработки
     */
    List<Path> findRegularFiles(Path path);

    /**
     * @param path адрес папки обработки
     *  Адрес к папке с файлами для работы берет из appConfig
     *  @return Получить список файлов для обработки
     */
    List<ArchiveEntry> findFilesInArchives(Path path);

    /**
     * @param listPatchSuccessful список файлов для перемещения как обработанных
     * @return возвращает true|false в зависимости от успеха перемещения
     * Адреса для перемещения берет из appConfig
     */
    boolean moveToSubjectFolder(List<Path> listPatchSuccessful);

    /**
     * Анализирует имена файлов, возвращает необходимые адреса по типам:
     * 1. FG_PDF - файлы с "ФГ" в названии и PDF формат
     * 2. EXCEL - Excel файлы (.xlsx, .xls)
     * 3. OTHER - все остальные файлы
     *
     * @param pathsEntries список записей для обработки
     * @return карта результатов обработки по типам файлов
     */
    Map<String, List<Path>> dispatchProcessing(
            List<Path> pathsEntries);

    /**
     * Анализирует имена файлов в архиве и возвращает необходимые адреса по типам
     *
     * @param archiveEntries список записей в архиве для обработки
     * @return карта результатов обработки по типам файлов
     */
    Map<String, List<ArchiveEntry>> dispatchArchiveProcessing(
            List<ArchiveEntry> archiveEntries);
}
