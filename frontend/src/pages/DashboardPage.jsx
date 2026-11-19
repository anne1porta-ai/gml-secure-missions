import { useCallback, useEffect, useState } from 'react';
import apiClient from '../api/apiClient';
import AppHeader from '../components/AppHeader';
import { useAuth } from '../context/AuthContext';
import useMissionNotifications from '../hooks/useMissionNotifications';
import { getErrorMessage } from '../utils/errorMessage';
import AdminPage from './AdminPage';
import DispatcherPage from './DispatcherPage';
import DriverPage from './DriverPage';

const notificationTitles = {
  NEW_MISSION: 'Nouvelle mission',
  MISSION_UPDATED: 'Mission modifiée',
  MISSION_CANCELLED: 'Mission annulée',
  MISSION_REASSIGNED: 'Mission réaffectée',
};

export default function DashboardPage() {
  const { user, logout } = useAuth();
  const [missions, setMissions] = useState([]);
  const [missionsError, setMissionsError] = useState(null);
  const [logoutError, setLogoutError] = useState(null);

  const fetchMissions = useCallback(async () => {
    try {
      const response = await apiClient.get('/api/missions');
      setMissions(response.data);
      setMissionsError(null);
    } catch (error) {
      setMissionsError(getErrorMessage(error, 'Impossible de charger les missions.'));
      // L'erreur est relancée : le canal temps réel s'en sert pour programmer une reconnexion.
      throw error;
    }
  }, []);

  const isDriver = user.role === 'DRIVER';
  const isDispatcher = user.role === 'DISPATCHER';
  const isAdmin = user.role === 'ADMIN';
  const { connected, toast, disconnect } = useMissionNotifications(isDriver, fetchMissions);

  useEffect(() => {
    if (isDispatcher) {
      fetchMissions().catch((error) => {
        console.error('Impossible de charger les missions', error);
      });
    }
  }, [fetchMissions, isDispatcher]);

  const handleLogout = async () => {
    setLogoutError(null);
    disconnect();

    try {
      await logout();
    } catch (error) {
      setLogoutError(getErrorMessage(error, 'La déconnexion a échoué. Réessayez.'));
    }
  };

  return (
    <div className="app-shell">
      <AppHeader user={user} onLogout={handleLogout} />

      <main className="app-main">
        {toast && (
          <div className="toast" role="status">
            <span className="toast__icon" aria-hidden="true">
              🔔
            </span>
            <div>
              <p className="toast__title">
                {notificationTitles[toast.type] ?? 'Mission mise à jour'}
              </p>
              <p className="toast__body">
                {toast.title}
                {toast.destination ? ` → ${toast.destination}` : ''}
              </p>
            </div>
          </div>
        )}

        {logoutError && (
          <p className="alert alert--error" role="alert">
            {logoutError}
          </p>
        )}

        {missionsError && (
          <p className="alert alert--error" role="alert">
            {missionsError}
          </p>
        )}

        {isDriver && (
          <p className={connected ? 'live-status live-status--on' : 'live-status'} role="status">
            {connected ? 'Notifications en temps réel actives' : 'Connexion au canal temps réel…'}
          </p>
        )}

        {isDispatcher && <DispatcherPage missions={missions} onMissionCreated={fetchMissions} />}

        {isDriver && <DriverPage missions={missions} onStatusChanged={fetchMissions} />}

        {isAdmin && <AdminPage />}
      </main>
    </div>
  );
}
