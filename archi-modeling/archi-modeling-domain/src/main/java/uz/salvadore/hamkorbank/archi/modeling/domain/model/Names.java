package uz.salvadore.hamkorbank.archi.modeling.domain.model;

import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingException;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/** Пределы имён из aggregates.yaml: одинаковые для модели, папки, элемента, связи и представления. */
public final class Names {

    /** Предел длины имени. */
    public static final int MAX = 500;

    /** Чьё имя проверяется — подпись в сообщении об ошибке. */
    public static final Message MODEL = Message.of(ModelingMessages.NAME_OF_MODEL);
    /** Подпись папки в сообщении о неверном имени. */
    public static final Message FOLDER = Message.of(ModelingMessages.NAME_OF_FOLDER);
    /** Подпись элемента в сообщении о неверном имени. */
    public static final Message ELEMENT = Message.of(ModelingMessages.NAME_OF_ELEMENT);
    /** Подпись связи в сообщении о неверном имени. */
    public static final Message RELATIONSHIP = Message.of(ModelingMessages.NAME_OF_RELATIONSHIP);
    /** Подпись представления в сообщении о неверном имени. */
    public static final Message VIEW = Message.of(ModelingMessages.NAME_OF_VIEW);

    private Names() {
    }

    /** Имя, которое не может быть пустым: модель, новая папка. */
    public static String required(String name, Message what) {
        if (name == null || name.isBlank()) {
            throw ModelingException.invalid(Message.of(ModelingMessages.NAME_EMPTY, what));
        }
        return limited(name, what);
    }

    /** Имя, которое бывает пустым: у Junction в файле его нет вовсе. */
    public static String limited(String name, Message what) {
        if (name == null) {
            throw ModelingException.invalid(Message.of(ModelingMessages.NAME_MISSING, what));
        }
        if (name.length() > MAX) {
            throw ModelingException.invalid(Message.of(ModelingMessages.NAME_TOO_LONG, what, MAX));
        }
        return name;
    }
}
