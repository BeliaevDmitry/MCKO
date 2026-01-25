package org.school.MckoReport.MckoCompleks.service;

import org.school.MckoReport.MckoCompleks.model.ArchiveEntry;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

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

}
