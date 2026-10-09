package uz.salvadore.hamkorbank.archi.bootstrap.web;

import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/**
 * Текст сообщений обоих контекстов на языке запроса: язык берётся из
 * {@code Accept-Language}, по умолчанию — русский. Аргумент-сообщение переводится
 * первым: так подпись объекта встаёт во фразу в нужной форме. Прочие аргументы —
 * строки: разряды числа по локали исказили бы номера версий и идентификаторы.
 * Ключа нет в ресурсах — виден сам ключ, а не пустота.
 */
@Component
public class MessageSourceTexts implements uz.salvadore.hamkorbank.archi.modeling.application.port.TextCatalog,
        uz.salvadore.hamkorbank.archi.interchange.application.port.TextCatalog {

    private final MessageSource messages;

    public MessageSourceTexts(MessageSource messages) {
        this.messages = messages;
    }

    @Override
    public String text(uz.salvadore.hamkorbank.archi.modeling.domain.common.Message message) {
        return resolve(message.key(), message.args(), LocaleContextHolder.getLocale());
    }

    @Override
    public String text(Message message) {
        return resolve(message.key(), message.args(), LocaleContextHolder.getLocale());
    }

    private String resolve(String key, List<Object> args, Locale locale) {
        Object[] resolved = args.stream().map(arg -> switch (arg) {
            case uz.salvadore.hamkorbank.archi.modeling.domain.common.Message nested ->
                    resolve(nested.key(), nested.args(), locale);
            case Message nested -> resolve(nested.key(), nested.args(), locale);
            default -> String.valueOf(arg);
        }).toArray();
        return messages.getMessage(key, resolved, key, locale);
    }
}
