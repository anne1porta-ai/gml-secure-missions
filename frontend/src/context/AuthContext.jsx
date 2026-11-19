import { createContext, useContext, useEffect, useState } from 'react';

import apiClient, { refreshCsrfToken, setOnSessionExpired } from '../api/apiClient';
import { broadcastAuthChange, subscribeToAuthChanges } from '../utils/authSync';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setOnSessionExpired(() => {
      setUser(null);
    });
  }, []);

  const checkSession = async () => {
    try {
      await refreshCsrfToken();
      const response = await apiClient.get('/api/auth/me');
      setUser(response.data);
    } catch {
      setUser(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    checkSession();
  }, []);

  useEffect(() => {
    // Un autre onglet a changé la session partagée (cookies communs au navigateur) :
    // on recharge pour repartir de la session réellement active.
    return subscribeToAuthChanges(() => window.location.reload());
  }, []);

  const login = async (email, password) => {
    await refreshCsrfToken();

    const response = await apiClient.post('/api/auth/login', {
      email,
      password,
    });

    // Après le login, Spring peut renouveler le token CSRF. On le relit tout de suite
    // pour éviter qu'une requête d'écriture suivante parte avec l'ancienne valeur.
    await refreshCsrfToken();
    setUser(response.data);
    broadcastAuthChange();
    return response.data;
  };

  const logout = async () => {
    await refreshCsrfToken();
    await apiClient.post('/api/auth/logout');
    setUser(null);
    broadcastAuthChange();
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, logout }}>{children}</AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
