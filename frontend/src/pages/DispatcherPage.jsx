import { useEffect, useRef, useState } from 'react';
import apiClient from '../api/apiClient';
import MissionList from '../components/MissionList';
import EditMissionForm from '../components/EditMissionForm';
import ReassignMissionForm from '../components/ReassignMissionForm';
import { getErrorMessage } from '../utils/errorMessage';
import { buildScheduledAt } from '../utils/missionDetails';

export default function DispatcherPage({ missions, onMissionCreated }) {
  const [title, setTitle] = useState('');
  const [destination, setDestination] = useState('');
  const [secretCode, setSecretCode] = useState('');
  const [description, setDescription] = useState('');
  const [scheduledDate, setScheduledDate] = useState('');
  const [scheduledTime, setScheduledTime] = useState('');
  const scheduledDateRef = useRef(null);
  const scheduledTimeRef = useRef(null);
  const [drivers, setDrivers] = useState([]);
  const [driversError, setDriversError] = useState(null);
  const [driverEmail, setDriverEmail] = useState('');
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [editingMission, setEditingMission] = useState(null);
  const [reassigningMission, setReassigningMission] = useState(null);
  const [missionToCancel, setMissionToCancel] = useState(null);
  const [cancelError, setCancelError] = useState(null);
  const [cancelling, setCancelling] = useState(false);

  const cancelMission = async () => {
    setCancelError(null);
    setCancelling(true);

    try {
      await apiClient.patch(`/api/missions/${missionToCancel.id}/cancel`);
      setMissionToCancel(null);
      await onMissionCreated().catch(() => {});
    } catch (requestError) {
      setCancelError(getErrorMessage(requestError, "Impossible d'annuler la mission."));
    } finally {
      setCancelling(false);
    }
  };

  const assignMission = async (missionId, email) => {
    await apiClient.patch(`/api/missions/${missionId}/driver`, { driverEmail: email });
    setReassigningMission(null);
    await onMissionCreated().catch(() => {});
  };

  const saveMission = async (missionId, changes) => {
    await apiClient.patch(`/api/missions/${missionId}`, changes);
    setEditingMission(null);
    await onMissionCreated().catch(() => {});
  };

  // Le dispatcher choisit parmi les chauffeurs actifs plutôt que de saisir un e-mail.
  useEffect(() => {
    apiClient
      .get('/api/missions/assignable-drivers')
      .then((response) => {
        setDrivers(response.data);
        setDriverEmail((currentEmail) => currentEmail || response.data[0]?.email || '');
      })
      .catch((error) => {
        setDriversError(getErrorMessage(error, 'Impossible de charger la liste des chauffeurs.'));
      });
  }, []);

  const createMission = async (event) => {
    event.preventDefault();
    setError(null);
    setSuccessMessage(null);

    if (!title.trim() || !destination.trim() || !secretCode.trim() || !driverEmail.trim()) {
      setError("Le titre, la destination, le code d'accès et le chauffeur sont obligatoires.");
      return;
    }

    // Une saisie partielle (jour sans année, heure sans minutes) laisse la valeur vide :
    // on la détecte pour l'expliquer en français plutôt que de l'ignorer.
    const scheduleInputs = [scheduledDateRef.current, scheduledTimeRef.current];

    if (scheduleInputs.some((input) => input?.validity.badInput)) {
      setError("La date ou l'heure prévue est incomplète.");
      return;
    }

    const scheduledAt = buildScheduledAt(scheduledDate, scheduledTime);

    if (scheduledAt.error) {
      setError(scheduledAt.error);
      return;
    }

    setSubmitting(true);

    try {
      await apiClient.post('/api/missions', {
        title,
        destination,
        secretCode,
        driverEmail,
        description: description.trim() || null,
        scheduledAt: scheduledAt.value,
      });
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'La création de la mission a échoué.'));
      return;
    } finally {
      setSubmitting(false);
    }

    setTitle('');
    setDestination('');
    setSecretCode('');
    setDescription('');
    setScheduledDate('');
    setScheduledTime('');
    setSuccessMessage('Mission créée : le chauffeur a été notifié.');

    // Une erreur de rechargement de la liste est déjà affichée par le tableau de bord.
    await onMissionCreated().catch(() => {});
  };

  return (
    <div className="dispatcher-layout">
      <section className="card" aria-labelledby="new-mission-title">
        <h2 id="new-mission-title" className="card__title">
          Nouvelle mission
        </h2>

        {/* noValidate : les contrôles sont faits ci-dessus, avec des messages en français. */}
        <form className="form" onSubmit={createMission} noValidate>
          <div className="field">
            <label htmlFor="mission-title">Titre</label>
            <input
              id="mission-title"
              required
              maxLength={150}
              placeholder="Ex. : Livraison de palettes"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </div>

          <div className="field">
            <label htmlFor="mission-destination">Destination</label>
            <input
              id="mission-destination"
              required
              maxLength={500}
              placeholder="Ex. : Entrepôt Lyon Sud"
              value={destination}
              onChange={(e) => setDestination(e.target.value)}
            />
          </div>

          <div className="field">
            <label htmlFor="mission-description">Description (facultatif)</label>
            <textarea
              id="mission-description"
              rows={3}
              maxLength={1000}
              placeholder="Ex. : quai 4, sonner à l'accueil"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>

          <div className="field">
            <label htmlFor="mission-scheduled-date">Date et heure prévues (facultatif)</label>
            <div className="field__row">
              <input
                id="mission-scheduled-date"
                ref={scheduledDateRef}
                type="date"
                value={scheduledDate}
                onChange={(e) => setScheduledDate(e.target.value)}
              />
              <input
                ref={scheduledTimeRef}
                type="time"
                aria-label="Heure prévue"
                value={scheduledTime}
                onChange={(e) => setScheduledTime(e.target.value)}
              />
            </div>
            <p className="field__hint">Remplir les deux, ou laisser les deux vides.</p>
          </div>

          <div className="field">
            <label htmlFor="mission-secret">Code d'accès entrepôt</label>
            <input
              id="mission-secret"
              required
              maxLength={200}
              autoComplete="off"
              value={secretCode}
              onChange={(e) => setSecretCode(e.target.value)}
            />
            <p className="field__hint">
              Chiffré avant enregistrement, visible uniquement par le chauffeur assigné.
            </p>
          </div>

          <div className="field">
            <label htmlFor="mission-driver">Chauffeur assigné</label>
            <select
              id="mission-driver"
              required
              value={driverEmail}
              onChange={(e) => setDriverEmail(e.target.value)}
            >
              {drivers.length === 0 && <option value="">Aucun chauffeur actif</option>}
              {drivers.map((driver) => (
                <option key={driver.email} value={driver.email}>
                  {driver.email}
                </option>
              ))}
            </select>
            {driversError && (
              <p className="alert alert--error" role="alert">
                {driversError}
              </p>
            )}
          </div>

          {error && (
            <p className="alert alert--error" role="alert">
              {error}
            </p>
          )}

          {successMessage && (
            <p className="alert alert--success" role="status">
              {successMessage}
            </p>
          )}

          <button
            type="submit"
            className="button button--primary button--block"
            disabled={submitting}
          >
            {submitting ? 'Création…' : 'Créer et notifier le chauffeur'}
          </button>
        </form>
      </section>

      {editingMission && (
        <EditMissionForm
          key={editingMission.id}
          mission={editingMission}
          onSave={saveMission}
          onClose={() => setEditingMission(null)}
        />
      )}

      {reassigningMission && (
        <ReassignMissionForm
          key={reassigningMission.id}
          mission={reassigningMission}
          drivers={drivers}
          onAssign={assignMission}
          onClose={() => setReassigningMission(null)}
        />
      )}

      {missionToCancel && (
        <section className="card" aria-labelledby="cancel-mission-title">
          <h2 id="cancel-mission-title" className="card__title">
            Annuler la mission
          </h2>
          <p>Confirmer l'annulation de « {missionToCancel.title} » ?</p>
          {cancelError && (
            <p className="alert alert--error" role="alert">
              {cancelError}
            </p>
          )}
          <div className="mission-card__actions">
            <button
              type="button"
              className="button button--primary"
              onClick={cancelMission}
              disabled={cancelling}
            >
              {cancelling ? 'Annulation…' : "Confirmer l'annulation"}
            </button>
            <button
              type="button"
              className="button button--ghost"
              onClick={() => setMissionToCancel(null)}
            >
              Fermer
            </button>
          </div>
        </section>
      )}

      <section aria-labelledby="dispatcher-missions-title">
        <div className="section-heading">
          <h2 id="dispatcher-missions-title">Mes missions créées</h2>
          <span className="count-badge">{missions.length}</span>
        </div>
        <MissionList
          missions={missions}
          role="DISPATCHER"
          onEdit={setEditingMission}
          onReassign={setReassigningMission}
          onCancel={setMissionToCancel}
        />
      </section>
    </div>
  );
}
