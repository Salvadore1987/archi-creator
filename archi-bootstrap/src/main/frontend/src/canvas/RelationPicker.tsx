import { useEffect, useRef, useState } from 'react';
import type { RelationType } from '../api/types';
import { t, typeName } from '../i18n';

export interface RelationChoice {
  x: number;
  y: number;
  title: string;
  options: RelationType[];
}

/**
 * Выбор типа связи по отпусканию: только допустимые матрицей для пары, вероятный
 * выделен. Стрелки двигают выбор, Enter подтверждает, Esc и клик мимо отменяют.
 */
export function RelationPicker({
  choice,
  onPick,
  onCancel,
}: {
  choice: RelationChoice;
  onPick(option: RelationType): void;
  onCancel(): void;
}) {
  const initial = Math.max(0, choice.options.findIndex((o) => o.byDefault));
  const [active, setActive] = useState(initial);
  const list = useRef<HTMLDivElement>(null);
  useEffect(() => list.current?.focus(), []);
  useEffect(() => {
    const away = (event: PointerEvent) => {
      if (!list.current?.contains(event.target as Node)) onCancel();
    };
    window.addEventListener('pointerdown', away, true);
    return () => window.removeEventListener('pointerdown', away, true);
  }, [onCancel]);

  const onKeyDown = (event: React.KeyboardEvent) => {
    event.stopPropagation();
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setActive((i) => (i + 1) % choice.options.length);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActive((i) => (i - 1 + choice.options.length) % choice.options.length);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      onPick(choice.options[active]!);
    } else if (event.key === 'Escape') {
      event.preventDefault();
      onCancel();
    }
  };

  return (
    <div
      ref={list}
      className="relation-picker"
      data-testid="relation-picker"
      role="listbox"
      tabIndex={-1}
      style={{ left: choice.x, top: choice.y }}
      onKeyDown={onKeyDown}
    >
      <div className="relation-picker__title">{choice.title}</div>
      {choice.options.map((option, i) => (
        <button
          key={option.archiType}
          type="button"
          role="option"
          aria-selected={i === active}
          className={`relation-picker__item${i === active ? ' is-active' : ''}`}
          data-relation-type={option.archiType}
          onPointerEnter={() => setActive(i)}
          onClick={() => onPick(option)}
        >
          {typeName(option.archiType)}
          {option.byDefault && <span className="relation-picker__hint">{t('canvas.relationDefault')}</span>}
        </button>
      ))}
    </div>
  );
}
