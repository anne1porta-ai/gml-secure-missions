import { useCallback, useEffect, useMemo, useState } from 'react';
import apiClient from '../api/apiClient';
import { filterAuditLogs, formatAuditDate, getStatusTone } from '../utils/audit';
import { getErrorMessage } from '../utils/errorMessage';

const PAGE_SIZE = 50;

/** Journal d'audit, du plus récent au plus ancien, avec filtre et affichage progressif. */
export default function AuditLogPanel() {
  const [logs, setLogs] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('');
  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE);

  const loadLogs = useCallback(async () => {
    setLoading(true);

    try {
      const response = await apiClient.get('/api/audit-logs');
      setLogs(response.data);
      setError(null);
    } catch (requestError) {
      setError(getErrorMessage(requestError, "Impossible de charger le journal d'audit."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadLogs();
  }, [loadLogs]);

  const filteredLogs = useMemo(() => filterAuditLogs(logs, filter), [logs, filter]);

  const handleFilterChange = (event) => {
    setFilter(event.target.value);
    setVisibleCount(PAGE_SIZE);
  };

  return (
    <section aria-labelledby="audit-title">
      <div className="section-heading section-heading--toolbar">
        <h2 id="audit-title">Journal d'audit</h2>
        <span className="count-badge">{filteredLogs.length}</span>
        <div className="toolbar">
          <input
            type="search"
            className="toolbar__search"
            placeholder="Filtrer : e-mail, action…"
            aria-label="Filtrer le journal d'audit"
            value={filter}
            onChange={handleFilterChange}
          />
          <button
            type="button"
            className="button button--secondary button--small"
            onClick={loadLogs}
            disabled={loading}
          >
            {loading ? 'Chargement…' : 'Actualiser'}
          </button>
        </div>
      </div>

      {error && (
        <p className="alert alert--error" role="alert">
          {error}
        </p>
      )}

      <AuditLogTable logs={filteredLogs.slice(0, visibleCount)} />

      {filteredLogs.length > visibleCount && (
        <button
          type="button"
          className="button button--ghost load-more"
          onClick={() => setVisibleCount((count) => count + PAGE_SIZE)}
        >
          Afficher plus
        </button>
      )}
    </section>
  );
}

export function AuditLogTable({ logs }) {
  if (logs.length === 0) {
    return <p className="empty-state">Aucune entrée.</p>;
  }

  return (
    <div className="table-wrapper">
      <table className="data-table">
        <thead>
          <tr>
            <th scope="col">Date</th>
            <th scope="col">Utilisateur</th>
            <th scope="col">Action</th>
            <th scope="col">Adresse IP</th>
            <th scope="col">Statut</th>
          </tr>
        </thead>
        <tbody>
          {logs.map((log) => (
            <tr key={log.id}>
              <td className="data-table__nowrap">{formatAuditDate(log.timestamp)}</td>
              <td className="data-table__strong">{log.email ?? 'Anonyme'}</td>
              <td>
                <code className="data-table__code">{log.action}</code>
              </td>
              <td className="data-table__nowrap">{log.ipAddress}</td>
              <td>
                <span className={`http-badge http-badge--${getStatusTone(log.statusCode)}`}>
                  {log.statusCode}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
