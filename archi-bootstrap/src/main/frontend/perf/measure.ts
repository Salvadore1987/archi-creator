/** Медиана и максимум времени прогона — замер без внешних библиотек. */
export function measure(runs: number, body: () => void): { median: number; max: number } {
  const times: number[] = [];
  body(); // прогрев: первая итерация меряет JIT, а не алгоритм
  for (let i = 0; i < runs; i++) {
    const started = performance.now();
    body();
    times.push(performance.now() - started);
  }
  times.sort((a, b) => a - b);
  return { median: times[Math.floor(times.length / 2)]!, max: times[times.length - 1]! };
}
