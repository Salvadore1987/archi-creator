import { Icon } from '../app/Icon';
import { t } from '../i18n';

export function TreePanel() {
  return (
    <div className="panel-head">
      <Icon name="tree" />
      {t('tree.title')}
    </div>
  );
}
