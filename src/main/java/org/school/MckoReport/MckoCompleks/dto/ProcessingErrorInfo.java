package org.school.MckoReport.MckoCompleks.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingErrorInfo {
    private String school;
    private String fileName;
    private String stage;
    private String reason;
    private String rawDate;
}
