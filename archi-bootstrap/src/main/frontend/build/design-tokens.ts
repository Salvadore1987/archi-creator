import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import type { Plugin } from 'vite';
import { parseAllDocuments } from 'yaml';

/**
 * Токены стиля читаются из файла спецификации на сборке — копии в исходниках
 * фронтенда нет. Плагин отдаёт два виртуальных модуля: CSS-переменные для
 * корпуса и канвы и те же значения объектом — для мест, где число нужно
 * в атрибуте SVG, а не в стиле.
 */
export const TOKENS_FILE = resolve(import.meta.dirname, '../../../../../spec/ui/design-tokens.yaml');

const CSS_ID = 'virtual:design-tokens.css';
const JS_ID = 'virtual:design-tokens';

interface TokenEntry {
  value: string;
  css: string;
}

interface LayerEntry {
  fill: string;
  border: string;
  css: string;
}

export interface DesignTokens {
  layers: Record<string, { fill: string; border: string }>;
  values: Record<string, string>;
  variables: Array<[string, string]>;
}

/** Разбор файла токенов: слои и группы `{ value, css }`; прочие секции — не токены. */
export function readDesignTokens(file: string = TOKENS_FILE): DesignTokens {
  // Первый документ файла — шапка с версией и якорями, токены — во втором.
  const documents = parseAllDocuments(readFileSync(file, 'utf8'));
  const raw = documents[documents.length - 1]?.toJS() as Record<string, unknown>;
  const layers: DesignTokens['layers'] = {};
  const values: DesignTokens['values'] = {};
  const variables: DesignTokens['variables'] = [];

  for (const [layer, entry] of Object.entries(raw.layers as Record<string, LayerEntry>)) {
    layers[layer] = { fill: entry.fill, border: entry.border };
    variables.push([entry.css, entry.fill], [`${entry.css}-border`, entry.border]);
  }
  for (const [group, entries] of Object.entries(raw)) {
    if (group === 'layers' || entries === null || typeof entries !== 'object' || Array.isArray(entries)) {
      continue;
    }
    for (const [name, entry] of Object.entries(entries as Record<string, unknown>)) {
      if (isToken(entry)) {
        values[name] = entry.value;
        variables.push([entry.css, entry.value]);
      }
    }
  }
  return { layers, values, variables };
}

function isToken(entry: unknown): entry is TokenEntry {
  return (
    typeof entry === 'object' &&
    entry !== null &&
    typeof (entry as TokenEntry).value === 'string' &&
    typeof (entry as TokenEntry).css === 'string'
  );
}

export function tokensCss(tokens: DesignTokens): string {
  const lines = tokens.variables.map(([name, value]) => `  ${name}: ${value};`);
  return `:root {\n${lines.join('\n')}\n}\n`;
}

export function designTokensPlugin(file: string = TOKENS_FILE): Plugin {
  return {
    name: 'archi-design-tokens',
    resolveId(id) {
      return id === CSS_ID || id === JS_ID ? `\0${id}` : null;
    },
    load(id) {
      if (id !== `\0${CSS_ID}` && id !== `\0${JS_ID}`) {
        return null;
      }
      this.addWatchFile(file);
      const tokens = readDesignTokens(file);
      return id === `\0${CSS_ID}`
        ? tokensCss(tokens)
        : `export default ${JSON.stringify({ layers: tokens.layers, values: tokens.values })};`;
    },
  };
}
