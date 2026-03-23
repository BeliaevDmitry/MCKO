package org.school.MckoReport.MckoCompleks.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "result_fg_data")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentResultFGData {

     @Id
     @GeneratedValue(strategy = GenerationType.IDENTITY)
     private Long id;

     @Column(name = "code", length = 100)
     private String code;

     @Column(name = "school", nullable = false)
     private String school;

     @Column(name = "class_name", length = 50)
     private String className;

     @Column(name = "subject", length = 100)
     private String subject;

     @Column(name = "date", length = 50)
     private String date;

     @Column(name = "overall_percent", length = 20)
     private String overallPercent; // Всего %   храним как строку без %

     @Column(name = "mastery_level", length = 50)
     private String masteryLevel;       //Уровень:

     @Column(name = "section1_percent", length = 20)
     private String section1Percent; // Раздел1 храним как строку без %

     @Column(name = "section2_percent", length = 20)
     private String section2Percent; // Раздел храним как строку без %

     @Column(name = "section3_percent", length = 20)
     private String section3Percent;    //Раздел3

     @Column(name = "school_year", length = 9)
     private String schoolYear;
}