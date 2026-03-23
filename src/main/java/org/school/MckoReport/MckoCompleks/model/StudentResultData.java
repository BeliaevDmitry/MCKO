package org.school.MckoReport.MckoCompleks.model;

import jakarta.persistence.*;
import lombok.*;

@Data
@Entity
@Table(name = "list_result_data")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentResultData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school", nullable = false)
    private String school;

    @Column(name = "parallel")
    private Integer parallel;

    @Column(name = "letter", length = 2)
    private String letter;

    @Column(name = "subject")
    private String subject;

    @Column(name = "work_date")
    private String date;

    @Column(name = "variant")
    private Integer variant;

    @Column(name = "diagnostic_code", length = 20)
    private String code;

    @Column(name = "task_scores", length = 4000)
    private String taskScores; // Просто храним JSON строку

    @Column(name = "total_score")
    private Integer ball;

    @Column(name = "percent_completed")
    private Integer percentCompleted;

    @Column(name = "mark")
    private Integer mark;

    @Column(name = "class_name", length = 10)
    private String className;

    @Column(name = "student_number")
    private Integer studentNumber;

    @Column(name = "school_year", length = 9)
    private String schoolYear;

    /**
     * Вычисляет className перед сохранением
     */
    @PrePersist
    @PreUpdate
    private void calculateClassName() {
        if (parallel != null && letter != null && !letter.isEmpty()) {
            this.className = parallel + "-" + letter.toUpperCase();
        } else {
            this.className = null;
        }
    }

    /**
     * Геттер для получения className (если не сохранён в БД)
     */
    public String getClassName() {
        if (className != null && !className.isEmpty()) {
            return className;
        }
        if (parallel != null && letter != null && !letter.isEmpty()) {
            return parallel + "-" + letter.toUpperCase();
        }
        return null;
    }
}