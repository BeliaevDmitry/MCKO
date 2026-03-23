package org.school.MckoReport.MckoCompleks.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class TaskScoresConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Конвертирует JSON строку в Map<String, Integer>
     */
    public Map<String, Integer> jsonToMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Ошибка десериализации JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Конвертирует Map<String, Integer> в JSON строку
     */
    public String mapToJson(Map<String, Integer> scores) {
        if (scores == null || scores.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(scores);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка сериализации в JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Получить балл за конкретное задание
     */
    public Integer getScoreForTask(String taskScoresJson, String taskName) {
        Map<String, Integer> scores = jsonToMap(taskScoresJson);
        return scores.get(taskName);
    }

    /**
     * Рассчитать сумму баллов
     */
    public Integer calculateTotalScore(Map<String, Integer> scores) {
        if (scores == null || scores.isEmpty()) {
            return 0;
        }
        return scores.values().stream()
                .filter(score -> score != null)
                .mapToInt(Integer::intValue)
                .sum();
    }

    /**
     * Рассчитать процент выполнения
     */
    public Double calculatePercentage(Map<String, Integer> scores, int maxPossibleScore) {
        if (maxPossibleScore <= 0 || scores == null) {
            return 0.0;
        }
        Integer total = calculateTotalScore(scores);
        return (total.doubleValue() / maxPossibleScore) * 100;
    }
}