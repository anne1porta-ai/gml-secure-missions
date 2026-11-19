import { useState } from 'react';
import apiClient from '../api/apiClient';
import MissionList from '../components/MissionList';
import { getErrorMessage } from '../utils/errorMessage';
import { getNextMissionStatus } from '../utils/missionStatus';

export default function DriverPage({ missions, onStatusChanged }) {
  const [error, setError] = useState(null);
  const [updatingMissionId, setUpdatingMissionId] = useState(null);

  const advanceStatus = async (mission) => {
    const nextStatus = getNextMissionStatus(mission.status);

    if (nextStatus === null) {
      return;
    }

    setError(null);
    setUpdatingMissionId(mission.id);

    try {
      await apiClient.patch(`/api/missions/${mission.id}/status`, {
        status: nextStatus,
      });
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Le changement de statut a échoué.'));
      return;
    } finally {
      setUpdatingMissionId(null);
    }

    // Une erreur de rechargement de la liste est déjà affichée par le tableau de bord.
    await onStatusChanged().catch(() => {});
  };

  // RG2 : le code d'accès est demandé à l'API au moment où le chauffeur en a besoin.
  const revealSecret = async (mission) => {
    const response = await apiClient.get(`/api/missions/${mission.id}/secret-code`);
    return response.data.secretCode;
  };

  return (
    <section aria-labelledby="driver-missions-title">
      <div className="section-heading">
        <h2 id="driver-missions-title">Mes missions assignées</h2>
        <span className="count-badge">{missions.length}</span>
      </div>

      {error && (
        <p className="alert alert--error" role="alert">
          {error}
        </p>
      )}

      <MissionList
        missions={missions}
        onAdvance={advanceStatus}
        onRevealSecret={revealSecret}
        role="DRIVER"
        updatingMissionId={updatingMissionId}
      />
    </section>
  );
}
