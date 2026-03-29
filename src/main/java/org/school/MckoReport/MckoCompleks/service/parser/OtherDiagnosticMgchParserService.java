package org.school.MckoReport.MckoCompleks.service.parser;

import org.school.MckoReport.MckoCompleks.model.OtherDiagnosticData;

import java.nio.file.Path;
import java.util.List;

public interface OtherDiagnosticMgchParserService {
    List<OtherDiagnosticData> extractDiagnosticData(Path filePath);
}
