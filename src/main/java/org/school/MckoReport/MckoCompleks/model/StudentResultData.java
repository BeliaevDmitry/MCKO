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
    private String school;              // название школы

    @Column(name = "parallel")
    private Integer parallel;           // параллель

    @Column(name = "letter", length = 2)
    private String letter;              // литера класса

    @Column(name = "subject")
    private String subject;             // предмет

    @Column(name = "work_date")
    private String date;                // дата работы

    @Column(name = "variant")
    private Integer variant;            // вариант работы

    @Column(name = "diagnostic_code", length = 20)
    private String code;                // Код диагн.

    @Column(name = "task_scores", length = 1000)
    private String taskScores;          // результаты в формате 1-1; 2-1; 3-2;...

    @Column(name = "total_score")
    private Integer ball;               // баллов за работу

    @Column(name = "percent_completed")
    private Integer percentCompleted;   // % вып.

    @Column(name = "mark")
    private Integer mark;               // отметка

    @Column(name = "class_name", length = 10)
    private String className;           // вычисляемое поле: "5-А"

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