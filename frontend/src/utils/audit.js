/** Couleur du badge selon la famille du statut HTTP : succès, refus ou erreur serveur. */
export function getStatusTone(statusCode) {
  if (statusCode >= 500) {
    return 'error';
  }

  if (statusCode >= 400) {
    return 'warning';
  }

  return 'success';
}

/** Date lisible en heure locale ; l'API renvoie un instant UTC. */
export function formatAuditDate(timestamp) {
  const date = new Date(timestamp);

  if (Number.isNaN(date.getTime())) {
    return '—';
  }

  return date.toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'medium' });
}

/** Filtre insensible à la casse sur l'e-mail, l'action, l'adresse IP et le statut. */
export function filterAuditLogs(logs, query) {
  const normalizedQuery = query.trim().toLowerCase();

  if (!normalizedQuery) {
    return logs;
  }

  const filteredLogs = [];

  for (const log of logs) {
    const values = [log.email, log.action, log.ipAddress, String(log.statusCode)];

    for (const value of values) {
      if (value !== null && value !== undefined && value.toLowerCase().includes(normalizedQuery)) {
        filteredLogs.push(log);
        break;
      }
    }
  }

  return filteredLogs;
}
