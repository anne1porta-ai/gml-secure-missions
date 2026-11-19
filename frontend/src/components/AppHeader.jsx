import Logo from './Logo';

const ROLE_LABELS = {
  ADMIN: 'Administrateur',
  DISPATCHER: 'Dispatcher',
  DRIVER: 'Chauffeur',
};

export default function AppHeader({ user, onLogout }) {
  const roleLabel = ROLE_LABELS[user.role] ?? user.role;

  return (
    <header className="app-header">
      <div className="app-header__inner">
        <div className="brand">
          <Logo />
          <div className="brand__text">
            <span className="brand__name">GML-SHIELD</span>
            <span className="brand__tagline">GreenMove Logistics</span>
          </div>
        </div>

        <div className="user-menu">
          <div className="user-menu__identity">
            <span className="user-menu__email" title={user.email}>
              {user.email}
            </span>
            <span className="role-badge">{roleLabel}</span>
          </div>
          <button type="button" className="button button--on-dark" onClick={onLogout}>
            Déconnexion
          </button>
        </div>
      </div>
    </header>
  );
}
