package uz.salvadore.hamkorbank.archi.modeling.domain.metamodel;

import java.util.Set;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingCodes;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.ModelingMessages;

/**
 * Попытка создать связь, которую матрица ArchiMate 3.2 для этой пары типов не
 * допускает. Несёт перечень допустимых — ответ {@code 422} обязан
 * сказать не только «нельзя», но и «а что можно».
 */
public final class RelationNotPermittedException extends RuntimeException {

    /** Код отказа для клиента: такую связь между этими типами создавать нельзя. */
    public static final String CODE = ModelingCodes.RELATION_NOT_PERMITTED;
    /** Код правила, которое нарушено: новая связь обязана быть допустимой по матрице. */
    public static final String INVARIANT = ModelingCodes.RELATION_MATRIX;

    private final ArchiType source;
    private final ArchiType target;
    private final RelationshipType relationship;
    private final Set<RelationshipType> permitted;

    public RelationNotPermittedException(ArchiType source, ArchiType target, RelationshipType relationship,
                                         Set<RelationshipType> permitted) {
        super(INVARIANT + ": " + reason(source, target, relationship, permitted));
        this.source = source;
        this.target = target;
        this.relationship = relationship;
        this.permitted = Set.copyOf(permitted);
    }

    /** Почему отказано — сообщение с ключом; текст собирает адаптер на языке запроса. */
    public Message reason() {
        return reason(source, target, relationship, permitted);
    }

    private static Message reason(ArchiType source, ArchiType target, RelationshipType relationship,
                                  Set<RelationshipType> permitted) {
        return Message.of(ModelingMessages.RELATION_NOT_PERMITTED, relationship, source.simpleName(),
                target.simpleName(), permitted);
    }

    /** Код отказа для клиента API. */
    public String code() {
        return CODE;
    }

    /** Тип источника связи, которую пытались создать. */
    public ArchiType source() {
        return source;
    }

    /** Тип цели связи, которую пытались создать. */
    public ArchiType target() {
        return target;
    }

    /** Тип связи, в котором отказано. */
    public RelationshipType relationship() {
        return relationship;
    }

    /** Типы связей, которые матрица допускает для этой пары. */
    public Set<RelationshipType> permitted() {
        return permitted;
    }
}
