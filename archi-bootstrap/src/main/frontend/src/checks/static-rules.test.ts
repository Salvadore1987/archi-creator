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
