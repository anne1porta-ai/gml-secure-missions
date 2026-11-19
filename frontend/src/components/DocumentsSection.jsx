import { useRef, useState } from 'react';
import apiClient from '../api/apiClient';
import { getErrorMessage } from '../utils/errorMessage';

/**
 * RG4 : le Driver dépose la preuve ; le Dispatcher propriétaire peut la
 * télécharger. Les contrôles d'autorisation restent exclusivement côté API.
 */
export default function DocumentsSection({ missionId, role, status }) {
  const [documents, setDocuments] = useState([]);
  const [selectedFile, setSelectedFile] = useState(null);
  const [uploadError, setUploadError] = useState(null);
  const [documentsError, setDocumentsError] = useState(null);
  const [documentsLoaded, setDocumentsLoaded] = useState(false);
  const [documentsVisible, setDocumentsVisible] = useState(false);
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef(null);

  const isDriver = role === 'DRIVER';
  const isDispatcher = role === 'DISPATCHER';
  const canUpload = isDriver && status === 'IN_PROGRESS';

  const loadDocuments = async () => {
    const endpoint = `/api/missions/${missionId}/documents`;

    try {
      const response = await apiClient.get(endpoint);
      setDocuments(response.data);
      setDocumentsLoaded(true);
      setDocumentsError(null);
    } catch (error) {
      setDocumentsError(getErrorMessage(error, 'Impossible de charger les documents.'));
    }
  };

  const toggleDocuments = async () => {
    const shouldShowDocuments = !documentsVisible;
    const shouldLoadDocuments = shouldShowDocuments && !documentsLoaded;

    setDocumentsVisible(shouldShowDocuments);

    if (shouldLoadDocuments) {
      await loadDocuments();
    }
  };

  const handleFileSelected = (event) => {
    const inputFiles = event.target.files;
    const firstFile = inputFiles.length > 0 ? inputFiles[0] : null;
    setSelectedFile(firstFile);
    setUploadError(null);
  };

  const handleUpload = async () => {
    if (!canUpload || selectedFile === null) {
      return;
    }

    setUploadError(null);
    setUploading(true);

    const formData = new FormData();
    formData.append('file', selectedFile);

    const endpoint = `/api/missions/${missionId}/documents`;

    try {
      await apiClient.post(endpoint, formData);
    } catch (error) {
      setUploadError(getErrorMessage(error, "Échec de l'envoi du fichier."));
      return;
    } finally {
      setUploading(false);
    }

    setSelectedFile(null);

    // Vide le sélecteur pour pouvoir renvoyer un fichier du même nom si besoin.
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }

    setDocumentsVisible(true);
    await loadDocuments();
  };

  const handleDownload = async (document) => {
    const endpoint = `/api/missions/${missionId}/documents/${document.id}`;
    setDocumentsError(null);

    try {
      const response = await apiClient.get(endpoint, { responseType: 'blob' });

      const fileBlob = new Blob([response.data]);
      const downloadUrl = window.URL.createObjectURL(fileBlob);
      const downloadLink = window.document.createElement('a');

      downloadLink.href = downloadUrl;
      downloadLink.download = document.originalFilename;
      downloadLink.click();

      window.URL.revokeObjectURL(downloadUrl);
    } catch (error) {
      setDocumentsError(getErrorMessage(error, 'Le téléchargement a échoué.'));
    }
  };

  return (
    <div className="documents">
      <div>
        <button
          type="button"
          className="button button--ghost button--small"
          onClick={toggleDocuments}
          aria-expanded={documentsVisible}
        >
          {documentsVisible ? 'Masquer les documents' : 'Afficher les documents'}
        </button>
      </div>

      {documentsError && (
        <p className="alert alert--error" role="alert">
          {documentsError}
        </p>
      )}

      {documentsVisible && documentsLoaded && documents.length === 0 && (
        <p className="muted-text documents__empty">Aucun document</p>
      )}

      {documentsVisible && documents.length > 0 && (
        <ul className="document-list">
          {documents.map((document) => (
            <li key={document.id} className="document-list__item">
              <span className="document-list__name">📎 {document.originalFilename}</span>
              {isDispatcher && (
                <button
                  type="button"
                  className="button button--link"
                  onClick={() => handleDownload(document)}
                >
                  Télécharger
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      {isDriver && status === 'PENDING' && (
        <p className="field__hint">
          Le dépôt de preuve est disponible après le démarrage de la mission.
        </p>
      )}

      {isDriver && (status === 'COMPLETED' || status === 'CANCELLED') && (
        <p className="field__hint">
          La mission est terminée ou annulée : aucun nouveau document ne peut être déposé.
        </p>
      )}

      {canUpload && (
        <div className="upload">
          <label className="upload__picker">
            <input
              ref={fileInputRef}
              type="file"
              className="visually-hidden"
              accept="application/pdf,image/jpeg,image/png"
              onChange={handleFileSelected}
            />
            <span className="upload__picker-label">
              {selectedFile ? selectedFile.name : 'Choisir un fichier'}
            </span>
          </label>

          <button
            type="button"
            className="button button--secondary button--small"
            onClick={handleUpload}
            disabled={selectedFile === null || uploading}
          >
            {uploading ? 'Envoi…' : 'Envoyer la preuve de livraison'}
          </button>

          <p className="field__hint">PDF, JPG ou PNG, 10 Mo maximum.</p>
          <p className="field__hint">
            Une preuve de livraison est nécessaire pour terminer la mission.
          </p>

          {uploadError && (
            <p className="alert alert--error" role="alert">
              {uploadError}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
