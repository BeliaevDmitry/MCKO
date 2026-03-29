package org.school.MckoReport.MckoCompleks.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "MCKO_combined_report_data")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MckoCombinedReportData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "row_key", nullable = false, unique = true, length = 500)
    private String rowKey;

    @Column(name = "source_type", length = 20)
    private String sourceType;

    @Column(name = "fio", length = 255)
    private String nameFIO;

    @Column(name = "diagnostic_code", length = 120)
    private String code;

    @Column(name = "class_name", length = 50)
    private String className;

    @Column(name = "subject", length = 120)
    private String subject;

    @Column(name = "work_date", length = 30)
    private String date;

    @Column(name = "school", length = 120)
    private String school;

    @Column(name = "school_year", length = 9)
    private String schoolYear;

    @Column(name = "class_level", length = 20)
    private String classLevel;

    @Column(name = "city_level", length = 20)
    private String cityLevel;

    @Column(name = "parallel")
    private Integer parallel;

    @Column(name = "letter", length = 10)
    private String letter;

    @Column(name = "variant")
    private Integer variant;

    @Column(name = "ball")
    private Integer ball;

    @Column(name = "percent_completed")
    private Integer percentCompleted;

    @Column(name = "grade_value", length = 50)
    private String gradeValue;

    @Column(name = "student_number")
    private Integer studentNumber;

    @Column(name = "task_scores", length = 4000)
    private String taskScores;
}
