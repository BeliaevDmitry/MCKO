package org.school.MckoReport.MckoCompleks.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StudentResultFunctionalLiteracyData {
     String code;
     String className;
     String subject;
     String date;
     String overallPercent; // храним как строку без %
     String masteryLevel;
     String section1Percent; // храним как строку без %
     String section2Percent; // храним как строку без %
     String section3Percent;
}
