import { useState } from 'react';
import Logo from '../components/Logo';
import { useAuth } from '../context/AuthContext';
import { getErrorMessage } from '../utils/errorMessage';

export default function LoginPage() {
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      await login(email, password);
    } catch (requestError) {
      // Message générique côté serveur : on ne précise jamais si l'email existe.
      setError(getErrorMessage(requestError, 'Identifiants invalides ou compte désactivé'));
      setSubmitting(false);
    }
  };

  return (
    <main className="login-screen">
      <section className="login-card" aria-labelledby="login-title">
        <div className="brand">
          <Logo size={44} />
          <div className="brand__text">
            <span className="brand__name">GML-SHIELD</span>
            <span className="brand__tagline">GreenMove Logistics</span>
          </div>
        </div>

        <div>
          <h1 id="login-title" className="login-card__title">
            Connexion
          </h1>
          <p className="login-card__intro">Espace sécurisé de gestion des missions.</p>
        </div>

        <form className="form" onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="login-email">Adresse e-mail</label>
            <input
              id="login-email"
              type="email"
              autoComplete="username"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          <div className="field">
            <label htmlFor="login-password">Mot de passe</label>
            <input
              id="login-password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          {error && (
            <p className="alert alert--error" role="alert">
              {error}
            </p>
          )}

          <button
            type="submit"
            className="button button--primary button--block"
            disabled={submitting}
          >
            {submitting ? 'Connexion…' : 'Se connecter'}
          </button>
        </form>
      </section>

      <p className="login-screen__footer">
        Accès réservé au personnel autorisé. Les actions sont journalisées.
      </p>
    </main>
  );
}
