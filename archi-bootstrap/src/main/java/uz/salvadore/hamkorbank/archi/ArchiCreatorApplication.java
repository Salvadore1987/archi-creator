package uz.salvadore.hamkorbank.archi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Точка входа приложения.
 *
 * <p>Класс лежит в корневом пакете {@code uz.salvadore.hamkorbank.archi}, чтобы
 * сканирование компонентов накрывало адаптеры всех трёх контекстов без явного
 * {@code basePackages}: модули собираются в один jar (ADR-0001).
 */
@SpringBootApplication
public class ArchiCreatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArchiCreatorApplication.class, args);
    }
}
