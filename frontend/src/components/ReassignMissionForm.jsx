import { useState } from 'react';
import { getErrorMessage } from '../utils/errorMessage';

export default function ReassignMissionForm({ mission, drivers, onAssign, onClose }) {
  const [driverEmail, setDriverEmail] = useState(mission.driverEmail);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  const assign = async (event) => {
    event.preventDefault();
    setError(null);

    if (!driverEmail || driverEmail === mission.driverEmail) {
      setError('Choisissez un autre chauffeur actif.');
      return;
    }

    setSaving(true);
    try {
      await onAssign(mission.id, driverEmail);
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Impossible de changer de chauffeur.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="card" aria-labelledby="reassign-title">
      <h2 id="reassign-title" className="card__title">
        Réaffecter la mission
      </h2>
      <p className="field__hint">{mission.title}</p>
      <form className="form" onSubmit={assign}>
        <div className="field">
          <label htmlFor="reassign-driver">Nouveau chauffeur</label>
          <select
            id="reassign-driver"
            value={driverEmail}
            onChange={(event) => setDriverEmail(event.target.value)}
          >
            {drivers.map((driver) => (
              <option key={driver.email} value={driver.email}>
                {driver.email}
              </option>
            ))}
          </select>
        </div>
        {error && (
          <p className="alert alert--error" role="alert">
            {error}
          </p>
        )}
        <div className="mission-card__actions">
          <button
            type="submit"
            className="button button--primary"
            disabled={saving || driverEmail === mission.driverEmail}
          >
            {saving ? 'Réaffectation…' : 'Confirmer la réaffectation'}
          </button>
          <button type="button" className="button button--ghost" onClick={onClose}>
            Fermer
          </button>
        </div>
      </form>
    </section>
  );
}
