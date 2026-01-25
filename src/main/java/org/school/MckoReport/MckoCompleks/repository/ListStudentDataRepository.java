package org.school.MckoReport.MckoCompleks.repository;

import org.school.MckoReport.MckoCompleks.model.ListStudentData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
}