import test, { after, before } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';

import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

let viteServer;
let UsersTable;
let AuditLogTable;

before(async () => {
  const frontendRoot = fileURLToPath(new URL('..', import.meta.url));
  viteServer = await createServer({
    root: frontendRoot,
    server: { middlewareMode: true, hmr: false },
    appType: 'custom',
  });

  ({ UsersTable } = await viteServer.ssrLoadModule('/src/components/UsersPanel.jsx'));
  ({ AuditLogTable } = await viteServer.ssrLoadModule('/src/components/AuditLogPanel.jsx'));
});

after(async () => {
  await viteServer.close();
});

const users = [
  { id: 1, email: 'admin1@gml.local', role: 'ADMIN', active: true },
  { id: 2, email: 'driver1@gml.local', role: 'DRIVER', active: true },
  { id: 3, email: 'driver2@gml.local', role: 'DRIVER', active: false },
];

test('an administrator account offers no activation control', () => {
  const html = renderToStaticMarkup(
    React.createElement(UsersTable, { users: [users[0]], onToggleActive: () => {} }),
  );

  assert.match(html, /Non modifiable/);
  assert.doesNotMatch(html, /Désactiver|Réactiver/);
});

test('active and inactive accounts get the matching action', () => {
  const html = renderToStaticMarkup(
    React.createElement(UsersTable, { users: users.slice(1), onToggleActive: () => {} }),
  );

  assert.match(html, /Désactiver/);
  assert.match(html, /Réactiver/);
  assert.match(html, /Chauffeur/);
});

test('audit entries without user are shown as anonymous with a status badge', () => {
  const html = renderToStaticMarkup(
    React.createElement(AuditLogTable, {
      logs: [
        {
          id: 9,
          email: null,
          action: 'POST /api/auth/login',
          ipAddress: '10.0.0.2',
          statusCode: 401,
          timestamp: '2026-10-28T09:12:00Z',
        },
      ],
    }),
  );

  assert.match(html, /Anonyme/);
  assert.match(html, /http-badge--warning/);
});
