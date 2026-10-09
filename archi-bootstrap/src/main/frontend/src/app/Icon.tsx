import sprite from './ui-icons.svg?raw';

export type IconName =
  | 'search'
  | 'undo'
  | 'redo'
  | 'lock'
  | 'eye'
  | 'chevron'
  | 'warn'
  | 'grid'
  | 'tree'
  | 'folder'
  | 'view'
  | 'relation'
  | 'plus'
  | 'minus'
  | 'close'
  | 'fit'
  | 'history'
  | 'pencil'
  | 'trash'
  | 'copy'
  | 'target'
  | 'upload'
  | 'download';

export function Icon({ name, className }: { name: IconName; className?: string }) {
  return (
    <svg className={className ?? 'icon'} aria-hidden>
      <use href={`#u-${name}`} />
    </svg>
  );
}

/** Спрайт иконок корпуса — в документе один раз. */
export function UiIconSprite() {
  return <div className="icon-sprite" aria-hidden dangerouslySetInnerHTML={{ __html: sprite }} />;
}

/** Иконка нотации из общего с сервером спрайта. */
export function NotationIcon({ spriteId, className }: { spriteId: string; className?: string }) {
  return (
    <svg className={className ?? 'icon'} aria-hidden>
      <use href={`#${spriteId}`} />
    </svg>
  );
}
