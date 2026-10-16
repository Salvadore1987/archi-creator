package uz.salvadore.hamkorbank.archi.interchange.domain.common;

/** Отказ домена interchange: правило формата, импорта или выгрузки нарушено. */
public class InterchangeDomainException extends DomainException {

    /** Отказ с кодом из {@link InterchangeCodes} и причиной-сообщением. */
    public InterchangeDomainException(String code, Message reason) {
        super(code, reason);
    }
}
