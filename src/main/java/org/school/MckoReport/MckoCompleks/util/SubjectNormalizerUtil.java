package org.school.MckoReport.MckoCompleks.util;

public class SubjectNormalizerUtil {

    private SubjectNormalizerUtil() {
    }

    public static String normalize(String subject) {
        if (subject == null) {
            return "";
        }

        String normalized = subject
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("(?i)\\bокруг\\b\\s*:?\\s*.*$", "")
                .replaceAll("(?i)\\bшкола\\b\\s*:?\\s*.*$", "")
                .replaceAll("(?i)\\bкласс\\b\\s*:?\\s*.*$", "")
                .replaceAll("[\\.;:,\\-\\s]+$", "")
                .replaceAll("\\s+", " ")
                .trim();

        if ("Читательская".equalsIgnoreCase(normalized)) {
            return "Читательская грамотность";
        }
        if ("Информационная".equalsIgnoreCase(normalized)) {
            return "Информационная безопасность";
        }
        if ("Вероятность и".equalsIgnoreCase(normalized)) {
            return "Вероятность и статистика";
        }
        if ("Математика (базовый".equalsIgnoreCase(normalized)) {
            return "Математика (базовый уровень)";
        }
        if ("Математика (профильный".equalsIgnoreCase(normalized)) {
            return "Математика (профильный уровень)";
        }

        return normalized;
    }
}
