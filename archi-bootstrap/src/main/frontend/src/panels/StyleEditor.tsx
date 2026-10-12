import { useEffect, useRef, useState } from 'react';
import type { Style, Uuid } from '../api/types';
import { formatArchiFont, parseArchiFont, type FontSpec } from '../canvas/style';
import { t } from '../i18n';
import { restyle } from '../model/ops';
import { useEditor } from '../model/store';

const FAMILIES = ['Segoe UI', 'Arial', 'Tahoma', 'Verdana', 'Times New Roman', 'Courier New'];
const SIZES = [7, 8, 9, 10, 11, 12, 14, 16, 18, 24];
const DEFAULT_FONT: FontSpec = { family: 'Segoe UI', sizePt: 9, bold: false, italic: false };
const ALIGNMENTS = [
  { value: 1, label: 'props.alignLeft' },
  { value: 2, label: 'props.alignCenter' },
  { value: 4, label: 'props.alignRight' },
] as const;

/**
 * Выбор цвета фиксируется, когда выбор закончен, а не на каждое движение по палитре:
 * одна правка — одна операция истории.
 */
function ColorField(props: { value: string; disabled: boolean; testId: string; onCommit(value: string): void }) {
  const [draft, setDraft] = useState(props.value);
  const input = useRef<HTMLInputElement>(null);
  const latest = useRef(props);
  latest.current = props;
  useEffect(() => setDraft(props.value), [props.value]);
  useEffect(() => {
    const element = input.current;
    if (!element) return;
    const commit = () => {
      if (element.value !== latest.current.value) latest.current.onCommit(element.value);
    };
    element.addEventListener('change', commit);
    return () => element.removeEventListener('change', commit);
  }, []);
  return (
    <input
      ref={input}
      type="color"
      className="style-row__color"
      data-testid={props.testId}
      disabled={props.disabled}
      value={draft}
      onChange={(event) => setDraft(event.target.value)}
    />
  );
}

/** Цвет токена из CSS — им заполняется выбор цвета, пока собственного нет. */
function tokenColor(variable: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(variable).trim();
  return /^#[0-9a-fA-F]{6}$/.test(value) ? value : '#000000';
}

/**
 * Собственный стиль объекта на открытом представлении — сильнее токена слоя.
 * Правка уходит в узлы (у элемента — во все его узлы здесь) или в рёбра.
 */
export function StyleEditor({
  viewId,
  nodeIds,
  edgeIds,
  name,
  readOnly,
  fallbackFill,
}: {
  viewId: Uuid;
  nodeIds: Uuid[];
  edgeIds: Uuid[];
  name: string;
  readOnly: boolean;
  fallbackFill: string;
}) {
  const view = useEditor((s) => s.doc!.loadedViews[viewId]);
  const perform = useEditor((s) => s.perform);
  const first: Style = (nodeIds[0] ? view?.nodes[nodeIds[0]]?.style : view?.edges[edgeIds[0] ?? '']?.style) ?? {};
  const font = (first.font && parseArchiFont(first.font)) || DEFAULT_FONT;
  const onNodes = nodeIds.length > 0;

  const apply = (patch: Partial<Record<keyof Style, unknown>>) => {
    const current = useEditor.getState().doc!.loadedViews[viewId]!;
    const edit = restyle(current, nodeIds, edgeIds, patch);
    perform(t('props.styleOp', { name }), edit.changes, edit.affected);
  };
  const setFont = (next: Partial<FontSpec>) => apply({ font: formatArchiFont({ ...font, ...next }) });

  const color = (field: 'fillColor' | 'fontColor' | 'lineColor', label: string, fallback: string) => (
    <div className="style-row">
      <span className="style-row__label">{label}</span>
      <ColorField
        testId={`style-${field}`}
        disabled={readOnly}
        value={first[field] ?? tokenColor(fallback)}
        onCommit={(value) => apply({ [field]: value })}
      />
      {first[field] && !readOnly && (
        <button type="button" className="btn btn--small btn--ghost" onClick={() => apply({ [field]: undefined })}>
          {t('props.styleReset')}
        </button>
      )}
    </div>
  );

  return (
    <div className="style-editor" data-testid="style-editor">
      {onNodes && color('fillColor', t('props.fillColor'), fallbackFill)}
      {color('lineColor', t('props.lineColor'), onNodes ? fallbackFill + '-border' : '--edge-color')}
      {color('fontColor', t('props.fontColor'), '--paper-text')}
      <div className="style-row">
        <span className="style-row__label">{t('props.font')}</span>
        <select
          className="style-row__select"
          data-testid="style-font-family"
          disabled={readOnly}
          value={font.family}
          onChange={(event) => setFont({ family: event.target.value })}
        >
          {[...new Set([font.family, ...FAMILIES])].map((family) => (
            <option key={family} value={family}>
              {family}
            </option>
          ))}
        </select>
        <select
          className="style-row__select style-row__select--size"
          data-testid="style-font-size"
          disabled={readOnly}
          value={font.sizePt}
          onChange={(event) => setFont({ sizePt: Number(event.target.value) })}
        >
          {[...new Set([font.sizePt, ...SIZES])].sort((a, b) => a - b).map((size) => (
            <option key={size} value={size}>
              {size}
            </option>
          ))}
        </select>
        <button
          type="button"
          className={`btn btn--small${font.bold ? ' is-on' : ''}`}
          aria-pressed={font.bold}
          title={t('props.bold')}
          data-testid="style-bold"
          disabled={readOnly}
          onClick={() => setFont({ bold: !font.bold })}
        >
          {t('props.boldShort')}
        </button>
        <button
          type="button"
          className={`btn btn--small${font.italic ? ' is-on' : ''}`}
          aria-pressed={font.italic}
          title={t('props.italic')}
          disabled={readOnly}
          onClick={() => setFont({ italic: !font.italic })}
        >
          {t('props.italicShort')}
        </button>
      </div>
      {onNodes && (
        <div className="style-row">
          <span className="style-row__label">{t('props.alignment')}</span>
          {ALIGNMENTS.map((a) => (
            <button
              key={a.value}
              type="button"
              className={`btn btn--small${(first.textAlignment ?? 2) === a.value ? ' is-on' : ''}`}
              disabled={readOnly}
              onClick={() => apply({ textAlignment: a.value })}
            >
              {t(a.label)}
            </button>
          ))}
        </div>
      )}
      {!readOnly && Object.keys(first).length > 0 && (
        <button type="button" className="btn btn--small btn--ghost" data-testid="style-reset-all" onClick={() => apply({ fillColor: undefined, fontColor: undefined, lineColor: undefined, font: undefined, textAlignment: undefined })}>
          {t('props.styleResetAll')}
        </button>
      )}
    </div>
  );
}
