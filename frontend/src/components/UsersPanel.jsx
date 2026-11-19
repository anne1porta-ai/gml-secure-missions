import { useCallback, useEffect, useState } from 'react';
import apiClient from '../api/apiClient';
import { getErrorMessage } from '../utils/errorMessage';
import { generateTemporaryPassword } from '../utils/password';

const ROLE_LABELS = {
  ADMIN: 'Administrateur',
  DISPATCHER: 'Dispatcher',
  DRIVER: 'Chauffeur',
};

/**
 * Gestion des comptes Dispatcher et Chauffeur. Les règles (rôle ADMIN, e-mail unique,
 * compte administrateur non désactivable) restent appliquées par l'API.
 */
export default function UsersPanel() {
  const [users, setUsers] = useState([]);
  const [listError, setListError] = useState(null);
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('DRIVER');
  const [temporaryPassword, setTemporaryPassword] = useState('');
  const [formError, setFormError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [updatingUserId, setUpdatingUserId] = useState(null);

  const loadUsers = useCallback(async () => {
    try {
      const response = await apiClient.get('/api/users');
      setUsers(response.data);
      setListError(null);
    } catch (error) {
      setListError(getErrorMessage(error, 'Impossible de charger les comptes.'));
    }
  }, []);

  useEffect(() => {
    loadUsers();
  }, [loadUsers]);

  const createUser = async (event) => {
    event.preventDefault();
    setFormError(null);
    setSuccessMessage(null);
    setSubmitting(true);

    try {
      await apiClient.post('/api/users', { email, role, temporaryPassword });
    } catch (error) {
      setFormError(getErrorMessage(error, 'La création du compte a échoué.'));
      return;
    } finally {
      setSubmitting(false);
    }

    setSuccessMessage(
      `Compte créé pour ${email}. Transmettez-lui son mot de passe temporaire par un canal sûr.`,
    );
    setEmail('');
    setTemporaryPassword('');
    await loadUsers();
  };

  const toggleActive = async (user) => {
    if (user.active && !window.confirm(`Désactiver le compte ${user.email} ?`)) {
      return;
    }

    setListError(null);
    setUpdatingUserId(user.id);

    try {
      await apiClient.patch(`/api/users/${user.id}/active`, { active: !user.active });
    } catch (error) {
      setListError(getErrorMessage(error, 'Le changement de statut a échoué.'));
      return;
    } finally {
      setUpdatingUserId(null);
    }

    await loadUsers();
  };

  return (
    <div className="admin-layout">
      <section className="card" aria-labelledby="new-user-title">
        <h2 id="new-user-title" className="card__title">
          Nouveau compte
        </h2>

        <form className="form" onSubmit={createUser}>
          <div className="field">
            <label htmlFor="user-email">Adresse e-mail</label>
            <input
              id="user-email"
              type="email"
              required
              maxLength={254}
              autoComplete="off"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          <div className="field">
            <label htmlFor="user-role">Rôle</label>
            <select id="user-role" value={role} onChange={(e) => setRole(e.target.value)}>
              <option value="DRIVER">Chauffeur</option>
              <option value="DISPATCHER">Dispatcher</option>
            </select>
          </div>

          <div className="field">
            <label htmlFor="user-password">Mot de passe temporaire</label>
            <div className="field__row">
              <input
                id="user-password"
                className="field__mono"
                required
                minLength={12}
                maxLength={64}
                autoComplete="off"
                value={temporaryPassword}
                onChange={(e) => setTemporaryPassword(e.target.value)}
              />
              <button
                type="button"
                className="button button--secondary"
                onClick={() => setTemporaryPassword(generateTemporaryPassword())}
              >
                Générer
              </button>
            </div>
            <p className="field__hint">
              12 caractères minimum, à transmettre au collaborateur par un canal sûr.
            </p>
          </div>

          {formError && (
            <p className="alert alert--error" role="alert">
              {formError}
            </p>
          )}

          {successMessage && (
            <p className="alert alert--success" role="status">
              {successMessage}
            </p>
          )}

          <button
            type="submit"
            className="button button--primary button--block"
            disabled={submitting}
          >
            {submitting ? 'Création…' : 'Créer le compte'}
          </button>
        </form>
      </section>

      <section aria-labelledby="users-title">
        <div className="section-heading">
          <h2 id="users-title">Comptes</h2>
          <span className="count-badge">{users.length}</span>
        </div>

        {listError && (
          <p className="alert alert--error" role="alert">
            {listError}
          </p>
        )}

        <UsersTable users={users} onToggleActive={toggleActive} updatingUserId={updatingUserId} />
      </section>
    </div>
  );
}

export function UsersTable({ users, onToggleActive, updatingUserId = null }) {
  if (users.length === 0) {
    return <p className="empty-state">Aucun compte.</p>;
  }

  return (
    <div className="table-wrapper">
      <table className="data-table">
        <thead>
          <tr>
            <th scope="col">E-mail</th>
            <th scope="col">Rôle</th>
            <th scope="col">Statut</th>
            <th scope="col">
              <span className="visually-hidden">Action</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {users.map((user) => (
            <tr key={user.id}>
              <td className="data-table__strong">{user.email}</td>
              <td>{ROLE_LABELS[user.role] ?? user.role}</td>
              <td>
                <span
                  className={user.active ? 'account-badge account-badge--active' : 'account-badge'}
                >
                  {user.active ? 'Actif' : 'Désactivé'}
                </span>
              </td>
              <td className="data-table__action">
                {user.role === 'ADMIN' ? (
                  <span className="muted-text">Non modifiable</span>
                ) : (
                  <button
                    type="button"
                    className={
                      user.active
                        ? 'button button--secondary button--small'
                        : 'button button--primary button--small'
                    }
                    onClick={() => onToggleActive(user)}
                    disabled={updatingUserId === user.id}
                  >
                    {user.active ? 'Désactiver' : 'Réactiver'}
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
