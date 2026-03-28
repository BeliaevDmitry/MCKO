package org.school.MckoReport.MckoCompleks.service.result;

import org.school.MckoReport.MckoCompleks.model.StudentResultData;
import org.school.MckoReport.MckoCompleks.util.TaskScoresConverter;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class StudentResultService {

    private final TaskScoresConverter taskScoresConverter;

    public StudentResultService(TaskScoresConverter taskScoresConverter) {
        this.taskScoresConverter = taskScoresConverter;
    }

    /**
     * Получить результаты как Map для конкретного StudentResultData
     */
    public Map<String, Integer> getTaskScoresAsMap(StudentResultData studentResult) {
        return taskScoresConverter.jsonToMap(studentResult.getTaskScores());
    }

    /**
     * Установить результаты из Map для конкретного StudentResultData
     */
    public void setTaskScoresFromMap(StudentResultData studentResult, Map<String, Integer> scores) {
        studentResult.setTaskScores(taskScoresConverter.mapToJson(scores));
    }

    /**
     * Получить балл за конкретное задание
     */
    public Integer getScoreForTask(StudentResultData studentResult, String taskName) {
        return taskScoresConverter.getScoreForTask(studentResult.getTaskScores(), taskName);
    }

    /**
     * Рассчитать сумму баллов для StudentResultData
     */
    public Integer calculateTotalScore(StudentResultData studentResult) {
        Map<String, Integer> scores = getTaskScoresAsMap(studentResult);
        return taskScoresConverter.calculateTotalScore(scores);
    }

    /**
     * Проверить, правильно ли сохранен балл в studentResult.ball
     * (должен совпадать с суммой taskScores)
     */
    public boolean validateTotalScore(StudentResultData studentResult) {
        Integer calculatedScore = calculateTotalScore(studentResult);
        return calculatedScore.equals(studentResult.getBall());
    }
}