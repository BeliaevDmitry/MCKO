package org.school.MckoReport.MckoCompleks.repository;

import org.school.MckoReport.MckoCompleks.model.ListStudentData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ListStudentDataRepository extends
        JpaRepository<ListStudentData, Long>,              // <Сущность, Тип ID>
        JpaSpecificationExecutor<ListStudentData> {

    // Дополнительные методы запросов

    /**
     * Найти всех студентов по классу
     */
    List<ListStudentData> findByClassName(String className);

    /**
     * Найти всех студентов по предмету
     */
    List<ListStudentData> findBySubject(String subject);

    /**
     * Найти студента по коду
     */
    List<ListStudentData> findByCode(String code);

    /**
     * Найти всех студентов по школе
     */
    List<ListStudentData> findBySchool(String school);

    /**
     * Найти студентов по классу и предмету
     */
    List<ListStudentData> findByClassNameAndSubject(String className, String subject);

    /**
     * Проверить существование студента с таким кодом
     */
    boolean existsByCode(String code);

    /**
     * Удалить всех студентов по школе
     */
    void deleteBySchool(String school);

    /**
     * Удалить всех студентов по классу
     */
    void deleteByClassName(String className);

    // Новые методы для комбинированного поиска
    List<ListStudentData> findBySchoolAndSubjectAndDate(String school, String subject, String date);

    List<ListStudentData> findBySchoolAndClassNameAndSubjectAndDate(
            String school, String className, String subject, String date);

    @Query("SELECT DISTINCT lsd.date FROM ListStudentData lsd WHERE lsd.school = :school")
    List<String> findDistinctDatesBySchool(@Param("school") String school);

    @Query("SELECT DISTINCT lsd.subject FROM ListStudentData lsd WHERE lsd.school = :school")
    List<String> findDistinctSubjectsBySchool(@Param("school") String school);

    @Query("SELECT DISTINCT lsd.className FROM ListStudentData lsd WHERE lsd.school = :school AND lsd.subject = :subject")
    List<String> findDistinctClassesBySchoolAndSubject(
            @Param("school") String school,
            @Param("subject") String subject);

}