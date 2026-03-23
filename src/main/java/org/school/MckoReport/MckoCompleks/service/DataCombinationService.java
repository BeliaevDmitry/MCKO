package org.school.MckoReport.MckoCompleks.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.school.MckoReport.MckoCompleks.dto.CombinedResultData;
import org.school.MckoReport.MckoCompleks.model.ListStudentData;
import org.school.MckoReport.MckoCompleks.model.StudentResultData;
import org.school.MckoReport.MckoCompleks.model.StudentResultFGData;
import org.school.MckoReport.MckoCompleks.repository.ListStudentDataRepository;
import org.school.MckoReport.MckoCompleks.repository.StudentResultDataRepository;
import org.school.MckoReport.MckoCompleks.repository.StudentResultFGDataRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataCombinationService {

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

        // Собираем результаты в Map для быстрого поиска
        Map<String, StudentResultData> resultDataMap = getResultDataMap(school, subject, date, className);
        Map<String, StudentResultData> resultDataByStudentNumberMap = getResultDataByStudentNumberMap(school, subject, date, className);

        // Собираем ФГ результаты в Map для быстрого поиска
        Map<String, StudentResultFGData> fgDataMap = getFGDataMap(school, subject, date, className);

        // Объединяем данные
        List<CombinedResultData> combinedResults = new ArrayList<>();

        for (ListStudentData student : studentList) {
            CombinedResultData combined = createCombinedData(student, resultDataMap, resultDataByStudentNumberMap, fgDataMap);
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

    private Map<String, StudentResultData> getResultDataMap(String school, String subject, String date, String className) {
        List<StudentResultData> resultData;

        if (className != null && !className.isEmpty()) {
            resultData = studentResultDataRepository.findBySchoolAndClassNameAndSubjectAndDate(
                    school, className, subject, date);
        } else {
            resultData = studentResultDataRepository.findBySchoolAndSubjectAndDate(school, subject, date);
        }

        // Создаем ключ для поиска: code_subject_date_school
        return resultData.stream()
                .filter(data -> hasText(data.getCode()))
                .collect(Collectors.toMap(
                        data -> generateKey(data.getCode(), data.getSubject(), data.getDate(), data.getSchool()),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    private Map<String, StudentResultData> getResultDataByStudentNumberMap(String school, String subject, String date, String className) {
        List<StudentResultData> resultData;

        if (className != null && !className.isEmpty()) {
            resultData = studentResultDataRepository.findBySchoolAndClassNameAndSubjectAndDate(
                    school, className, subject, date);
        } else {
            resultData = studentResultDataRepository.findBySchoolAndSubjectAndDate(school, subject, date);
        }

        return resultData.stream()
                .filter(data -> data.getStudentNumber() != null)
                .collect(Collectors.toMap(
                        data -> generateStudentNumberKey(data.getStudentNumber(), data.getSubject(), data.getDate(), data.getSchool()),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    private Map<String, StudentResultFGData> getFGDataMap(String school, String subject, String date, String className) {
        // Поскольку у FGData нет репозитория с кастомными методами, используем Specification или фильтруем в памяти
        List<StudentResultFGData> allFGData = studentResultFGDataRepository.findAll();

        return allFGData.stream()
                .filter(data -> matchesCriteria(data, school, subject, date, className))
                .filter(data -> hasText(data.getCode()))
                .collect(Collectors.toMap(
                        data -> generateKey(data.getCode(), data.getSubject(), data.getDate(), school),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    private boolean matchesCriteria(StudentResultFGData data, String school, String subject, String date, String className) {
        boolean matches = data.getSubject().equals(subject) && data.getDate().equals(date);

        if (className != null && !className.isEmpty()) {
            matches = matches && data.getClassName().equals(className);
        }

        return matches;
    }

    private String generateKey(String code, String subject, String date, String school) {
        return String.format("%s_%s_%s_%s", code, subject, date, school);
    }

    private String generateStudentNumberKey(Integer studentNumber, String subject, String date, String school) {
        return String.format("%s_%s_%s_%s", studentNumber, subject, date, school);
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
            Map<String, StudentResultFGData> fgDataMap) {

        StudentResultData resultData = null;
        if (hasText(student.getCode())) {
            String key = generateKey(student.getCode(), student.getSubject(), student.getDate(), student.getSchool());
            resultData = resultDataMap.get(key);
        }
        if (resultData == null) {
            resultData = resultDataByStudentNumberMap.get(
                    generateStudentNumberKey(student.getStudentNumber(), student.getSubject(), student.getDate(), student.getSchool())
            );
        }
        StudentResultFGData fgData = null;
        if (hasText(student.getCode())) {
            String fgKey = generateKey(student.getCode(), student.getSubject(), student.getDate(), student.getSchool());
            fgData = fgDataMap.get(fgKey);
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