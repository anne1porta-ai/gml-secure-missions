import { useState } from 'react';
import { getErrorMessage } from '../utils/errorMessage';
import { buildScheduledAt } from '../utils/missionDetails';

export default function EditMissionForm({ mission, onSave, onClose }) {
  const [title, setTitle] = useState(mission.title);
  const [destination, setDestination] = useState(mission.destination);
  const [description, setDescription] = useState(mission.description || '');
  const [date, setDate] = useState(mission.scheduledAt?.slice(0, 10) || '');
  const [time, setTime] = useState(mission.scheduledAt?.slice(11, 16) || '');
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  const save = async (event) => {
    event.preventDefault();
    setError(null);

    if (!title.trim() || !destination.trim()) {
      setError('Le titre et la destination sont obligatoires.');
      return;
    }

    const schedule = buildScheduledAt(date, time);
    if (schedule.error) {
      setError(schedule.error);
      return;
    }

    setSaving(true);
    try {
      await onSave(mission.id, {
        title: title.trim(),
        destination: destination.trim(),
        description: description.trim() || null,
        scheduledAt: schedule.value,
      });
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Impossible de modifier la mission.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="card" aria-labelledby="edit-mission-title">
      <h2 id="edit-mission-title" className="card__title">
        Modifier la mission
      </h2>
      <form className="form" onSubmit={save}>
        <div className="field">
          <label htmlFor="edit-title">Titre de la mission</label>
          <input
            id="edit-title"
            required
            maxLength={150}
            value={title}
            onChange={(event) => setTitle(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="edit-destination">Destination corrigée</label>
          <input
            id="edit-destination"
            required
            maxLength={500}
            value={destination}
            onChange={(event) => setDestination(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="edit-description">Description</label>
          <textarea
            id="edit-description"
            maxLength={1000}
            rows={3}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="edit-date">Date prévue</label>
          <input
            id="edit-date"
            type="date"
            value={date}
            onChange={(event) => setDate(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="edit-time">Heure prévue</label>
          <input
            id="edit-time"
            type="time"
            value={time}
            onChange={(event) => setTime(event.target.value)}
          />
        </div>

        {error && (
          <p className="alert alert--error" role="alert">
            {error}
          </p>
        )}

        <div className="mission-card__actions">
          <button type="submit" className="button button--primary" disabled={saving}>
            {saving ? 'Enregistrement…' : 'Enregistrer les modifications'}
          </button>
          <button type="button" className="button button--ghost" onClick={onClose}>
            Fermer
          </button>
        </div>
      </form>
    </section>
  );
}
