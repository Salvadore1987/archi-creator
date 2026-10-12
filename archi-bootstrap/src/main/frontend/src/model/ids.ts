/**
 * Идентификаторы новых объектов задаёт клиент: ссылки в истории правок
 * не переписываются после ответа сервера, а отмена удаления возвращает
 * объект с тем же `archi_id`.
 */

/** UUIDv7: время в старших битах — ключи вставляются в индекс по порядку, как у сервера. */
export function uuidv7(now: number = Date.now()): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  const time = BigInt(now);
  for (let i = 0; i < 6; i++) {
    bytes[i] = Number((time >> BigInt(8 * (5 - i))) & 0xffn);
  }
  bytes[6] = (bytes[6]! & 0x0f) | 0x70;
  bytes[8] = (bytes[8]! & 0x3f) | 0x80;
  const hex = [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** Новый `archi_id` — как у Archi: `id-` и 32 шестнадцатеричных знака. */
export function newArchiId(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return `id-${[...bytes].map((b) => b.toString(16).padStart(2, '0')).join('')}`;
}
