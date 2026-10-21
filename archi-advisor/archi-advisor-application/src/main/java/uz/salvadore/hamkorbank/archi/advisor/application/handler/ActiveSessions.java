package uz.salvadore.hamkorbank.archi.advisor.application.handler;

import java.util.HashMap;
import java.util.Map;
import uz.salvadore.hamkorbank.archi.advisor.application.port.LlmStream;

/**
 * Не больше одного потока на автора: новое обращение отменяет предыдущее, а не
 * добавляет второе. Параллельные обращения одного архитектора не дают ему ничего,
 * кроме удвоенного счёта.
 */
public final class ActiveSessions {

    private final Map<String, LlmStream> active = new HashMap<>();

    /** Встать вместо текущего потока автора; текущий отменяется. */
    public void replace(String subject, LlmStream stream) {
        LlmStream previous;
        synchronized (this) {
            previous = active.put(subject, stream);
        }
        if (previous != null && previous != stream) {
            previous.cancel();
        }
    }

    /** Поток кончился — снять его, если его ещё не сменил следующий. */
    public synchronized void release(String subject, LlmStream stream) {
        active.remove(subject, stream);
    }
}
