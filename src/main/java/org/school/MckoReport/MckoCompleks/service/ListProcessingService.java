package org.school.MckoReport.MckoCompleks.service;

import org.school.MckoReport.MckoCompleks.model.ArchiveEntry;
import org.school.MckoReport.MckoCompleks.model.ListStudentData;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.Map;


public interface ListProcessingService {
    /**
     * Анализирует имена файлов в архиве и возвращает необходимые адреса по типам
     *
     * @param archiveEntries список записей в архиве для обработки
     * @return карта результатов обработки по типам файлов
     */
    Map<String, List<ArchiveEntry>> dispatchArchiveProcessing(
            List<ArchiveEntry> archiveEntries);

    /**
     * Метод принимает адреса файлов, обрабатывает только "Списки участников"
     * возвращает списки студентов и их коды для сопоставления
     *
     * @param archiveEntry archiveEntry адрес файла в архиве
     */
    List<ListStudentData> extractStudentsCodFromArchive(ArchiveEntry archiveEntry);


}
