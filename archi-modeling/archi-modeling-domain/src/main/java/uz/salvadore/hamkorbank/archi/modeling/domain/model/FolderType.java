package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import java.util.Arrays;
import java.util.Optional;
import uz.salvadore.hamkorbank.archi.modeling.domain.metamodel.Layer;

/**
 * Девять корневых папок Archi в порядке, в котором их пишет Archi.
 * В файле — атрибут {@code type} в нижнем регистре.
 */
public enum FolderType {
    STRATEGY("strategy", "Strategy"),
    BUSINESS("business", "Business"),
    APPLICATION("application", "Application"),
    TECHNOLOGY("technology", "Technology & Physical"),
    MOTIVATION("motivation", "Motivation"),
    IMPLEMENTATION_MIGRATION("implementation_migration", "Implementation & Migration"),
    OTHER("other", "Other"),
    RELATIONS("relations", "Relations"),
    DIAGRAMS("diagrams", "Views");

    private final String fileValue;
    private final String defaultName;

    FolderType(String fileValue, String defaultName) {
        this.fileValue = fileValue;
        this.defaultName = defaultName;
    }

    /** Значение атрибута {@code type} в файле. */
    public String fileValue() {
        return fileValue;
    }

    /** Имя, которое Archi даёт папке новой модели. */
    public String defaultName() {
        return defaultName;
    }

    public static Optional<FolderType> fromFileValue(String value) {
        return Arrays.stream(values()).filter(t -> t.fileValue.equals(value)).findFirst();
    }

    /** Корневая папка, в поддереве которой лежат элементы слоя. */
    public static FolderType forLayer(Layer layer) {
        return switch (layer) {
            case BUSINESS -> BUSINESS;
            case APPLICATION -> APPLICATION;
            case TECHNOLOGY, PHYSICAL -> TECHNOLOGY;
            case MOTIVATION -> MOTIVATION;
            case STRATEGY -> STRATEGY;
            case IMPLEMENTATION -> IMPLEMENTATION_MIGRATION;
            case OTHER -> OTHER;
        };
    }
}
