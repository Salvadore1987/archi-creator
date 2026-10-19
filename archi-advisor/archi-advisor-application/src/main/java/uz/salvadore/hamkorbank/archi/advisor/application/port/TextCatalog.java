package uz.salvadore.hamkorbank.archi.advisor.application.port;

import uz.salvadore.hamkorbank.archi.advisor.domain.common.Message;

/** Текст сообщения на языке запроса. */
public interface TextCatalog {

    /** Текст по ключу и аргументам сообщения на языке текущего запроса. */
    String text(Message message);
}
