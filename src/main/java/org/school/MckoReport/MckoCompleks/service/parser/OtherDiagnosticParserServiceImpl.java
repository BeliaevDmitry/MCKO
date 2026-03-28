package org.school.MckoReport.MckoCompleks.service.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.school.MckoReport.MckoCompleks.expextion.ProcessingException;
import org.school.MckoReport.MckoCompleks.model.OtherDiagnosticData;
import org.school.MckoReport.MckoCompleks.util.DateNormalizerUtil;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OtherDiagnosticParserServiceImpl implements OtherDiagnosticParserService {

    public List<OtherDiagnosticData> extractDiagnosticData(Path filePath) {
        List<OtherDiagnosticData> results = new ArrayList<>();

        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            String date = extractDate(text);
            String normalizedDate = DateNormalizerUtil.normalizeDate(date);
            String schoolYear = DateNormalizerUtil.calculateSchoolYear(normalizedDate);

            String school = extractSchool(text);
            String className = extractClass(text);
            String subject = extractSubject(text);

            OtherDiagnosticData data = OtherDiagnosticData.builder()
                    .school(school)
                    .className(className)
                    .subject(subject)
                    .date(normalizedDate)
                    .schoolYear(schoolYear)
                    .fileName(filePath.getFileName().toString())
                    .build();

            setAveragePercents(data, text);
            validateRequiredFields(data, filePath);

            results.add(data);

            log.info("Извлечены данные: школа={}, класс={}, предмет={}, дата={}, %={}, город%={}",
                    school, className, subject, normalizedDate, data.getAvgPercent(), data.getCityPercent());

        } catch (IOException e) {
            log.error("Ошибка при обработке файла {}: {}", filePath, e.getMessage());
            throw new ProcessingException("Не удалось обработать PDF: " + filePath, e);
        }

        return results;
    }

    private String extractDate(String text) {
        Pattern pattern = Pattern.compile("Дата:\\s*([0-9\\-]+\\s+[а-я]+\\s+\\d{4}г\\.?)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "дата не определена";
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
            return matcher.group(1);
        }
        return "не указан";
    }

    private String extractSubject(String text) {
        Pattern pattern = Pattern.compile("Предмет:\\s*([А-Яа-я\\s]+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "не указан";
    }

    private void setAveragePercents(OtherDiagnosticData data, String text) {
        // Ищем два процента: "Средний % выполнения диагн. работы: 27% 38%"
        Pattern patternDouble = Pattern.compile("Средний\\s+%\\s+выполнения\\s+диагн\\.\\s+работы:\\s*(\\d+)%\\s*(\\d+)%");
        Matcher matcherDouble = patternDouble.matcher(text);
        if (matcherDouble.find()) {
            data.setAvgPercent(matcherDouble.group(1) + "%");
            data.setCityPercent(matcherDouble.group(2) + "%");
            return;
        }

        // Если только один процент
        Pattern patternSingle = Pattern.compile("Средний\\s+%\\s+выполнения\\s+диагн\\.\\s+работы:\\s*(\\d+)%");
        Matcher matcherSingle = patternSingle.matcher(text);
        if (matcherSingle.find()) {
            data.setAvgPercent(matcherSingle.group(1) + "%");
            data.setCityPercent(null);
            return;
        }

        data.setAvgPercent("не определен");
        data.setCityPercent(null);
    }

    private void validateRequiredFields(OtherDiagnosticData data, Path filePath) {
        List<String> missingFields = new ArrayList<>();

        if (!hasText(data.getDate()) || "дата не определена".equalsIgnoreCase(data.getDate())) {
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
                    "Файл не прошел валидацию, отсутствуют обязательные поля: " +
                            String.join(", ", missingFields) + " (" + filePath.getFileName() + ")"
            );
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
