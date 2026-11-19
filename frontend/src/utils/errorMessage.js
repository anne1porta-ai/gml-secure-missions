const NETWORK_ERROR_MESSAGE = 'Serveur injoignable. Vérifiez votre connexion puis réessayez.';

/**
 * Transforme une erreur Axios en message affichable à l'utilisateur.
 *
 * Le backend renvoie un format commun ({ error, details }) : on affiche ce message
 * quand il existe, sinon le message de repli propre à l'action en cours.
 */
export function getErrorMessage(error, fallbackMessage) {
  if (error && error.response) {
    const body = error.response.data;
    let message = fallbackMessage;

    if (body && body.error) {
      message = body.error;
    }

    if (body && Array.isArray(body.details) && body.details.length > 0) {
      return `${message} (${body.details.join(' ; ')})`;
    }

    return message;
  }

  // Requête partie sans réponse : serveur arrêté, coupure réseau ou délai dépassé.
  if (error && error.request) {
    return NETWORK_ERROR_MESSAGE;
  }

  return fallbackMessage;
}
