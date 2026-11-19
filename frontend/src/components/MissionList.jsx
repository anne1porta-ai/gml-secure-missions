import { useEffect, useRef, useState } from 'react';
import DocumentsSection from './DocumentsSection';
import StatusBadge from './StatusBadge';
import { getErrorMessage } from '../utils/errorMessage';
import { formatScheduledAt } from '../utils/missionDetails';
import { getMissionStatusLabel, getNextMissionStatus } from '../utils/missionStatus';

const MASKED_SECRET = '••••••••';
const SECRET_VISIBLE_MS = 30_000;

export default function MissionList({
  missions,
  onAdvance,
  onRevealSecret,
  onEdit,
  onReassign,
  onCancel,
  role,
  updatingMissionId = null,
}) {
  // Codes révélés à la demande : gardés en mémoire le temps de l'affichage seulement,
  // jamais dans le stockage du navigateur.
  const [revealedSecrets, setRevealedSecrets] = useState(() => new Map());
  const secretTimers = useRef(new Map());
  const [loadingSecretId, setLoadingSecretId] = useState(null);
  const [secretError, setSecretError] = useState(null);

  // Les minuteries sont annulées lorsque le composant disparaît.
  useEffect(() => {
    const timers = secretTimers.current;

    return () => {
      for (const timer of timers.values()) {
        window.clearTimeout(timer);
      }
    };
  }, []);

  // Une mission terminée ou en attente ne doit plus afficher de code.
  useEffect(() => {
    const inProgressIds = new Set(
      missions.filter((mission) => mission.status === 'IN_PROGRESS').map((mission) => mission.id),
    );

    for (const [missionId, timer] of secretTimers.current) {
      if (!inProgressIds.has(missionId)) {
        window.clearTimeout(timer);
        secretTimers.current.delete(missionId);
      }
    }

    setRevealedSecrets((currentSecrets) => {
      const keptSecrets = new Map(
        [...currentSecrets].filter(([missionId]) => inProgressIds.has(missionId)),
      );

      return keptSecrets.size === currentSecrets.size ? currentSecrets : keptSecrets;
    });
  }, [missions]);

  const hideSecret = (missionId) => {
    const timer = secretTimers.current.get(missionId);
    if (timer !== undefined) {
      window.clearTimeout(timer);
      secretTimers.current.delete(missionId);
    }

    setRevealedSecrets((currentSecrets) => {
      if (!currentSecrets.has(missionId)) {
        return currentSecrets;
      }

      const updatedSecrets = new Map(currentSecrets);
      updatedSecrets.delete(missionId);
      return updatedSecrets;
    });
  };

  const toggleSecret = async (mission) => {
    if (revealedSecrets.has(mission.id)) {
      hideSecret(mission.id);
      return;
    }

    setSecretError(null);
    setLoadingSecretId(mission.id);

    try {
      // Le code n'est jamais présent dans la liste : il est demandé à l'API au clic,
      // qui vérifie le chauffeur, le statut de la mission et trace la demande.
      const secretCode = await onRevealSecret(mission);
      setRevealedSecrets((currentSecrets) => new Map(currentSecrets).set(mission.id, secretCode));

      // Ce masquage limite l'exposition à l'écran mais n'est pas une autorisation :
      // le backend décide seul qui peut demander le déchiffrement.
      // Le délai est propre à chaque révélation du code.
      const timer = window.setTimeout(() => hideSecret(mission.id), SECRET_VISIBLE_MS);
      secretTimers.current.set(mission.id, timer);
    } catch (error) {
      setSecretError({
        missionId: mission.id,
        message: getErrorMessage(error, "Impossible d'afficher le code secret."),
      });
    } finally {
      setLoadingSecretId(null);
    }
  };

  if (missions.length === 0) {
    return <p className="empty-state">Aucune mission pour l'instant.</p>;
  }

  return (
    <ul className="mission-list">
      {missions.map((mission) => {
        const isDriver = role === 'DRIVER';
        const isInProgress = mission.status === 'IN_PROGRESS';
        const isSecretRevealed = revealedSecrets.has(mission.id);
        const isSecretLoading = loadingSecretId === mission.id;

        const displayedSecret = isSecretRevealed ? revealedSecrets.get(mission.id) : MASKED_SECRET;
        const secretButtonLabel = isSecretRevealed ? 'Masquer' : 'Révéler';

        const nextStatus = getNextMissionStatus(mission.status);

        const canAdvanceMission = Boolean(onAdvance) && nextStatus !== null;
        const canRevealSecret = isDriver && isInProgress && Boolean(onRevealSecret);
        const isUpdating = updatingMissionId === mission.id;
        const scheduledLabel = formatScheduledAt(mission.scheduledAt);
        const secretButtonAriaLabel = `${secretButtonLabel} le code secret de la mission ${mission.title}`;

        return (
          <li key={mission.id} className="mission-card">
            <div className="mission-card__header">
              <h3 className="mission-card__title">{mission.title}</h3>
              <StatusBadge status={mission.status} />
            </div>

            <p className="mission-card__destination">
              <span className="mission-card__label">Destination</span>
              {mission.destination}
            </p>

            {mission.description && (
              <p className="mission-card__description">{mission.description}</p>
            )}

            {scheduledLabel && <p className="mission-card__meta">Prévue le {scheduledLabel}</p>}

            {role === 'DISPATCHER' && mission.driverEmail && (
              <p className="mission-card__meta">Chauffeur : {mission.driverEmail}</p>
            )}

            {canRevealSecret && (
              <div className="secret-box">
                <span className="secret-box__value">Code secret : {displayedSecret}</span>
                <button
                  type="button"
                  className="button button--ghost button--small"
                  onClick={() => toggleSecret(mission)}
                  disabled={isSecretLoading}
                  aria-expanded={isSecretRevealed}
                  aria-label={secretButtonAriaLabel}
                >
                  {isSecretLoading ? 'Déchiffrement…' : secretButtonLabel}
                </button>
              </div>
            )}

            {canRevealSecret && isSecretRevealed && (
              <p className="field__hint">Le code sera masqué automatiquement après 30 secondes.</p>
            )}

            {isDriver && mission.status === 'PENDING' && (
              <p className="secret-box secret-box--locked">
                Code secret disponible une fois la mission démarrée.
              </p>
            )}

            {secretError?.missionId === mission.id && (
              <p className="alert alert--error" role="alert">
                {secretError.message}
              </p>
            )}

            {canAdvanceMission && (
              <div className="mission-card__actions">
                <button
                  type="button"
                  className="button button--primary"
                  onClick={() => onAdvance(mission)}
                  disabled={isUpdating}
                >
                  {isUpdating
                    ? 'Mise à jour…'
                    : `Passer à « ${getMissionStatusLabel(nextStatus)} »`}
                </button>
              </div>
            )}

            {role === 'DISPATCHER' && mission.status === 'PENDING' && onEdit && (
              <div className="mission-card__actions">
                <button
                  type="button"
                  className="button button--secondary"
                  onClick={() => onEdit(mission)}
                >
                  Modifier
                </button>
              </div>
            )}

            {role === 'DISPATCHER' && mission.status === 'PENDING' && onReassign && (
              <div className="mission-card__actions">
                <button
                  type="button"
                  className="button button--secondary"
                  onClick={() => onReassign(mission)}
                >
                  Changer de chauffeur
                </button>
              </div>
            )}

            {role === 'DISPATCHER' &&
              (mission.status === 'PENDING' || mission.status === 'IN_PROGRESS') &&
              onCancel && (
                <div className="mission-card__actions">
                  <button
                    type="button"
                    className="button button--ghost"
                    onClick={() => onCancel(mission)}
                  >
                    Annuler la mission
                  </button>
                </div>
              )}

            <DocumentsSection missionId={mission.id} role={role} status={mission.status} />
          </li>
        );
      })}
    </ul>
  );
}
