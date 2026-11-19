/**
 * Synchronisation de la session entre les onglets du navigateur.
 *
 * Les jetons sont dans des cookies partagés par tous les onglets : une seule session
 * y est donc active. Quand un onglet se connecte ou se déconnecte, il prévient les
 * autres, qui se rechargent pour afficher la session réelle au lieu d'agir sous une
 * identité périmée.
 */
const CHANNEL_NAME = 'gml-shield-auth';
const AUTH_CHANGED = 'auth-changed';

export function createAuthSync(channel) {
  return {
    broadcastAuthChange() {
      channel?.postMessage(AUTH_CHANGED);
    },

    subscribeToAuthChanges(onChange) {
      if (!channel) {
        return () => {};
      }

      const listener = (event) => {
        if (event.data === AUTH_CHANGED) {
          onChange();
        }
      };

      channel.addEventListener('message', listener);
      return () => channel.removeEventListener('message', listener);
    },
  };
}

// Un seul canal par onglet, utilisé pour émettre et pour écouter : un BroadcastChannel
// ne reçoit pas ses propres messages, l'onglet à l'origine du changement ne se recharge
// donc pas lui-même. Le canal est créé au premier usage pour ne rien ouvrir à l'import.
let browserAuthSync = null;

function getBrowserAuthSync() {
  if (browserAuthSync === null) {
    const BroadcastChannelApi = typeof window === 'undefined' ? undefined : window.BroadcastChannel;
    const channel = BroadcastChannelApi ? new BroadcastChannelApi(CHANNEL_NAME) : null;
    browserAuthSync = createAuthSync(channel);
  }

  return browserAuthSync;
}

export function broadcastAuthChange() {
  getBrowserAuthSync().broadcastAuthChange();
}

export function subscribeToAuthChanges(onChange) {
  return getBrowserAuthSync().subscribeToAuthChanges(onChange);
}
