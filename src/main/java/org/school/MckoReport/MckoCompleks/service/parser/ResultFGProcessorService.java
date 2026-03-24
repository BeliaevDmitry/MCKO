package org.school.MckoReport.MckoCompleks.service.parser;

import org.school.MckoReport.MckoCompleks.model.StudentResultFGData;

import java.nio.file.Path;
import java.util.List;


public interface ResultFGProcessorService {
    /**
     * Метод принимает адреса файлов, обрабатывает только pdf функциональной грамотности
     * возвращает результаты и их коды для сопоставления
     * @param patch  адрес файла
     */
    List<StudentResultFGData> extractStudentsResultFG(Path patch);

}
