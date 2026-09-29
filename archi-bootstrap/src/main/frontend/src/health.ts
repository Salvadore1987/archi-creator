/**
 * Единственный запрос к бэкенду на этапе 0: проба живучести.
 *
 * Адрес относительный, без хоста, — в разработке его перехватывает прокси
 * Vite, в бою статику отдаёт то же приложение. Одинаковый код в обоих
 * случаях получается не из настроек, а из того, что хоста в адресе нет.
 */
export type HealthStatus = 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN';

export interface Health {
  status: HealthStatus;
}

export async function fetchHealth(): Promise<Health> {
  const response = await fetch('/actuator/health', { headers: { Accept: 'application/json' } });
  if (!response.ok) {
    throw new Error(`/actuator/health ответил ${response.status}`);
  }
  return (await response.json()) as Health;
}
