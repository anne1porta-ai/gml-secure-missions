import { AuthProvider, useAuth } from './context/AuthContext';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';

export default function App() {
  return (
    <AuthProvider>
      <Root />
    </AuthProvider>
  );
}

function Root() {
  const { user, loading } = useAuth();

  if (loading) {
    return <p className="loading-message">Chargement…</p>;
  }

  return user ? <DashboardPage /> : <LoginPage />;
}
