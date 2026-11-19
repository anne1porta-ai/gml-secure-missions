import test, { after, before } from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';

import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

let viteServer;
let MissionList;

before(async () => {
  const frontendRoot = fileURLToPath(new URL('..', import.meta.url));
  viteServer = await createServer({
    root: frontendRoot,
    server: { middlewareMode: true, hmr: false },
    appType: 'custom',
  });

  const module = await viteServer.ssrLoadModule('/src/components/MissionList.jsx');
  MissionList = module.default;
});

after(async () => {
  await viteServer.close();
});

test('driver sees a hidden secret and the reveal action while the mission is in progress', () => {
  const html = render('DRIVER', mission('IN_PROGRESS'), () => {});

  assert.match(html, /Code secret : ••••••••/);
  assert.match(html, /Révéler/);
});

test('a pending mission explains when the secret becomes available', () => {
  const html = render('DRIVER', mission('PENDING'), () => {});

  assert.match(html, /Code secret disponible une fois la mission démarrée/);
  assert.doesNotMatch(html, /Révéler/);
});

test('dispatcher never receives the secret display controls', () => {
  const html = render('DISPATCHER', mission('PENDING'));

  assert.doesNotMatch(html, /Code secret/);
  assert.doesNotMatch(html, /Révéler/);
});

test('dispatcher can edit a pending mission', () => {
  const html = renderToStaticMarkup(
    React.createElement(MissionList, {
      missions: [mission('PENDING')],
      role: 'DISPATCHER',
      onEdit: () => {},
    }),
  );

  assert.match(html, /Modifier/);
});

test('dispatcher can reassign a pending mission', () => {
  const html = renderToStaticMarkup(
    React.createElement(MissionList, {
      missions: [mission('PENDING')],
      role: 'DISPATCHER',
      onReassign: () => {},
    }),
  );
  assert.match(html, /Changer de chauffeur/);
});

test('dispatcher cannot reassign a mission after departure', () => {
  const html = renderToStaticMarkup(
    React.createElement(MissionList, {
      missions: [mission('IN_PROGRESS')],
      role: 'DISPATCHER',
      onReassign: () => {},
    }),
  );
  assert.doesNotMatch(html, /Changer de chauffeur/);
});

test('dispatcher can cancel a mission in progress', () => {
  const html = renderToStaticMarkup(
    React.createElement(MissionList, {
      missions: [mission('IN_PROGRESS')],
      role: 'DISPATCHER',
      onCancel: () => {},
    }),
  );
  assert.match(html, /Annuler la mission/);
});

test('a completed mission cannot be cancelled', () => {
  const html = renderToStaticMarkup(
    React.createElement(MissionList, {
      missions: [mission('COMPLETED')],
      role: 'DISPATCHER',
      onCancel: () => {},
    }),
  );
  assert.doesNotMatch(html, /Annuler la mission/);
});

test('dispatcher cannot edit a mission after departure', () => {
  const html = renderToStaticMarkup(
    React.createElement(MissionList, {
      missions: [mission('IN_PROGRESS')],
      role: 'DISPATCHER',
      onEdit: () => {},
    }),
  );

  assert.doesNotMatch(html, />Modifier</);
});

test('completed mission no longer exposes a status advance action nor the secret', () => {
  const html = render('DRIVER', mission('COMPLETED'), () => {});

  assert.doesNotMatch(html, /Passer à/);
  assert.doesNotMatch(html, /Code secret/);
});

test('driver sees the French status and the next lifecycle step', () => {
  const html = render('DRIVER', mission('PENDING'), () => {});

  assert.match(html, /En attente/);
  assert.match(html, /Passer à « En cours »/);
});

test('driver sees that a delivery proof is required', () => {
  const html = render('DRIVER', mission('IN_PROGRESS'), () => {});

  assert.match(html, /Une preuve de livraison est nécessaire pour terminer la mission/);
});

test('a pending mission does not allow uploading a document', () => {
  const html = render('DRIVER', mission('PENDING'), () => {});

  assert.match(html, /Le dépôt de preuve est disponible après le démarrage de la mission/);
  assert.doesNotMatch(html, /type="file"/);
});

test('an in-progress mission allows uploading a document', () => {
  const html = render('DRIVER', mission('IN_PROGRESS'), () => {});

  assert.match(html, /type="file"/);
  assert.match(html, /Envoyer la preuve de livraison/);
});

test('a completed mission keeps the document list but blocks new uploads', () => {
  const html = render('DRIVER', mission('COMPLETED'), () => {});

  assert.match(html, /Afficher les documents/);
  assert.match(html, /aucun nouveau document ne peut être déposé/);
  assert.doesNotMatch(html, /type="file"/);
});

test('the status button is disabled while the mission is being updated', () => {
  const html = renderToStaticMarkup(
    React.createElement(MissionList, {
      missions: [mission('IN_PROGRESS')],
      role: 'DRIVER',
      onAdvance: () => {},
      updatingMissionId: 42,
    }),
  );

  assert.match(html, /disabled=""[^>]*>Mise à jour…/);
});

test('a mission title containing a script is displayed as text', () => {
  const html = render('DISPATCHER', {
    ...mission('PENDING'),
    title: '<script>alert(1)</script>',
  });

  assert.match(html, /&lt;script&gt;alert\(1\)&lt;\/script&gt;/);
  assert.doesNotMatch(html, /<script>/);
});

test('optional details are shown on the mission card', () => {
  const html = render('DISPATCHER', {
    ...mission('PENDING'),
    description: 'Quai 4',
    scheduledAt: '2026-11-02T08:30:00',
  });

  assert.match(html, /Quai 4/);
  assert.match(html, /Prévue le/);
});

function render(role, currentMission, onAdvance) {
  return renderToStaticMarkup(
    React.createElement(MissionList, {
      missions: [currentMission],
      role,
      onAdvance,
      onRevealSecret: async () => 'QX-2085',
    }),
  );
}

function mission(status) {
  return {
    id: 42,
    title: 'Livraison',
    destination: 'Lyon',
    status,
  };
}
