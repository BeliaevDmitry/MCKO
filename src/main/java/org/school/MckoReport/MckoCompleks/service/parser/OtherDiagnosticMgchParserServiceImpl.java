package org.school.MckoReport.MckoCompleks.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.school.MckoReport.MckoCompleks.expextion.ProcessingException;
import org.school.MckoReport.MckoCompleks.model.OtherDiagnosticData;
import org.school.MckoReport.MckoCompleks.util.DateNormalizerUtil;
import org.school.MckoReport.MckoCompleks.util.SubjectNormalizerUtil;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OtherDiagnosticMgchParserServiceImpl implements OtherDiagnosticMgchParserService {

    @Override
    public List<OtherDiagnosticData> extractDiagnosticData(Path filePath) {
        List<OtherDiagnosticData> results = new ArrayList<>();

        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            String rawDate = extractDate(text);
            String normalizedDate = DateNormalizerUtil.normalizeDate(rawDate);
            String schoolYear = DateNormalizerUtil.calculateSchoolYear(normalizedDate);

            String school = extractSchool(text);
            String className = extractClass(text);
            String subject = extractSubjectForMgch(text);

            OtherDiagnosticData data = OtherDiagnosticData.builder()
                    .school(school)
                    .className(className)
                    .subject(subject)
                    .date(normalizedDate)
                    .schoolYear(schoolYear)
                    .fileName(filePath.getFileName().toString())
                    .build();

            setAveragePercents(data, text);
            validateRequiredFields(data, filePath, rawDate);
            results.add(data);

        } catch (IOException e) {
            throw new ProcessingException("Не удалось обработать PDF МГЧ: " + filePath, e);
        }

        return results;
    }

    private String extractDate(String text) {
        Pattern pattern = Pattern.compile("Дата:\\s*(.+?)(?=\\s+Округ:|\\s+Школа:|\\r|\\n|$)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "дата не определена";
    }

    private String extractSubjectForMgch(String text) {
        Pattern pattern = Pattern.compile("Дата:\\s*.+?\\s+(.+?)\\s+Округ:", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return SubjectNormalizerUtil.normalize(matcher.group(1));
        }
        return "не указан";
    }

    private String extractSchool(String text) {
        Pattern pattern = Pattern.compile("ГБОУ\\s+Школа\\s+№\\s*(\\d+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return "ГБОУ Школа № " + matcher.group(1);
        }
        return "не указана";
    }

    private String extractClass(String text) {
        Pattern pattern = Pattern.compile("Класс:\\s*(\\d+[А-Яа-яЁё]?)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String value = matcher.group(1).trim().toUpperCase();
            if (value.matches("^\\d+[А-ЯЁ]$")) {
                return value.replaceAll("^(\\d+)([А-ЯЁ])$", "$1-$2");
            }
            return value;
        }
        return "не указан";
    }

    private void setAveragePercents(OtherDiagnosticData data, String text) {
        Pattern patternDouble = Pattern.compile("Средний\\s+%\\s+выполнения\\s+(?:диагн\\.\\s+работы|теста):\\s*(\\d+)%\\s*(\\d+)%", Pattern.CASE_INSENSITIVE);
        Matcher matcherDouble = patternDouble.matcher(text);
        if (matcherDouble.find()) {
            data.setAvgPercent(matcherDouble.group(1) + "%");
            data.setCityPercent(matcherDouble.group(2) + "%");
            return;
        }

        Pattern patternSingle = Pattern.compile("Средний\\s+%\\s+выполнения\\s+(?:диагн\\.\\s+работы|теста):\\s*(\\d+)%", Pattern.CASE_INSENSITIVE);
        Matcher matcherSingle = patternSingle.matcher(text);
        if (matcherSingle.find()) {
            data.setAvgPercent(matcherSingle.group(1) + "%");
            data.setCityPercent(null);
            return;
        }

        data.setAvgPercent("не определен");
        data.setCityPercent(null);
    }

    private void validateRequiredFields(OtherDiagnosticData data, Path filePath, String rawDate) {
        List<String> missingFields = new ArrayList<>();
        if (!DateNormalizerUtil.isValidDate(data.getDate())) {
            missingFields.add("дата");
        }
        if (!hasText(data.getClassName()) || "не указан".equalsIgnoreCase(data.getClassName())) {
            missingFields.add("класс");
        }
        if (!hasText(data.getSubject()) || "не указан".equalsIgnoreCase(data.getSubject())) {
            missingFields.add("предмет");
        }
        if (!hasText(data.getAvgPercent()) || "не определен".equalsIgnoreCase(data.getAvgPercent())) {
            missingFields.add("средний % выполнения");
        }
        if (!missingFields.isEmpty()) {
            throw new ProcessingException(
                    "Файл МГЧ не прошел валидацию, отсутствуют обязательные поля: " +
                            String.join(", ", missingFields) +
                            " (" + filePath.getFileName() + "); rawDate='" + rawDate + "'"
            );
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
