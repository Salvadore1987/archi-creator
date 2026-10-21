package uz.salvadore.hamkorbank.archi.bootstrap.web;

import java.util.List;
import java.util.TreeSet;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Пути полей, не прошедших проверку формы запроса: {@code view.viewId}, {@code nodes[2].id},
 * {@code [0]} — элемент тела-массива. Тело-объект проверяет разбор аргумента, тело-массив
 * с ограничениями на элементах — проверка метода; пути у обоих одного вида.
 */
final class RequestViolations {

    private RequestViolations() {
    }

    static List<String> fields(MethodArgumentNotValidException invalid) {
        TreeSet<String> paths = new TreeSet<>();
        invalid.getBindingResult().getFieldErrors().forEach(error -> paths.add(error.getField()));
        return List.copyOf(paths);
    }

    static List<String> fields(HandlerMethodValidationException invalid) {
        TreeSet<String> paths = new TreeSet<>();
        for (ParameterValidationResult result : invalid.getParameterValidationResults()) {
            String at = position(result);
            if (result instanceof ParameterErrors errors && errors.hasFieldErrors()) {
                errors.getFieldErrors().forEach(error -> paths.add(at.isEmpty() ? error.getField()
                        : at + "." + error.getField()));
            } else {
                paths.add(at.isEmpty() ? String.valueOf(result.getMethodParameter().getParameterName()) : at);
            }
        }
        return List.copyOf(paths);
    }

    private static String position(ParameterValidationResult result) {
        if (result.getContainerIndex() != null) {
            return "[" + result.getContainerIndex() + "]";
        }
        return result.getContainerKey() == null ? "" : "[" + result.getContainerKey() + "]";
    }
}
