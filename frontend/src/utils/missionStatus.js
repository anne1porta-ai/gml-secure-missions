export function getNextMissionStatus(currentStatus) {
  if (currentStatus === 'PENDING') {
    return 'IN_PROGRESS';
  }

  if (currentStatus === 'IN_PROGRESS') {
    return 'COMPLETED';
  }

  return null;
}

const STATUS_LABELS = {
  PENDING: 'En attente',
  IN_PROGRESS: 'En cours',
  COMPLETED: 'Terminée',
  CANCELLED: 'Annulée',
};

/**
 * Libellé affiché à l'utilisateur. Le code technique (PENDING…) reste celui
 * de l'API ; un statut inconnu est affiché tel quel plutôt que masqué.
 */
export function getMissionStatusLabel(status) {
  return STATUS_LABELS[status] ?? status;
}
