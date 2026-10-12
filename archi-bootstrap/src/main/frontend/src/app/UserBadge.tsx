import type { Me } from '../api/types';
import { t } from '../i18n';

function initials(name: string): string {
  return name
    .split(/[\s._-]+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join('');
}

export function UserBadge({ me, onSignOut }: { me?: Me; onSignOut?: () => void }) {
  if (!me) {
    return null;
  }
  const role = me.roles.includes('ADMIN') ? 'ADMIN' : me.roles.includes('ARCHITECT') ? 'ARCHITECT' : 'VIEWER';
  return (
    <div className="row">
      <span className="role-chip">{t(`roles.${role}`)}</span>
      <span className="avatar" title={me.displayName}>
        {initials(me.displayName)}
      </span>
      {onSignOut && (
        <button type="button" className="btn btn--small" onClick={onSignOut}>
          {t('models.signOut')}
        </button>
      )}
    </div>
  );
}
