package org.school.MckoReport.MckoCompleks.Config;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AppConfig {
    public static final List<String> SCHOOLS = List.of(
            "ГБОУ №7"
            //,"ГБОУ Школа № 1811"
    );

    public static final List<String> SCHOOLS_MCKO = List.of(
            "ГБОУ № 7"
            //,"ГБОУ Школа № 1811"
    );


    // Путь к папке с файлами
    public static final String FOLDER_PATCH =
            "C:\\Users\\dimah\\Yandex.Disk\\{школа}\\МЦКО\\на обработку";
    // Путь к папке с обработанными файлами
    public static final String OUTPUT_FILE_PATCH =
            "C:\\Users\\dimah\\Yandex.Disk\\{школа}\\МЦКО\\обработано\\{предмет}";
    // Путь для сохранения Excel файла
    public static final String outputExcelPath =
            "C:\\Users\\dimah\\Yandex.Disk\\{школа}\\МЦКО\\ФИО_код.xlsx";
    // Путь для сохранения отчетов
    public static final String REPORTS_FOLDER =
            "C:\\Users\\dimah\\Yandex.Disk\\{школа}\\МЦКО";

    // Дополнительные константы м
    public static final boolean ENABLE_MOVE = true; //переносить файлы или нет true переносить
    public static final int BATCH_SIZE = 100;
}
