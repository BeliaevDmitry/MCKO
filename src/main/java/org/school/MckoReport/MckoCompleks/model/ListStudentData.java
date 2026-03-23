package org.school.MckoReport.MckoCompleks.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Table(name = "list_student_data")
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListStudentData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column (name = "FIO")
    String nameFIO;

    @Column (name = "className")
    String className;

    @Column (name = "codStudents")
    String code;

    @Column (name = "subject")
    String subject;

    @Column (name = "date")
    String date;

    @Column (name = "school")
    String school;

    @Column(name = "student_number")
    private Integer studentNumber;

    @Column(name = "school_year", length = 9)
    private String schoolYear;
}
