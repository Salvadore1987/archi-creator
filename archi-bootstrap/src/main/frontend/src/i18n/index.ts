import { folderTypeNames, layerNames, relationVerbs, typeNames } from './notation.ru';
import { ru } from './ru';

type Leaves<T, P extends string = ''> = {
  [K in keyof T & string]: T[K] extends string ? `${P}${K}` : Leaves<T[K], `${P}${K}.`>;
}[keyof T & string];

export type MessageKey = Leaves<typeof ru>;

type Params = Record<string, string | number>;

function lookup(key: string): string | undefined {
  let node: unknown = ru;
  for (const part of key.split('.')) {
    node = (node as Record<string, unknown> | undefined)?.[part];
  }
  return typeof node === 'string' ? node : undefined;
}

/** Строка ресурса с подстановками; отсутствующий ключ виден как есть, а не пустотой. */
export function t(key: MessageKey, params?: Params): string {
  return format(lookup(key) ?? key, params);
}

/** То же для ключа, известного лишь во время работы, — например, кода ошибки сервера. */
export function tOptional(key: string, params?: Params): string | undefined {
  const message = lookup(key);
  return message === undefined ? undefined : format(message, params);
}

function format(template: string, params?: Params): string {
  if (!params) {
    return template;
  }
  return template.replace(/\{(\w+)\}/g, (match, name: string) => (name in params ? String(params[name]) : match));
}

/** Форма слова по числу: 1 элемент, 2 элемента, 5 элементов. */
export function plural(n: number, forms: [string, string, string]): string {
  const mod10 = n % 10;
  const mod100 = n % 100;
  if (mod10 === 1 && mod100 !== 11) return forms[0];
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return forms[1];
  return forms[2];
}

export function typeName(archiType: string): string {
  return typeNames[archiType] ?? archiType.replace(/^[a-z]+:/, '');
}

export function relationVerb(archiType: string, outgoing: boolean): string {
  const verbs = relationVerbs[archiType];
  return verbs ? (outgoing ? verbs.out : verbs.in) : typeName(archiType);
}

export function layerName(layer: string): string {
  return layerNames[layer] ?? layer;
}

export function folderTypeName(folderType: string): string {
  return folderTypeNames[folderType] ?? folderType;
}
