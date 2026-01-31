package org.school.MckoReport.MckoCompleks.repository;

import org.school.MckoReport.MckoCompleks.model.StudentResultData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentResultDataRepository extends
        JpaRepository<StudentResultData, Long>,
        JpaSpecificationExecutor<StudentResultData> {

    // Базовые запросы

    List<StudentResultData> findBySchool(String school);

    List<StudentResultData> findBySubject(String subject);

    List<StudentResultData> findByCode(String code);

    List<StudentResultData> findByClassName(String className);

    List<StudentResultData> findByParallelAndLetter(Integer parallel, String letter);

    List<StudentResultData> findByDate(String date);

    List<StudentResultData> findByVariant(Integer variant);

    // Составные запросы

    List<StudentResultData> findBySchoolAndSubject(String school, String subject);

    List<StudentResultData> findBySchoolAndClassName(String school, String className);

    List<StudentResultData> findBySchoolAndDate(String school, String date);

    // Запросы с диапазонами

    List<StudentResultData> findByBallGreaterThan(Integer minBall);

    List<StudentResultData> findByBallBetween(Integer minBall, Integer maxBall);

    List<StudentResultData> findByPercentCompletedGreaterThan(Integer minPercent);

    // Кастомные JPQL запросы

    @Query("SELECT s FROM StudentResultData s WHERE s.school = :school AND s.date BETWEEN :startDate AND :endDate")
    List<StudentResultData> findBySchoolAndDateRange(
            @Param("school") String school,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    @Query("SELECT DISTINCT s.subject FROM StudentResultData s WHERE s.school = :school")
    List<String> findDistinctSubjectsBySchool(@Param("school") String school);

    @Query("SELECT DISTINCT s.className FROM StudentResultData s WHERE s.school = :school")
    List<String> findDistinctClassesBySchool(@Param("school") String school);

    @Query("SELECT AVG(s.ball) FROM StudentResultData s WHERE s.school = :school AND s.subject = :subject")
    Double findAverageScoreBySchoolAndSubject(
            @Param("school") String school,
            @Param("subject") String subject);

    // Проверка существования

    boolean existsByCode(String code);

    boolean existsBySchoolAndClassNameAndSubjectAndDate(
            String school, String className, String subject, String date);

    // Удаление

    void deleteBySchool(String school);

    void deleteBySchoolAndDate(String school, String date);

    void deleteByCode(String code);

    // Поиск по части кода

    List<StudentResultData> findByCodeContaining(String codePart);

    List<StudentResultData> findByCodeStartingWith(String prefix);

    // Новые методы для комбинированного поиска
    List<StudentResultData> findBySchoolAndSubjectAndDate(String school, String subject, String date);

    List<StudentResultData> findBySchoolAndClassNameAndSubjectAndDate(
            String school, String className, String subject, String date);

    List<StudentResultData> findByCodeAndSubjectAndDate(String code, String subject, String date);
}