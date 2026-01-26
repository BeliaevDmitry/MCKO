package org.school.MckoReport.MckoCompleks.service;

import org.school.MckoReport.MckoCompleks.model.ArchiveEntry;
import org.school.MckoReport.MckoCompleks.model.ListStudentData;

import java.util.List;

public interface ListProcessingService {

    /**
     * Метод принимает адреса файлов, обрабатывает только "Списки участников"
     * возвращает списки студентов и их коды для сопоставления
     *
     * @param archiveEntry archiveEntry адрес файла в архиве
     */
    List<ListStudentData> extractStudentsCodFromArchive(ArchiveEntry archiveEntry);


}
