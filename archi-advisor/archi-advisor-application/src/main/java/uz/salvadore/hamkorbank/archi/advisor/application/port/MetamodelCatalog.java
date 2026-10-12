package uz.salvadore.hamkorbank.archi.advisor.application.port;

import java.util.List;
import java.util.Map;

/**
 * Метамодель ArchiMate глазами помощника: типы фазы 1 и матрица допустимых связей.
 * Список допустимых связей помощник формулирует, но не вычисляет — иначе в подсказке
 * появятся связи, которые сервер отклонит.
 */
public interface MetamodelCatalog {

    /** Типы элементов фазы 1 — только их план может предлагать. */
    List<String> elementTypes();

    /** Типы связей. */
    List<String> relationshipTypes();

    /** Допустимые связи от элемента типа {@code sourceType}: тип связи → типы цели. */
    Map<String, List<String>> permittedFrom(String sourceType);

    /** Допустима ли связь по матрице. Неизвестный тип — недопустима. */
    boolean permitted(String sourceType, String targetType, String relationshipType);
}
