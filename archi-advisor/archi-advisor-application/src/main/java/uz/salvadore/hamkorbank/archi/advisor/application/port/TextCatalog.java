package uz.salvadore.hamkorbank.archi.advisor.application.port;

import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;

/** Текст сообщения на языке запроса. */
public interface TextCatalog {

    String text(Message message);
}
