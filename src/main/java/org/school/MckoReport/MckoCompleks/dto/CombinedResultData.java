package org.school.MckoReport.MckoCompleks.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CombinedResultData {
    // Из ListStudentData
    private String nameFIO;
    private String code;
    private String className;
    private String subject;
    private String date;
    private String school;
    private String schoolYear;

    // Из StudentResultData
    private Integer parallel;
    private String letter;
    private Integer variant;
    private String taskScores;
    private Integer ball;
    private Integer percentCompleted;
    private Integer mark;
    private Integer studentNumber;

    // Из StudentResultFGData
    private String overallPercent;
    private String masteryLevel;
    private String section1Percent;
    private String section2Percent;
    private String section3Percent;

    // Флаги наличия данных
    private boolean hasResultData;
    private boolean hasFGData;
}