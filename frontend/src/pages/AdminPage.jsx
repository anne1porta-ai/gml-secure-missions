import { useState } from 'react';
import AuditLogPanel from '../components/AuditLogPanel';
import UsersPanel from '../components/UsersPanel';

const TABS = [
  { id: 'users', label: 'Comptes' },
  { id: 'audit', label: "Journal d'audit" },
];

/** Espace administrateur : gestion des comptes et consultation du journal d'audit. */
export default function AdminPage() {
  const [activeTab, setActiveTab] = useState('users');

  return (
    <div className="admin-page">
      <div className="tabs" role="tablist" aria-label="Espace administrateur">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            type="button"
            role="tab"
            id={`tab-${tab.id}`}
            aria-selected={activeTab === tab.id}
            aria-controls={`panel-${tab.id}`}
            className={activeTab === tab.id ? 'tabs__tab tabs__tab--active' : 'tabs__tab'}
            onClick={() => setActiveTab(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div role="tabpanel" id={`panel-${activeTab}`} aria-labelledby={`tab-${activeTab}`}>
        {activeTab === 'users' ? <UsersPanel /> : <AuditLogPanel />}
      </div>
    </div>
  );
}
