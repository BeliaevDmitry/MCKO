package org.school.MckoReport.MckoCompleks.util;

public class SubjectNormalizerUtil {
    private static final String DISTRICT_MARKER_REGEX = "(?iu)\\b[оo]круг\\b\\s*:?";
    private static final String SCHOOL_MARKER_REGEX = "(?iu)\\bшкола\\b\\s*:?";
    private static final String CLASS_MARKER_REGEX = "(?iu)\\bкласс\\b\\s*:?";

    private SubjectNormalizerUtil() {
    }

    public static String normalize(String subject) {
        if (subject == null) {
            return "";
        }

        String normalized = subject
                .replaceAll("[\\r\\n]+", " ")
                .trim();

        normalized = trimByMetadataMarkers(normalized)
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

    private static String trimByMetadataMarkers(String value) {
        int districtIndex = findMarkerIndex(value, DISTRICT_MARKER_REGEX);
        int schoolIndex = findMarkerIndex(value, SCHOOL_MARKER_REGEX);
        int classIndex = findMarkerIndex(value, CLASS_MARKER_REGEX);

        int cutIndex = minPositiveIndex(districtIndex, schoolIndex, classIndex);
        if (cutIndex >= 0) {
            return value.substring(0, cutIndex).trim();
        }
        return value;
    }

    private static int findMarkerIndex(String text, String regex) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(text);
        return matcher.find() ? matcher.start() : -1;
    }

    private static int minPositiveIndex(int... values) {
        int min = Integer.MAX_VALUE;
        for (int value : values) {
            if (value >= 0 && value < min) {
                min = value;
            }
        }
        return min == Integer.MAX_VALUE ? -1 : min;
    }
}
