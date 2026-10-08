package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;

/** Пределы имён из aggregates.yaml: одинаковые для модели, папки, элемента, связи и представления. */
public final class Names {

    public static final int MAX = 500;

    private Names() {
    }

    /** Имя, которое не может быть пустым: модель, новая папка. */
    public static String required(String name, String what) {
        if (name == null || name.isBlank()) {
            throw ModelingException.invalid("имя " + what + " пусто");
        }
        return limited(name, what);
    }

    /** Имя, которое бывает пустым: у Junction в файле его нет вовсе. */
    public static String limited(String name, String what) {
        if (name == null) {
            throw ModelingException.invalid("имя " + what + " не задано");
        }
        if (name.length() > MAX) {
            throw ModelingException.invalid("имя " + what + " длиннее " + MAX + " символов");
        }
        return name;
    }
}
