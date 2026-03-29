package org.school.MckoReport.MckoCompleks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.school.MckoReport.MckoCompleks.Config.AppConfig;
import org.school.MckoReport.MckoCompleks.service.orchestration.GeneralService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class Main implements CommandLineRunner {

    private final GeneralService generalService;

    public static void main(String[] args) {
        printBanner();
        SpringApplication.run(Main.class, args);
    }

    @Override
    public void run(String... args) {
        try {
            log.info("Запускаем программу: {}", AppConfig.SCHOOLS);
            //generalService.processListCod();
            //generalService.processFGResult();
            //generalService.processResult();
            generalService.processOtherDiagnostics();
            generalService.createSchoolReports();
            log.info("✅ Обработка успешно завершена!");
        } catch (Exception e) {
            log.error("❌ Критическая ошибка при обработке: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private static void printBanner() {
        System.out.println("=".repeat(80));
        System.out.println("🚀 ЗАПУСК СИСТЕМЫ ОБРАБОТКИ ОТЧЁТОВ МЦКО");
        System.out.println("=".repeat(80));
        System.out.println("Конфигурация:");
        System.out.println("  📁 Шаблон с исходными файлами: " + AppConfig.FOLDER_PATCH);
        System.out.println("  📊 Шаблон для отчетов: " + AppConfig.OUTPUT_FILE_PATCH);
        System.out.println("  🏫 Школы для обработки: " + AppConfig.SCHOOLS);
        System.out.println("  🔧 Размер пакета: " + AppConfig.BATCH_SIZE);
        System.out.println("  📦 Перемещение архивов: " + (AppConfig.ENABLE_MOVE ? "ВКЛ" : "ВЫКЛ"));
        System.out.println("=".repeat(80));
    }
}