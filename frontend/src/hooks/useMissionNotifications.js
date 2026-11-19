import { useCallback, useEffect, useRef, useState } from 'react';

const BACKEND_URL = import.meta.env?.VITE_API_URL ?? '';
const RECONNECT_DELAY_MS = 1500;
const TOAST_DURATION_MS = 4000;

function clearTimer(timerRef) {
  if (timerRef.current) {
    clearTimeout(timerRef.current);
    timerRef.current = null;
  }
}

export function createMissionNotificationConnection({
  refreshMissions,
  onConnected,
  onDisconnected,
  onNotification,
  backendUrl = BACKEND_URL,
  eventSourceFactory = (url, options) => new EventSource(url, options),
  schedule = (callback, delay) => setTimeout(callback, delay),
  cancelSchedule = (timer) => clearTimeout(timer),
  reconnectDelayMs = RECONNECT_DELAY_MS,
  logError = (...args) => console.error(...args),
}) {
  let stopped = false;
  let eventSource = null;
  let reconnectTimer = null;

  function closeEventSource() {
    if (eventSource) {
      eventSource.close();
      eventSource = null;
    }
  }

  function cancelReconnect() {
    if (reconnectTimer) {
      cancelSchedule(reconnectTimer);
      reconnectTimer = null;
    }
  }

  function scheduleReconnect() {
    if (stopped) {
      return;
    }

    cancelReconnect();
    reconnectTimer = schedule(connect, reconnectDelayMs);
  }

  // SSE signale seulement qu'une mission a changé : la liste REST reste la
  // source de vérité, notamment si des événements ont été perdus hors connexion.
  function handleMissionEvent(event) {
    const notification = JSON.parse(event.data);
    onNotification(notification);

    refreshMissions().catch((error) => {
      logError('Impossible de recharger les missions', error);
    });
  }

  async function connect() {
    try {
      // Chaque ouverture ou reconnexion commence par REST afin de récupérer l'état
      // réellement persisté avant de reprendre les événements temps réel.
      await refreshMissions();

      if (stopped) {
        return;
      }

      closeEventSource();

      // Le flux utilise le cookie de session ; aucun JWT n'est placé dans l'URL.
      // Les actions du chauffeur continuent de passer par les appels REST.
      eventSource = eventSourceFactory(`${backendUrl}/api/notifications/stream`, {
        withCredentials: true,
      });

      eventSource.onopen = () => {
        onConnected();
      };

      // Une rupture ferme le flux précédent avant toute reconnexion,
      // pour éviter de dupliquer les abonnements et les notifications.
      eventSource.onerror = () => {
        onDisconnected();
        closeEventSource();
        scheduleReconnect();
      };

      // Chaque changement recharge la liste depuis l'API, sans rafraîchir la page.
      eventSource.addEventListener('NEW_MISSION', handleMissionEvent);
      eventSource.addEventListener('MISSION_UPDATED', handleMissionEvent);
      eventSource.addEventListener('MISSION_CANCELLED', handleMissionEvent);
      eventSource.addEventListener('MISSION_REASSIGNED', handleMissionEvent);
    } catch (error) {
      onDisconnected();

      if (stopped) {
        return;
      }

      logError("Impossible d'ouvrir les notifications", error);
      scheduleReconnect();
    }
  }

  function disconnect() {
    stopped = true;
    cancelReconnect();
    closeEventSource();
    onDisconnected();
  }

  connect();

  return { disconnect };
}

export default function useMissionNotifications(enabled, refreshMissions) {
  const [connected, setConnected] = useState(false);
  const [toast, setToast] = useState(null);

  const connectionRef = useRef(null);
  const toastTimerRef = useRef(null);

  const disconnect = useCallback(() => {
    connectionRef.current?.disconnect();
    connectionRef.current = null;
    clearTimer(toastTimerRef);
    setConnected(false);
  }, []);

  useEffect(() => {
    if (!enabled) {
      disconnect();
      return undefined;
    }

    const connection = createMissionNotificationConnection({
      refreshMissions,
      onConnected: () => setConnected(true),
      onDisconnected: () => setConnected(false),
      onNotification: (notification) => {
        setToast(notification);
        clearTimer(toastTimerRef);

        toastTimerRef.current = setTimeout(() => {
          setToast(null);
        }, TOAST_DURATION_MS);
      },
    });

    connectionRef.current = connection;

    return () => {
      if (connectionRef.current === connection) {
        connectionRef.current = null;
      }

      connection.disconnect();
      clearTimer(toastTimerRef);
    };
  }, [disconnect, enabled, refreshMissions]);

  return {
    connected,
    toast,
    disconnect,
  };
}
