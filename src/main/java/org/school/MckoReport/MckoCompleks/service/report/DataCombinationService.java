package org.school.MckoReport.MckoCompleks.service.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.school.MckoReport.MckoCompleks.dto.CombinedResultData;
import org.school.MckoReport.MckoCompleks.model.ListStudentData;
import org.school.MckoReport.MckoCompleks.model.StudentResultData;
import org.school.MckoReport.MckoCompleks.model.StudentResultFGData;
import org.school.MckoReport.MckoCompleks.repository.ListStudentDataRepository;
import org.school.MckoReport.MckoCompleks.repository.StudentResultDataRepository;
import org.school.MckoReport.MckoCompleks.repository.StudentResultFGDataRepository;
import org.school.MckoReport.MckoCompleks.util.DateNormalizerUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataCombinationService {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final ListStudentDataRepository listStudentDataRepository;
    private final StudentResultDataRepository studentResultDataRepository;
    private final StudentResultFGDataRepository studentResultFGDataRepository;

    /**
     * Получить объединенные данные по ключевым параметрам
     */
    public List<CombinedResultData> combineDataByKey(
            String school,
            String subject,
            String date,
            String className) {

        log.info("Объединение данных для школы: {}, предмет: {}, дата: {}, класс: {}",
                school, subject, date, className);

        // Получаем базовые данные студентов
        List<ListStudentData> studentList = getStudentList(school, subject, date, className);

        // Собираем результаты в Map для быстрого поиска по каскаду:
        // 1) точная дата -> 2) месяц+год -> 3) учебный год
        List<StudentResultData> resultData = getResultData(school, subject, className);
        Map<String, StudentResultData> resultDataMap = getResultDataMap(resultData);
        Map<String, StudentResultData> resultDataByStudentNumberMap = getResultDataByStudentNumberMap(resultData);
        Map<String, StudentResultData> resultDataByMonthYearMap = getResultDataByMonthYearMap(resultData);
        Map<String, StudentResultData> resultDataByStudentNumberMonthYearMap = getResultDataByStudentNumberMonthYearMap(resultData);
        Map<String, StudentResultData> resultDataBySchoolYearMap = getResultDataBySchoolYearMap(resultData);
        Map<String, StudentResultData> resultDataByStudentNumberSchoolYearMap = getResultDataByStudentNumberSchoolYearMap(resultData);

        // Собираем ФГ результаты в Map для быстрого поиска
        List<StudentResultFGData> fgData = getFGData(school, subject, className);
        Map<String, StudentResultFGData> fgDataMap = getFGDataMap(fgData, school);
        Map<String, StudentResultFGData> fgDataByMonthYearMap = getFGDataByMonthYearMap(fgData, school);
        Map<String, StudentResultFGData> fgDataBySchoolYearMap = getFGDataBySchoolYearMap(fgData, school);

        // Объединяем данные
        List<CombinedResultData> combinedResults = new ArrayList<>();

        for (ListStudentData student : studentList) {
            CombinedResultData combined = createCombinedData(
                    student,
                    resultDataMap,
                    resultDataByStudentNumberMap,
                    resultDataByMonthYearMap,
                    resultDataByStudentNumberMonthYearMap,
                    resultDataBySchoolYearMap,
                    resultDataByStudentNumberSchoolYearMap,
                    fgDataMap,
                    fgDataByMonthYearMap,
                    fgDataBySchoolYearMap
            );
            combinedResults.add(combined);
        }

        log.info("Объединено {} записей", combinedResults.size());
        return combinedResults;
    }

    private List<ListStudentData> getStudentList(String school, String subject, String date, String className) {
        if (className != null && !className.isEmpty()) {
            return listStudentDataRepository.findBySchoolAndClassNameAndSubjectAndDate(
                    school, className, subject, date);
        } else {
            return listStudentDataRepository.findBySchoolAndSubjectAndDate(school, subject, date);
        }
    }

    private List<StudentResultData> getResultData(String school, String subject, String className) {
        List<StudentResultData> resultData = studentResultDataRepository.findBySchoolAndSubject(school, subject);
        if (className == null || className.isEmpty()) {
            return resultData;
        }
        return resultData.stream()
                .filter(data -> Objects.equals(className, data.getClassName()))
                .collect(Collectors.toList());
    }

    private Map<String, StudentResultData> getResultDataMap(List<StudentResultData> resultData) {
        // Создаем ключ для поиска: code_subject_date_school
        return resultData.stream()
                .filter(data -> hasText(data.getCode()))
                .collect(Collectors.toMap(
                        data -> generateKey(data.getCode(), data.getSubject(), data.getDate(), data.getSchool()),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    private Map<String, StudentResultData> getResultDataByStudentNumberMap(List<StudentResultData> resultData) {
        return resultData.stream()
                .filter(data -> data.getStudentNumber() != null)
                .collect(Collectors.toMap(
                        data -> generateStudentNumberKey(data.getStudentNumber(), data.getSubject(), data.getDate(), data.getSchool()),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    private Map<String, StudentResultData> getResultDataByMonthYearMap(List<StudentResultData> resultData) {
        return resultData.stream()
                .filter(data -> hasText(data.getCode()))
                .filter(data -> hasText(extractMonthYear(data.getDate())))
                .collect(Collectors.toMap(
                        data -> generateMonthYearKey(data.getCode(), data.getSubject(), data.getDate(), data.getSchool()),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    private Map<String, StudentResultData> getResultDataByStudentNumberMonthYearMap(List<StudentResultData> resultData) {
        return resultData.stream()
                .filter(data -> data.getStudentNumber() != null)
                .filter(data -> hasText(extractMonthYear(data.getDate())))
                .collect(Collectors.toMap(
                        data -> generateStudentNumberMonthYearKey(data.getStudentNumber(), data.getSubject(), data.getDate(), data.getSchool()),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    private Map<String, StudentResultData> getResultDataBySchoolYearMap(List<StudentResultData> resultData) {
        return resultData.stream()
                .filter(data -> hasText(data.getCode()))
                .filter(data -> hasText(resolveSchoolYear(data.getSchoolYear(), data.getDate())))
                .collect(Collectors.toMap(
                        data -> generateSchoolYearKey(data.getCode(), data.getSubject(), data.getSchoolYear(), data.getDate(), data.getSchool()),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    private Map<String, StudentResultData> getResultDataByStudentNumberSchoolYearMap(List<StudentResultData> resultData) {
        return resultData.stream()
                .filter(data -> data.getStudentNumber() != null)
                .filter(data -> hasText(resolveSchoolYear(data.getSchoolYear(), data.getDate())))
                .collect(Collectors.toMap(
                        data -> generateStudentNumberSchoolYearKey(data.getStudentNumber(), data.getSubject(), data.getSchoolYear(), data.getDate(), data.getSchool()),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    private List<StudentResultFGData> getFGData(String school, String subject, String className) {
        // Поскольку у FGData нет репозитория с кастомными методами, фильтруем в памяти
        List<StudentResultFGData> allFGData = studentResultFGDataRepository.findAll();
        return allFGData.stream()
                .filter(data -> matchesCriteria(data, school, subject, className))
                .collect(Collectors.toList());
    }

    private Map<String, StudentResultFGData> getFGDataMap(List<StudentResultFGData> fgData, String school) {
        return fgData.stream()
                .filter(data -> hasText(data.getCode()))
                .collect(Collectors.toMap(
                        data -> generateKey(data.getCode(), data.getSubject(), data.getDate(), school),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    private Map<String, StudentResultFGData> getFGDataByMonthYearMap(List<StudentResultFGData> fgData, String school) {
        return fgData.stream()
                .filter(data -> hasText(data.getCode()))
                .filter(data -> hasText(extractMonthYear(data.getDate())))
                .collect(Collectors.toMap(
                        data -> generateMonthYearKey(data.getCode(), data.getSubject(), data.getDate(), school),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    private Map<String, StudentResultFGData> getFGDataBySchoolYearMap(List<StudentResultFGData> fgData, String school) {
        return fgData.stream()
                .filter(data -> hasText(data.getCode()))
                .filter(data -> hasText(resolveSchoolYear(data.getSchoolYear(), data.getDate())))
                .collect(Collectors.toMap(
                        data -> generateSchoolYearKey(data.getCode(), data.getSubject(), data.getSchoolYear(), data.getDate(), school),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    private boolean matchesCriteria(StudentResultFGData data, String school, String subject, String className) {
        boolean matches = Objects.equals(data.getSchool(), school) && Objects.equals(data.getSubject(), subject);

        if (className != null && !className.isEmpty()) {
            matches = matches && Objects.equals(data.getClassName(), className);
        }

        return matches;
    }

    private String generateKey(String code, String subject, String date, String school) {
        return String.format("%s_%s_%s_%s", code, subject, date, school);
    }

    private String generateStudentNumberKey(Integer studentNumber, String subject, String date, String school) {
        return String.format("%s_%s_%s_%s", studentNumber, subject, date, school);
    }

    private String generateMonthYearKey(String code, String subject, String date, String school) {
        return String.format("%s_%s_%s_%s", code, subject, extractMonthYear(date), school);
    }

    private String generateStudentNumberMonthYearKey(Integer studentNumber, String subject, String date, String school) {
        return String.format("%s_%s_%s_%s", studentNumber, subject, extractMonthYear(date), school);
    }

    private String generateSchoolYearKey(String code, String subject, String schoolYear, String date, String school) {
        return String.format("%s_%s_%s_%s", code, subject, resolveSchoolYear(schoolYear, date), school);
    }

    private String generateStudentNumberSchoolYearKey(Integer studentNumber, String subject, String schoolYear, String date, String school) {
        return String.format("%s_%s_%s_%s", studentNumber, subject, resolveSchoolYear(schoolYear, date), school);
    }

    private String extractMonthYear(String date) {
        if (!hasText(date)) {
            return null;
        }
        try {
            LocalDate localDate = LocalDate.parse(date, DATE_FORMATTER);
            return String.format("%02d.%d", localDate.getMonthValue(), localDate.getYear());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String resolveSchoolYear(String schoolYear, String date) {
        if (hasText(schoolYear)) {
            return schoolYear;
        }
        return DateNormalizerUtil.calculateSchoolYear(date);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private CombinedResultData createCombinedData(
            ListStudentData student,
            Map<String, StudentResultData> resultDataMap,
            Map<String, StudentResultData> resultDataByStudentNumberMap,
            Map<String, StudentResultData> resultDataByMonthYearMap,
            Map<String, StudentResultData> resultDataByStudentNumberMonthYearMap,
            Map<String, StudentResultData> resultDataBySchoolYearMap,
            Map<String, StudentResultData> resultDataByStudentNumberSchoolYearMap,
            Map<String, StudentResultFGData> fgDataMap,
            Map<String, StudentResultFGData> fgDataByMonthYearMap,
            Map<String, StudentResultFGData> fgDataBySchoolYearMap) {

        StudentResultData resultData = null;
        if (hasText(student.getCode())) {
            String key = generateKey(student.getCode(), student.getSubject(), student.getDate(), student.getSchool());
            resultData = resultDataMap.get(key);
            if (resultData == null) {
                resultData = resultDataByMonthYearMap.get(
                        generateMonthYearKey(student.getCode(), student.getSubject(), student.getDate(), student.getSchool())
                );
            }
            if (resultData == null) {
                resultData = resultDataBySchoolYearMap.get(
                        generateSchoolYearKey(student.getCode(), student.getSubject(), student.getSchoolYear(), student.getDate(), student.getSchool())
                );
            }
        }
        if (resultData == null) {
            resultData = resultDataByStudentNumberMap.get(
                    generateStudentNumberKey(student.getStudentNumber(), student.getSubject(), student.getDate(), student.getSchool())
            );
        }
        if (resultData == null) {
            resultData = resultDataByStudentNumberMonthYearMap.get(
                    generateStudentNumberMonthYearKey(student.getStudentNumber(), student.getSubject(), student.getDate(), student.getSchool())
            );
        }
        if (resultData == null) {
            resultData = resultDataByStudentNumberSchoolYearMap.get(
                    generateStudentNumberSchoolYearKey(student.getStudentNumber(), student.getSubject(), student.getSchoolYear(), student.getDate(), student.getSchool())
            );
        }
        StudentResultFGData fgData = null;
        if (hasText(student.getCode())) {
            String fgKey = generateKey(student.getCode(), student.getSubject(), student.getDate(), student.getSchool());
            fgData = fgDataMap.get(fgKey);
            if (fgData == null) {
                fgData = fgDataByMonthYearMap.get(
                        generateMonthYearKey(student.getCode(), student.getSubject(), student.getDate(), student.getSchool())
                );
            }
            if (fgData == null) {
                fgData = fgDataBySchoolYearMap.get(
                        generateSchoolYearKey(student.getCode(), student.getSubject(), student.getSchoolYear(), student.getDate(), student.getSchool())
                );
            }
        }

        String schoolYear = firstNonBlank(
                student.getSchoolYear(),
                resultData != null ? resultData.getSchoolYear() : null,
                fgData != null ? fgData.getSchoolYear() : null
        );

        return CombinedResultData.builder()
                .nameFIO(student.getNameFIO())
                .code(student.getCode())
                .className(student.getClassName())
                .subject(student.getSubject())
                .date(student.getDate())
                .school(student.getSchool())
                .schoolYear(schoolYear)

                // Данные из StudentResultData
                .parallel(resultData != null ? resultData.getParallel() : null)
                .letter(resultData != null ? resultData.getLetter() : null)
                .variant(resultData != null ? resultData.getVariant() : null)
                .taskScores(resultData != null ? resultData.getTaskScores() : null)
                .ball(resultData != null ? resultData.getBall() : null)
                .percentCompleted(resultData != null ? resultData.getPercentCompleted() : null)
                .mark(resultData != null ? resultData.getMark() : null)
                .studentNumber(resultData != null ? resultData.getStudentNumber() : null)

                // Данные из StudentResultFGData
                .overallPercent(fgData != null ? fgData.getOverallPercent() : null)
                .masteryLevel(fgData != null ? fgData.getMasteryLevel() : null)
                .section1Percent(fgData != null ? fgData.getSection1Percent() : null)
                .section2Percent(fgData != null ? fgData.getSection2Percent() : null)
                .section3Percent(fgData != null ? fgData.getSection3Percent() : null)

                // Флаги
                .hasResultData(resultData != null)
                .hasFGData(fgData != null)
                .build();
    }

    /**
     * Получить уникальные комбинации для фильтрации
     */
    public Map<String, List<String>> getFilterOptions() {
        Map<String, List<String>> options = new HashMap<>();

        // Школы
        List<String> schools = listStudentDataRepository.findAll().stream()
                .map(ListStudentData::getSchool)
                .distinct()
                .collect(Collectors.toList());
        options.put("schools", schools);

        // Предметы
        List<String> subjects = listStudentDataRepository.findAll().stream()
                .map(ListStudentData::getSubject)
                .distinct()
                .collect(Collectors.toList());
        options.put("subjects", subjects);

        // Даты
        List<String> dates = listStudentDataRepository.findAll().stream()
                .map(ListStudentData::getDate)
                .distinct()
                .collect(Collectors.toList());
        options.put("dates", dates);

        List<String> schoolYears = listStudentDataRepository.findAll().stream()
                .map(ListStudentData::getSchoolYear)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        options.put("schoolYears", schoolYears);

        // Классы
        List<String> classes = listStudentDataRepository.findAll().stream()
                .map(ListStudentData::getClassName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        options.put("classes", classes);

        return options;
    }
}
