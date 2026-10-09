import { ErrorCodes } from '../api/codes';
import { ApiError } from '../api/http';
import { t, type MessageKey } from '../i18n';
import { UnsupportedChange } from '../model/commands';

/** Отказы, которые интерфейс объясняет своими словами; прочие — текстом сервера на языке интерфейса. */
const KNOWN: Record<string, MessageKey> = {
  [ErrorCodes.LOCK_REQUIRED]: 'errors.lockRequired',
  [ErrorCodes.RELATIONSHIP_ENDS]: 'errors.relationshipEnds',
  [ErrorCodes.FOLDER_TREE]: 'errors.folderTree',
  [ErrorCodes.FOLDER_NOT_EMPTY]: 'errors.folderNotEmpty',
  [ErrorCodes.RELATION_NOT_PERMITTED]: 'errors.relationNotPermitted',
};

/** Отказ — человеческим языком: по коду, если он известен, иначе текст сервера. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    const known = error.code ? KNOWN[error.code] : undefined;
    if (known) return t(known);
    if (error.status === 403) return t('errors.forbidden');
    return t('errors.generic', { detail: error.message });
  }
  if (error instanceof UnsupportedChange) {
    return t('errors.unsupported', { detail: t(error.reason) });
  }
  if (error instanceof TypeError) {
    return t('errors.network');
  }
  return t('errors.generic', { detail: error instanceof Error ? error.message : String(error) });
}
