package uz.salvadore.hamkorbank.archi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа приложения.
 *
 * <p>Сканирование компонентов накрывает адаптеры всех трёх контекстов — корень
 * {@code uz.salvadore.hamkorbank.archi} назван явно: набор бинов не зависит от того,
 * в каком пакете лежит класс запуска.
 */
@SpringBootApplication(scanBasePackages = "uz.salvadore.hamkorbank.archi")
public class ArchiCreatorApplication {

    /** Запускает приложение Spring Boot с аргументами командной строки. */
    public static void main(String[] args) {
        SpringApplication.run(ArchiCreatorApplication.class, args);
    }
}
