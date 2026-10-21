package uz.salvadore.hamkorbank.archi.interchange.application.git;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Разреженные позиции записей одного контейнера с оглядкой на прежние.
 *
 * <p>Записи, чьи прежние позиции идут в новом порядке по возрастанию (наибольшая
 * такая подпоследовательность), сохраняют их. Остальные встают в промежутки
 * равномерно; в хвосте — шагом 1000. Если промежутка не хватает, контейнер
 * нумеруется заново шагом 1000 — единственный случай, когда двигаются соседи; так же —
 * если хвост вышел бы за наибольшее {@code long}.
 * Повтор с прежними позициями, взятыми из собственного вывода, даёт те же числа.
 */
final class PositionPlan {

    private PositionPlan() {
    }

    static long[] plan(List<String> keys, Map<String, Long> previous) {
        int n = keys.size();
        long[] known = new long[n];
        boolean[] has = new boolean[n];
        for (int i = 0; i < n; i++) {
            Long p = previous.get(keys.get(i));
            if (p != null && p > 0) {
                known[i] = p;
                has[i] = true;
            }
        }
        boolean[] kept = increasingSubsequence(known, has);
        long[] result = new long[n];
        long lower = 0;
        int i = 0;
        while (i < n) {
            if (kept[i]) {
                result[i] = known[i];
                lower = known[i];
                i++;
                continue;
            }
            int end = i;
            while (end < n && !kept[end]) {
                end++;
            }
            int count = end - i;
            if (end == n) {
                if (lower > Long.MAX_VALUE - (count + 1L) * GitYamlFormat.STEP) {
                    return dense(n);
                }
                long next = (lower / GitYamlFormat.STEP + 1) * GitYamlFormat.STEP;
                for (int j = 0; j < count; j++) {
                    result[i + j] = next + j * GitYamlFormat.STEP;
                }
            } else {
                long gap = known[end] - lower;
                if (gap < count + 1) {
                    return dense(n);
                }
                for (int j = 1; j <= count; j++) {
                    result[i + j - 1] = lower + share(gap, j, count + 1);
                }
            }
            i = end;
        }
        return result;
    }

    /**
     * {@code gap * j / parts} с округлением вниз без переполнения: частное и остаток делятся
     * отдельно, поэтому промежуток в весь диапазон {@code long} делится так же, как узкий.
     */
    private static long share(long gap, int j, int parts) {
        return gap / parts * j + gap % parts * j / parts;
    }

    private static long[] dense(int n) {
        long[] result = new long[n];
        for (int i = 0; i < n; i++) {
            result[i] = (i + 1) * GitYamlFormat.STEP;
        }
        return result;
    }

    /** Наибольшая строго возрастающая подпоследовательность среди известных позиций. */
    private static boolean[] increasingSubsequence(long[] values, boolean[] has) {
        int n = values.length;
        int[] tails = new int[n];
        int[] previous = new int[n];
        Arrays.fill(previous, -1);
        int length = 0;
        for (int i = 0; i < n; i++) {
            if (!has[i]) {
                continue;
            }
            int lo = 0;
            int hi = length;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (values[tails[mid]] < values[i]) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            if (lo > 0) {
                previous[i] = tails[lo - 1];
            }
            tails[lo] = i;
            if (lo == length) {
                length++;
            }
        }
        boolean[] kept = new boolean[n];
        List<Integer> chain = new ArrayList<>();
        for (int k = length == 0 ? -1 : tails[length - 1]; k >= 0; k = previous[k]) {
            chain.add(k);
        }
        chain.forEach(k -> kept[k] = true);
        return kept;
    }
}
