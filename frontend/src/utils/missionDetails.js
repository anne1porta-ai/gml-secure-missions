/**
 * Date planifiée d'une mission. L'API renvoie une heure locale sans fuseau
 * (ex. « 2026-11-02T08:30:00 ») : elle est affichée telle quelle.
 */
export function formatScheduledAt(value) {
  if (!value) {
    return null;
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return null;
  }

  return date.toLocaleString('fr-FR', { dateStyle: 'medium', timeStyle: 'short' });
}

/**
 * Combine la date et l'heure prévues saisies séparément. Les deux sont facultatives
 * mais vont ensemble : une seule des deux renvoie un message explicite plutôt qu'un
 * blocage silencieux du formulaire.
 */
export function buildScheduledAt(date, time) {
  if (!date && !time) {
    return { value: null, error: null };
  }

  if (!date || !time) {
    return {
      value: null,
      error: "Indiquez la date et l'heure prévues, ou laissez les deux vides.",
    };
  }

  return { value: `${date}T${time}`, error: null };
}
