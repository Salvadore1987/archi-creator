import sprite from '@shared-ui/icons.svg?raw';

/**
 * Спрайт иконок нотации в документе — один раз на приложение. Символы
 * обязаны существовать до первой фигуры, иначе `<use>` ссылается в пустоту.
 */
export function IconSprite() {
  return <div className="icon-sprite" aria-hidden dangerouslySetInnerHTML={{ __html: sprite }} />;
}
