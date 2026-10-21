package uz.salvadore.hamkorbank.archi.advisor.application.port.in;

import java.util.UUID;

/**
 * Ответ помощника, который ещё предстоит прочитать. Все отказы уже приняты, когда
 * он получен: поток не может начаться отказом.
 */
public interface AdvisorStream {

    /** Сессия, которой принадлежит ответ, — известна до начала потока. */
    UUID sessionId();

    /** Отдать ответ целиком; блокирует поток выполнения до последнего кадра. */
    void run(StreamSink sink);
}
