import { describe, expect, it } from 'vitest';
import { readDesignTokens, tokensCss } from './design-tokens';

describe('UI-010: токены — единственный источник стиля', () => {
  const tokens = readDesignTokens();

  it('каждый слой даёт заливку и обводку переменными --layer-*', () => {
    const css = tokensCss(tokens);
    for (const layer of ['business', 'application', 'technology', 'motivation', 'strategy', 'other']) {
      expect(css).toContain(`--layer-${layer}:`);
      expect(css).toContain(`--layer-${layer}-border:`);
    }
  });

  it('токен, на который ссылается выделение, объявлен в том же файле', () => {
    expect(tokens.values.node_shadow_selected).toContain('var(--accent)');
    expect(tokens.variables.map(([name]) => name)).toContain('--accent');
  });

  it('имена переменных не повторяются', () => {
    const names = tokens.variables.map(([name]) => name);
    expect(new Set(names).size).toBe(names.length);
  });
});
