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
import org.school.MckoReport.MckoCompleks.util.SubjectNormalizerUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
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
        // 1) точная дата -> 2) месяц+год -> 3) учебный год.
        // Значение — список, потому что одна работа может состоять из нескольких листов
        // с уточненным предметом: например "Математика (часть 2 - геометрия)" и
        // "Математика (часть 2 - вероятность и статистика)" для одного списка детей.
        List<StudentResultData> resultData = getResultData(school, subject, className);
        Map<String, List<StudentResultData>> resultDataMap = getResultDataMap(resultData);
        Map<String, List<StudentResultData>> resultDataByMonthYearMap = getResultDataByMonthYearMap(resultData);
        Map<String, List<StudentResultData>> resultDataBySchoolYearMap = getResultDataBySchoolYearMap(resultData);

        // Собираем ФГ результаты в Map для быстрого поиска
        List<StudentResultFGData> fgData = getFGData(school, subject, className);
        Map<String, List<StudentResultFGData>> fgDataMap = getFGDataMap(fgData, school);
        Map<String, List<StudentResultFGData>> fgDataByMonthYearMap = getFGDataByMonthYearMap(fgData, school);
        Map<String, List<StudentResultFGData>> fgDataBySchoolYearMap = getFGDataBySchoolYearMap(fgData, school);

        // Объединяем данные
        List<CombinedResultData> combinedResults = new ArrayList<>();

        for (ListStudentData student : studentList) {
            combinedResults.addAll(createCombinedData(
                    student,
                    resultDataMap,
                    resultDataByMonthYearMap,
                    resultDataBySchoolYearMap,
                    fgDataMap,
                    fgDataByMonthYearMap,
                    fgDataBySchoolYearMap
            ));
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
        List<StudentResultData> resultData = hasText(className)
                ? studentResultDataRepository.findBySchoolAndClassName(school, className)
                : studentResultDataRepository.findBySchool(school);
        return resultData.stream()
                .filter(data -> subjectsMatch(data.getSubject(), subject))
                .collect(Collectors.toList());
    }

    private Map<String, List<StudentResultData>> getResultDataMap(List<StudentResultData> resultData) {
        // Создаем ключ для поиска: code_subject_date_school, где subject нормализован для сопоставления частей работы.
        return resultData.stream()
                .filter(data -> hasText(data.getCode()))
                .collect(Collectors.groupingBy(
                        data -> generateKey(data.getCode(), data.getSubject(), data.getDate(), data.getSchool()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private Map<String, List<StudentResultData>> getResultDataByMonthYearMap(List<StudentResultData> resultData) {
        return resultData.stream()
                .filter(data -> hasText(data.getCode()))
                .filter(data -> hasText(extractMonthYear(data.getDate())))
                .collect(Collectors.groupingBy(
                        data -> generateMonthYearKey(data.getCode(), data.getSubject(), data.getDate(), data.getSchool()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private Map<String, List<StudentResultData>> getResultDataBySchoolYearMap(List<StudentResultData> resultData) {
        return resultData.stream()
                .filter(data -> hasText(data.getCode()))
                .filter(data -> hasText(resolveSchoolYear(data.getSchoolYear(), data.getDate())))
                .collect(Collectors.groupingBy(
                        data -> generateSchoolYearKey(data.getCode(), data.getSubject(), data.getSchoolYear(), data.getDate(), data.getSchool()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private List<StudentResultFGData> getFGData(String school, String subject, String className) {
        // Поскольку у FGData нет репозитория с кастомными методами, фильтруем в памяти
        List<StudentResultFGData> allFGData = studentResultFGDataRepository.findAll();
        return allFGData.stream()
                .filter(data -> matchesCriteria(data, school, subject, className))
                .collect(Collectors.toList());
    }

    private Map<String, List<StudentResultFGData>> getFGDataMap(List<StudentResultFGData> fgData, String school) {
        return fgData.stream()
                .filter(data -> hasText(data.getCode()))
                .collect(Collectors.groupingBy(
                        data -> generateKey(data.getCode(), data.getSubject(), data.getDate(), school),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private Map<String, List<StudentResultFGData>> getFGDataByMonthYearMap(List<StudentResultFGData> fgData, String school) {
        return fgData.stream()
                .filter(data -> hasText(data.getCode()))
                .filter(data -> hasText(extractMonthYear(data.getDate())))
                .collect(Collectors.groupingBy(
                        data -> generateMonthYearKey(data.getCode(), data.getSubject(), data.getDate(), school),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private Map<String, List<StudentResultFGData>> getFGDataBySchoolYearMap(List<StudentResultFGData> fgData, String school) {
        return fgData.stream()
                .filter(data -> hasText(data.getCode()))
                .filter(data -> hasText(resolveSchoolYear(data.getSchoolYear(), data.getDate())))
                .collect(Collectors.groupingBy(
                        data -> generateSchoolYearKey(data.getCode(), data.getSubject(), data.getSchoolYear(), data.getDate(), school),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private boolean matchesCriteria(StudentResultFGData data, String school, String subject, String className) {
        boolean matches = Objects.equals(data.getSchool(), school) && subjectsMatch(data.getSubject(), subject);

        if (className != null && !className.isEmpty()) {
            matches = matches && Objects.equals(data.getClassName(), className);
        }

        return matches;
    }

    private String generateKey(String code, String subject, String date, String school) {
        return String.format("%s_%s_%s_%s", code, normalizeSubjectForMatching(subject), date, school);
    }

    private String generateMonthYearKey(String code, String subject, String date, String school) {
        return String.format("%s_%s_%s_%s", code, normalizeSubjectForMatching(subject), extractMonthYear(date), school);
    }

    private String generateSchoolYearKey(String code, String subject, String schoolYear, String date, String school) {
        return String.format("%s_%s_%s_%s", code, normalizeSubjectForMatching(subject), resolveSchoolYear(schoolYear, date), school);
    }

    private boolean subjectsMatch(String resultSubject, String requestedSubject) {
        return Objects.equals(normalizeSubjectForMatching(resultSubject), normalizeSubjectForMatching(requestedSubject));
    }

    private String normalizeSubjectForMatching(String subject) {
        return SubjectNormalizerUtil.normalizeForMatching(subject);
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

    private List<CombinedResultData> createCombinedData(
            ListStudentData student,
            Map<String, List<StudentResultData>> resultDataMap,
            Map<String, List<StudentResultData>> resultDataByMonthYearMap,
            Map<String, List<StudentResultData>> resultDataBySchoolYearMap,
            Map<String, List<StudentResultFGData>> fgDataMap,
            Map<String, List<StudentResultFGData>> fgDataByMonthYearMap,
            Map<String, List<StudentResultFGData>> fgDataBySchoolYearMap) {

        List<StudentResultData> resultData = findResultData(student, resultDataMap, resultDataByMonthYearMap, resultDataBySchoolYearMap);
        List<StudentResultFGData> fgData = findFGData(student, fgDataMap, fgDataByMonthYearMap, fgDataBySchoolYearMap);

        if (resultData.isEmpty() && fgData.isEmpty()) {
            return Collections.singletonList(buildCombinedData(student, null, null));
        }

        List<CombinedResultData> combined = new ArrayList<>();
        if (!resultData.isEmpty()) {
            for (StudentResultData result : resultData) {
                combined.add(buildCombinedData(student, result, findMatchingFG(result, fgData)));
            }
            return combined;
        }

        for (StudentResultFGData fg : fgData) {
            combined.add(buildCombinedData(student, null, fg));
        }
        return combined;
    }

    private List<StudentResultData> findResultData(
            ListStudentData student,
            Map<String, List<StudentResultData>> resultDataMap,
            Map<String, List<StudentResultData>> resultDataByMonthYearMap,
            Map<String, List<StudentResultData>> resultDataBySchoolYearMap) {
        if (!hasText(student.getCode())) {
            return Collections.emptyList();
        }

        List<StudentResultData> resultData = resultDataMap.get(
                generateKey(student.getCode(), student.getSubject(), student.getDate(), student.getSchool())
        );
        if (resultData != null && !resultData.isEmpty()) {
            return resultData;
        }

        resultData = resultDataByMonthYearMap.get(
                generateMonthYearKey(student.getCode(), student.getSubject(), student.getDate(), student.getSchool())
        );
        if (resultData != null && !resultData.isEmpty()) {
            return resultData;
        }

        resultData = resultDataBySchoolYearMap.get(
                generateSchoolYearKey(student.getCode(), student.getSubject(), student.getSchoolYear(), student.getDate(), student.getSchool())
        );
        if (resultData != null && !resultData.isEmpty()) {
            return resultData;
        }

        return Collections.emptyList();
    }

    private List<StudentResultFGData> findFGData(
            ListStudentData student,
            Map<String, List<StudentResultFGData>> fgDataMap,
            Map<String, List<StudentResultFGData>> fgDataByMonthYearMap,
            Map<String, List<StudentResultFGData>> fgDataBySchoolYearMap) {
        if (!hasText(student.getCode())) {
            return Collections.emptyList();
        }

        List<StudentResultFGData> fgData = fgDataMap.get(
                generateKey(student.getCode(), student.getSubject(), student.getDate(), student.getSchool())
        );
        if (fgData != null && !fgData.isEmpty()) {
            return fgData;
        }

        fgData = fgDataByMonthYearMap.get(
                generateMonthYearKey(student.getCode(), student.getSubject(), student.getDate(), student.getSchool())
        );
        if (fgData != null && !fgData.isEmpty()) {
            return fgData;
        }

        fgData = fgDataBySchoolYearMap.get(
                generateSchoolYearKey(student.getCode(), student.getSubject(), student.getSchoolYear(), student.getDate(), student.getSchool())
        );
        if (fgData != null && !fgData.isEmpty()) {
            return fgData;
        }

        return Collections.emptyList();
    }

    private StudentResultFGData findMatchingFG(StudentResultData resultData, List<StudentResultFGData> fgData) {
        if (resultData == null || fgData.isEmpty()) {
            return null;
        }

        for (StudentResultFGData fg : fgData) {
            if (Objects.equals(resultData.getSubject(), fg.getSubject())) {
                return fg;
            }
        }
        return fgData.get(0);
    }

    private CombinedResultData buildCombinedData(
            ListStudentData student,
            StudentResultData resultData,
            StudentResultFGData fgData) {

        String schoolYear = firstNonBlank(
                student.getSchoolYear(),
                resultData != null ? resultData.getSchoolYear() : null,
                fgData != null ? fgData.getSchoolYear() : null
        );
        String subject = firstNonBlank(
                resultData != null ? resultData.getSubject() : null,
                fgData != null ? fgData.getSubject() : null,
                student.getSubject()
        );

        return CombinedResultData.builder()
                .nameFIO(student.getNameFIO())
                .code(student.getCode())
                .className(student.getClassName())
                .subject(subject)
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
