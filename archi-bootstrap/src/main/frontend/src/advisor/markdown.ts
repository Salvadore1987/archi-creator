/**
 * Разбор Markdown ответа помощника в простые блоки — без HTML: заголовки, списки,
 * абзацы, а в строке — жирный, код и ссылки. Ссылка `archi:` — переход к объекту
 * модели, прочие ссылки остаются текстом: помощник не уводит из редактора.
 */
export type Inline =
  | { kind: 'text'; text: string }
  | { kind: 'strong'; text: string }
  | { kind: 'code'; text: string }
  | { kind: 'ref'; text: string; archiId: string };

export type Block =
  | { kind: 'heading'; level: number; inline: Inline[] }
  | { kind: 'list'; ordered: boolean; items: Inline[][] }
  | { kind: 'paragraph'; inline: Inline[] };

const INLINE = /\*\*([^*]+)\*\*|`([^`]+)`|\[([^\]]+)\]\(([^)\s]+)\)/g;

export function parseInline(text: string): Inline[] {
  const result: Inline[] = [];
  let last = 0;
  for (const match of text.matchAll(INLINE)) {
    if (match.index! > last) result.push({ kind: 'text', text: text.slice(last, match.index) });
    if (match[1] !== undefined) result.push({ kind: 'strong', text: match[1] });
    else if (match[2] !== undefined) result.push({ kind: 'code', text: match[2] });
    else if (match[4]!.startsWith('archi:')) result.push({ kind: 'ref', text: match[3]!, archiId: match[4]!.slice(6) });
    else result.push({ kind: 'text', text: match[3]! });
    last = match.index! + match[0].length;
  }
  if (last < text.length) result.push({ kind: 'text', text: text.slice(last) });
  return result;
}

export function parseMarkdown(markdown: string): Block[] {
  const blocks: Block[] = [];
  let paragraph: string[] = [];
  let list: { ordered: boolean; items: Inline[][] } | null = null;
  const flush = () => {
    if (paragraph.length) blocks.push({ kind: 'paragraph', inline: parseInline(paragraph.join(' ')) });
    paragraph = [];
    if (list) blocks.push({ kind: 'list', ...list });
    list = null;
  };
  for (const raw of markdown.split('\n')) {
    const line = raw.trimEnd();
    const heading = line.match(/^(#{1,6})\s+(.*)$/);
    const bullet = line.match(/^\s*[-*]\s+(.*)$/);
    const numbered = line.match(/^\s*\d+[.)]\s+(.*)$/);
    if (heading) {
      flush();
      blocks.push({ kind: 'heading', level: heading[1]!.length, inline: parseInline(heading[2]!) });
    } else if (bullet || numbered) {
      const ordered = numbered !== null;
      if (paragraph.length || (list && list.ordered !== ordered)) flush();
      list ??= { ordered, items: [] };
      list.items.push(parseInline((bullet ?? numbered)![1]!));
    } else if (!line.trim()) {
      flush();
    } else {
      if (list) flush();
      paragraph.push(line.trim());
    }
  }
  flush();
  return blocks;
}
