package uz.salvadore.hamkorbank.archi.advisor.application.service;

import uz.salvadore.hamkorbank.archi.advisor.domain.identity.AdvisorSessionId;

/**
 * Ответ помощника, который ещё предстоит прочитать. Все отказы уже приняты, когда
 * он получен: поток не может начаться отказом.
 */
public interface AdvisorStream {

    AdvisorSessionId sessionId();

    /** Отдать ответ целиком; блокирует поток выполнения до последнего кадра. */
    void run(StreamSink sink);
}
