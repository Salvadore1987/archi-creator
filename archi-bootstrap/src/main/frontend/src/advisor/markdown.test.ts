import { describe, expect, it } from 'vitest';
import { parseInline, parseMarkdown } from './markdown';

describe('Markdown ответа помощника', () => {
  it('ссылка archi: — переход к объекту, прочие ссылки остаются текстом', () => {
    expect(parseInline('Компонент [CRM](archi:id-1) и [сайт](https://example.org) **важно** `код`')).toEqual([
      { kind: 'text', text: 'Компонент ' },
      { kind: 'ref', text: 'CRM', archiId: 'id-1' },
      { kind: 'text', text: ' и ' },
      { kind: 'text', text: 'сайт' },
      { kind: 'text', text: ' ' },
      { kind: 'strong', text: 'важно' },
      { kind: 'text', text: ' ' },
      { kind: 'code', text: 'код' },
    ]);
  });

  it('заголовки, списки и абзацы — блоками, без HTML', () => {
    const blocks = parseMarkdown('## Обзор\nПервая строка\nвторая.\n\n- один\n- два\n1. три\n<b>не тег</b>');
    expect(blocks.map((b) => b.kind)).toEqual(['heading', 'paragraph', 'list', 'list', 'paragraph']);
    expect(blocks[1]).toEqual({ kind: 'paragraph', inline: [{ kind: 'text', text: 'Первая строка вторая.' }] });
    expect(blocks[4]).toEqual({ kind: 'paragraph', inline: [{ kind: 'text', text: '<b>не тег</b>' }] });
  });
});
