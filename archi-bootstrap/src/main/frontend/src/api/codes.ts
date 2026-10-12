/**
 * Коды отказов сервера — поле `code` ответа `problem+json`. Это контракт API:
 * интерфейс ветвится по коду, а текст для человека берёт из своих ресурсов.
 * Единственное место во фронтенде, где строка с кодом инварианта допустима.
 */
export const ErrorCodes = {
  LOCK_REQUIRED: 'INV-MDL-006',
  RELATIONSHIP_ENDS: 'INV-MDL-004',
  FOLDER_TREE: 'INV-MDL-009',
  FOLDER_NOT_EMPTY: 'MDL_FOLDER_NOT_EMPTY',
  RELATION_NOT_PERMITTED: 'RELATION_NOT_PERMITTED',
  ID_TAKEN: 'MDL_ID_TAKEN',
} as const;
