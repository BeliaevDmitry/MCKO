package org.school.MckoReport.MckoCompleks.service.parser;

import org.school.MckoReport.MckoCompleks.model.StudentResultData;

import java.nio.file.Path;
import java.util.List;

public interface ResultProcessorService {
    /**
     * Метод обрабатывает только exls файлы с результатами
     * возвращает результаты и их коды для сопоставления
     * @param patch  адрес файла
     */
    List<StudentResultData> extractStudentsResult(Path patch);

}

