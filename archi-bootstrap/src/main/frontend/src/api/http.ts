import { UI_LOCALE } from '../i18n';
import type { Problem } from './types';

const BASE = '/api/v1';

/** Отказ сервера с телом `problem+json`; код инварианта — в `problem.code`. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem: Problem,
  ) {
    super(problem.detail ?? problem.title ?? `HTTP ${status}`);
  }

  get code(): string | undefined {
    return this.problem.code;
  }
}

type TokenSource = () => Promise<string | null>;

let tokenSource: TokenSource = async () => null;
let onUnauthorized: () => void = () => {};

/** Откуда брать токен и что делать при `401` — решает вход, а не клиент. */
export function configureAuth(source: TokenSource, unauthorized: () => void): void {
  tokenSource = source;
  onUnauthorized = unauthorized;
}

export interface RequestOptions {
  method?: string;
  body?: unknown;
  idempotencyKey?: string;
  /** Ответ не JSON: выгрузка файла. */
  raw?: boolean;
  signal?: AbortSignal;
  keepalive?: boolean;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {
    Accept: 'application/json, application/problem+json',
    'Accept-Language': UI_LOCALE,
  };
  const token = await tokenSource();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (options.idempotencyKey) {
    headers['Idempotency-Key'] = options.idempotencyKey;
  }
  let body: BodyInit | undefined;
  if (options.body instanceof FormData) {
    body = options.body;
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(options.body);
  }
  const response = await fetch(BASE + path, {
    method: options.method ?? 'GET',
    headers,
    body,
    signal: options.signal,
    keepalive: options.keepalive,
  });
  if (response.status === 401) {
    onUnauthorized();
  }
  if (!response.ok) {
    throw new ApiError(response.status, await problemOf(response));
  }
  if (options.raw) {
    return response as unknown as T;
  }
  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

async function problemOf(response: Response): Promise<Problem> {
  try {
    const parsed = (await response.json()) as Problem;
    return { ...parsed, status: parsed.status ?? response.status };
  } catch {
    return { status: response.status, title: response.statusText };
  }
}
