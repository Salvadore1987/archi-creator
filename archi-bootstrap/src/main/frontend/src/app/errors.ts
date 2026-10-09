import { ApiError } from '../api/http';
import { t, tOptional } from '../i18n';
import { UnsupportedChange } from '../model/commands';

/** Отказ — человеческим языком: по коду инварианта, если он известен, иначе текст сервера. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    const known = error.code ? tOptional(`errors.${error.code}`) : undefined;
    if (known) return known;
    if (error.status === 403) return t('errors.forbidden');
    return t('errors.generic', { detail: error.message });
  }
  if (error instanceof UnsupportedChange) {
    return t('errors.unsupported', { detail: error.message });
  }
  if (error instanceof TypeError) {
    return t('errors.network');
  }
  return t('errors.generic', { detail: error instanceof Error ? error.message : String(error) });
}
