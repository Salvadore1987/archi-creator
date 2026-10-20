package uz.salvadore.hamkorbank.archi.modeling.application.mapper;

import uz.salvadore.hamkorbank.archi.modeling.domain.common.PropertyEntry;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.SortOrder;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.AccessType;
import uz.salvadore.hamkorbank.archi.modeling.domain.model.FolderType;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.Bounds;
import uz.salvadore.hamkorbank.archi.modeling.domain.view.StyleOverride;

/**
 * Правила формата значений модели — для того, кто собирает {@code ModelRows} из чужого представления
 * (файла {@code .archimate}). Сами правила живут в домене; здесь они отвечают на простых значениях:
 * примет ли modeling значение в поле строки. Не принятое значение в строку не кладут — оно остаётся
 * там, откуда пришло, и modeling его не теряет и не исправляет.
 */
public final class ModelRowRules {

    private ModelRowRules() {
    }

    /** Позиция {@code index}-го содержимого родителя: разреженная, вставка не сдвигает соседей. */
    public static long position(int index) {
        return SortOrder.ofPosition(index).value();
    }

    /** Знает ли modeling тип корневой папки с таким значением в файле. */
    public static boolean folderType(String fileValue) {
        return FolderType.fromFileValue(fileValue).isPresent();
    }

    /** Знает ли modeling вид доступа с таким значением в файле. */
    public static boolean accessType(String fileValue) {
        return AccessType.fromFileValue(fileValue).isPresent();
    }

    /** Цвет в записи, которую принимает стиль узла и ребра. */
    public static boolean color(String value) {
        return StyleOverride.validColor(value);
    }

    /** Выравнивание текста в нотации Archi, которое принимает стиль. */
    public static boolean textAlignment(int value) {
        return StyleOverride.validTextAlignment(value);
    }

    /** Ширина или высота узла, которую принимает геометрия. */
    public static boolean size(int value) {
        return Bounds.validSize(value);
    }

    /** Ключ свойства, который помещается в поле. */
    public static boolean propertyKey(String key) {
        return key.length() <= PropertyEntry.KEY_MAX;
    }
}
