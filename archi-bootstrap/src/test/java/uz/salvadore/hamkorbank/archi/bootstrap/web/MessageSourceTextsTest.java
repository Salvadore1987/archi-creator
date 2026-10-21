package uz.salvadore.hamkorbank.archi.bootstrap.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;

/** Язык текста без запроса — фоновый проход, планировщик — язык продукта, а не локаль JVM. */
class MessageSourceTextsTest {

    private final Locale system = Locale.getDefault();

    @AfterEach
    void restore() {
        Locale.setDefault(system);
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("без контекста запроса — русский, даже если системная локаль английская")
    void backgroundThreadUsesProductLanguage() {
        Locale.setDefault(Locale.ENGLISH);
        LocaleContextHolder.resetLocaleContext();

        assertEquals("Версия 3", texts().text(Message.of(InterchangeMessages.GIT_COMMIT_MESSAGE, 3)));
    }

    @Test
    @DisplayName("язык запроса сильнее языка продукта")
    void requestLanguageWins() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        assertEquals("Version 3", texts().text(Message.of(InterchangeMessages.GIT_COMMIT_MESSAGE, 3)));
    }

    private static MessageSourceTexts texts() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setAlwaysUseMessageFormat(true);
        return new MessageSourceTexts(source);
    }
}
