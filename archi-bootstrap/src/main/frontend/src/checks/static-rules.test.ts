import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';

const SRC = join(import.meta.dirname, '..');

function files(dir: string, extensions: string[]): string[] {
  const result: string[] = [];
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) {
      result.push(...files(path, extensions));
    } else if (extensions.some((ext) => name.endsWith(ext)) && !name.endsWith('.test.ts')) {
      result.push(path);
    }
  }
  return result;
}

/** Строки, где нашёлся запрещённый шаблон, — с путём и номером, чтобы отказ читался без отладчика. */
function violations(paths: string[], pattern: RegExp, skipLine: (line: string) => boolean = () => false): string[] {
  const found: string[] = [];
  for (const path of paths) {
    readFileSync(path, 'utf8')
      .split('\n')
      .forEach((line, index) => {
        if (!skipLine(line) && pattern.test(line)) {
          found.push(`${relative(SRC, path)}:${index + 1}: ${line.trim()}`);
        }
      });
  }
  return found;
}

const isComment = (line: string) => /^\s*(\/\/|\*|\/\*)/.test(line);

describe('UI-010: в компонентах канвы нет литералов цвета', () => {
  it('цвет приходит из токенов, а не из кода', () => {
    const canvas = existsSync(join(SRC, 'canvas')) ? files(join(SRC, 'canvas'), ['.ts', '.tsx', '.css']) : [];
    expect(violations(canvas, /#[0-9a-fA-F]{3,8}\b|rgba?\(|hsla?\(/, isComment)).toEqual([]);
  });
});
describe('UI-021: пользовательский текст — в ресурсах, а не в разметке', () => {
  const components = files(SRC, ['.tsx']);

  it('в JSX нет текста между тегами', () => {
    // Текст узла JSX — буквы между открывающим тегом и закрывающим.
    const inline = /<[A-Za-z][\w.]*(\s[^<>]*)?>[^<>{}]*[A-Za-zА-Яа-яЁё][^<>{}]*<\//;
    // Текст на отдельной строке внутри разметки — кириллица без синтаксиса кода.
    const standalone = /^\s*[А-Яа-яЁё][^{}<>;=()'"`]*$/;
    expect(violations(components, inline, isComment)).toEqual([]);
    expect(violations(components, standalone, isComment)).toEqual([]);
  });

  it('подсказки и подписи атрибутов — тоже из ресурсов', () => {
    expect(
      violations(components, /\b(title|placeholder|aria-label|alt)="[^"]*[A-Za-zА-Яа-яЁё]/, isComment),
    ).toEqual([]);
  });
});

describe('UI-021: строковые литералы — ключи ресурсов, а не текст', () => {
  const sources = files(SRC, ['.ts', '.tsx']).filter((path) => !relative(SRC, path).startsWith('i18n'));

  it('русского текста в строках кода нет — он живёт в i18n/', () => {
    expect(violations(sources, /(['"`])[^'"`]*[А-Яа-яЁё][^'"`]*\1/, isComment)).toEqual([]);
  });

  it('в строках нет ссылок на требования и якоря спецификации', () => {
    // Коды отказов сервера — контракт API, их место одно: api/codes.ts.
    const all = files(SRC, ['.ts', '.tsx']).filter((path) => relative(SRC, path) !== join('api', 'codes.ts'));
    expect(violations(all, /(['"`])[^'"`]*\b(FR|NFR|INV|UC|UI|ADR)-[A-Z0-9]/, isComment)).toEqual([]);
  });
});
