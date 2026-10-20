package uz.salvadore.hamkorbank.archi.interchange.application.exporting;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import uz.salvadore.hamkorbank.archi.interchange.application.InterchangeException;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeCodes;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.InterchangeMessages;
import uz.salvadore.hamkorbank.archi.interchange.domain.common.Message;
import uz.salvadore.hamkorbank.archi.modeling.domain.common.Failure;

/**
 * Растеризация PNG в процессе: предел площади растра и число одновременных растеризаций.
 * Растр держит в куче площадь × 4 байта, поэтому растеризаций — по одной, а очередь честная
 * и поштучная: пакет картинок занимает растеризатор на одно представление, и одиночная картинка
 * ждёт не дольше одной растеризации. Ждать дольше {@code wait} — отказ «занят», а не бесконечная
 * очередь. Один экземпляр на приложение: предел — на процесс.
 */
public final class RasterGate {

    private final long maxAreaPx;
    private final Semaphore slots;
    private final Duration wait;

    /**
     * @param maxAreaPx предел площади растра в пикселях
     * @param parallel  сколько растеризаций идёт одновременно
     * @param wait      сколько ждать свободной растеризации
     */
    public RasterGate(long maxAreaPx, int parallel, Duration wait) {
        if (maxAreaPx < 1 || parallel < 1 || wait.isNegative()) {
            throw new IllegalArgumentException(maxAreaPx + " " + parallel + " " + wait);
        }
        this.maxAreaPx = maxAreaPx;
        this.slots = new Semaphore(parallel, true);
        this.wait = wait;
    }

    /** Предел площади растра в пикселях. */
    public long maxAreaPx() {
        return maxAreaPx;
    }

    /** Выполняет растеризацию, когда подойдёт очередь; не дождался — {@code 503 IXC_RASTERIZER_BUSY}. */
    public <T> T through(Supplier<T> work) {
        try {
            if (!slots.tryAcquire(wait.toMillis(), TimeUnit.MILLISECONDS)) {
                throw busy();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw busy();
        }
        try {
            return work.get();
        } finally {
            slots.release();
        }
    }

    private static InterchangeException busy() {
        return new InterchangeException(InterchangeCodes.RASTERIZER_BUSY, Failure.UNAVAILABLE,
                Message.of(InterchangeMessages.RASTERIZER_BUSY), Map.of());
    }
}
