package org.school.MckoReport.MckoCompleks.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "other_diagnostic_data")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtherDiagnosticData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "school", nullable = false)
    private String school;

    @Column(name = "class_name", length = 50)
    private String className;

    @Column(name = "subject", length = 100)
    private String subject;

    @Column(name = "date", length = 50)
    private String date;

    @Column(name = "avg_percent", length = 10)
    private String avgPercent;

    @Column(name = "city_percent", length = 10)
    private String cityPercent;

    @Column(name = "school_year", length = 9)
    private String schoolYear;

    @Column(name = "file_name", length = 255)
    private String fileName;
}