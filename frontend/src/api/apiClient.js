import axios from 'axios';

/**
 * Client HTTP unique de l'application.
 *
 * withCredentials envoie les cookies de session : React ne lit jamais le JWT (HttpOnly).
 * Le jeton CSRF est recopié dans l'en-tête X-XSRF-TOKEN pour autoriser les écritures.
 */
const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  withXSRFToken: true,
});

let onSessionExpired = () => {};
let refreshPromise = null;

export async function refreshCsrfToken() {
  await apiClient.get('/api/auth/csrf');
}

export function setOnSessionExpired(callback) {
  onSessionExpired = callback;
}

/**
 * Renouvelle la session quand une requête reçoit 401 (JWT expiré), puis rejoue la requête.
 *
 * Les requêtes simultanées partagent un seul renouvellement, et un échec renvoie à l'écran
 * de connexion au lieu de boucler.
 */
apiClient.interceptors.response.use(
  (response) => response,

  async (error) => {
    const originalRequest = error.config;
    const isAuthEndpoint =
      originalRequest?.url?.includes('/api/auth/login') ||
      originalRequest?.url?.includes('/api/auth/refresh');

    // Après un 401, une seule rotation est partagée entre requêtes concurrentes.
    // _retry empêche qu'un échec du renouvellement déclenche une boucle infinie.
    if (error.response?.status === 401 && !originalRequest?._retry && !isAuthEndpoint) {
      originalRequest._retry = true;

      if (!refreshPromise) {
        refreshPromise = apiClient
          .post('/api/auth/refresh')
          .catch((refreshError) => {
            onSessionExpired();
            throw refreshError;
          })
          .finally(() => {
            refreshPromise = null;
          });
      }

      await refreshPromise;
      return apiClient(originalRequest);
    }

    return Promise.reject(error);
  },
);

export default apiClient;
