import { test, expect } from '@playwright/test';

// Connexion à l'application temporaire lancée par la CI.
const motDePasseAdmin = process.env.E2E_ADMIN_PASSWORD;

if (!motDePasseAdmin) {
  throw new Error('Le mot de passe du compte ADMIN de test est absent.');
}

async function seConnecter(page, email, motDePasse) {
  await page.goto('/');

  const formulaireConnexion = page.getByLabel('Adresse e-mail');
  const boutonDeconnexion = page.getByRole('button', { name: 'Déconnexion' });

  // Si une session est déjà ouverte, elle est fermée avant le test.
  await expect(formulaireConnexion.or(boutonDeconnexion)).toBeVisible();
  if (await boutonDeconnexion.isVisible()) {
    await boutonDeconnexion.click();
  }

  await expect(formulaireConnexion).toBeVisible();
  await formulaireConnexion.fill(email);
  await page.getByLabel('Mot de passe').fill(motDePasse);
  await page.getByRole('button', { name: 'Se connecter' }).click();
}

test('ADMIN : créer puis désactiver un chauffeur', async ({ page }) => {
  await seConnecter(page, 'admin.e2e@example.test', motDePasseAdmin);

  await expect(page.getByRole('heading', { name: 'Nouveau compte' })).toBeVisible();

  // L'administrateur crée un compte depuis l'écran ADMIN.
  const email = 'chauffeur-test@example.test';
  await page.getByLabel('Adresse e-mail').fill(email);
  await page.getByLabel('Mot de passe temporaire').fill('MotDePasseTest123!');
  await page.getByRole('button', { name: 'Créer le compte' }).click();

  const ligne = page.getByRole('row').filter({ hasText: email });
  await expect(ligne).toContainText('Actif');

  // Il désactive ensuite ce compte.
  page.once('dialog', (dialog) => dialog.accept());
  await ligne.getByRole('button', { name: 'Désactiver' }).click();

  await expect(ligne).toContainText('Désactivé');
});

test('DISPATCHER : modifier une mission en attente', async ({ page }) => {
  await seConnecter(page, 'dispatcher1@gml.local', 'dispatch123');

  await page.getByLabel('Titre', { exact: true }).fill('Livraison à corriger');
  await page.getByLabel('Destination', { exact: true }).fill('Paris');
  await page.getByLabel("Code d'accès entrepôt").fill('SECRET-TEST');
  await page.getByLabel('Chauffeur assigné').selectOption('driver1@gml.local');
  await page.getByRole('button', { name: 'Créer et notifier le chauffeur' }).click();

  const mission = page.locator('.mission-card').filter({ hasText: 'Livraison à corriger' });
  await mission.getByRole('button', { name: 'Modifier' }).click();

  await page.getByLabel('Titre de la mission').fill('Livraison modifiée');
  await page.getByLabel('Destination corrigée').fill('Nantes');
  await page.getByRole('button', { name: 'Enregistrer les modifications' }).click();

  const updated = page.locator('.mission-card').filter({ hasText: 'Livraison modifiée' });
  await expect(updated).toContainText('Nantes');
});

test('DISPATCHER : réaffecter une mission à un autre chauffeur', async ({ page }) => {
  await seConnecter(page, 'admin.e2e@example.test', motDePasseAdmin);
  await expect(page.getByRole('heading', { name: 'Nouveau compte' })).toBeVisible();

  await page.getByLabel('Adresse e-mail').fill('chauffeur-reaffecte@example.test');
  await page.getByLabel('Mot de passe temporaire').fill('MotDePasseTest123!');
  await page.getByRole('button', { name: 'Créer le compte' }).click();

  const nouveauCompte = page.getByRole('row').filter({ hasText: 'chauffeur-reaffecte@example.test' });
  await expect(nouveauCompte).toContainText('Actif');

  await page.getByRole('button', { name: 'Déconnexion' }).click();
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible();
  await seConnecter(page, 'dispatcher1@gml.local', 'dispatch123');

  await page.getByLabel('Titre', { exact: true }).fill('Mission à réaffecter');
  await page.getByLabel('Destination', { exact: true }).fill('Bordeaux');
  await page.getByLabel("Code d'accès entrepôt").fill('CODE-AFFECTATION');
  await page.getByLabel('Chauffeur assigné').selectOption('driver1@gml.local');
  await page.getByRole('button', { name: 'Créer et notifier le chauffeur' }).click();

  const mission = page.locator('.mission-card').filter({ hasText: 'Mission à réaffecter' });
  await mission.getByRole('button', { name: 'Changer de chauffeur' }).click();
  await page.getByLabel('Nouveau chauffeur').selectOption('chauffeur-reaffecte@example.test');
  await page.getByRole('button', { name: 'Confirmer la réaffectation' }).click();

  await expect(mission).toContainText('chauffeur-reaffecte@example.test');
  await page.getByRole('button', { name: 'Déconnexion' }).click();
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible();
  await seConnecter(page, 'chauffeur-reaffecte@example.test', 'MotDePasseTest123!');
  await expect(page.locator('.mission-card').filter({ hasText: 'Mission à réaffecter' }))
    .toBeVisible();
});

test('DISPATCHER : annuler une mission en attente', async ({ page }) => {
  await seConnecter(page, 'dispatcher1@gml.local', 'dispatch123');

  await page.getByLabel('Titre', { exact: true }).fill('Livraison à annuler');
  await page.getByLabel('Destination', { exact: true }).fill('Lille');
  await page.getByLabel("Code d'accès entrepôt").fill('CODE-ANNULE');
  await page.getByLabel('Chauffeur assigné').selectOption('driver1@gml.local');
  await page.getByRole('button', { name: 'Créer et notifier le chauffeur' }).click();

  const mission = page.locator('.mission-card').filter({ hasText: 'Livraison à annuler' });
  await mission.getByRole('button', { name: 'Annuler la mission' }).click();
  await page.getByRole('button', { name: "Confirmer l'annulation" }).click();

  await expect(mission.locator('.status-badge')).toHaveText('Annulée');
  await expect(mission.getByRole('button', { name: 'Annuler la mission' })).toHaveCount(0);

  await page.getByRole('button', { name: 'Déconnexion' }).click();
  await seConnecter(page, 'driver1@gml.local', 'driver123');

  const cancelled = page.locator('.mission-card').filter({ hasText: 'Livraison à annuler' });
  await expect(cancelled.locator('.status-badge')).toHaveText('Annulée');
  await expect(cancelled.locator('input[type="file"]')).toHaveCount(0);
});

test('ANNULATION : le chauffeur est averti sans actualiser sa page', async ({ browser, baseURL }) => {
  // Deux navigateurs indépendants : un répartiteur et un chauffeur.
  const dispatcherSession = await browser.newContext({ baseURL });
  const driverSession = await browser.newContext({ baseURL });
  const dispatcher = await dispatcherSession.newPage();
  const driver = await driverSession.newPage();

  try {
    await seConnecter(driver, 'driver1@gml.local', 'driver123');
    await expect(driver.getByText('Notifications en temps réel actives')).toBeVisible();

    await seConnecter(dispatcher, 'dispatcher1@gml.local', 'dispatch123');
    const title = 'Mission à annuler en direct';

    await dispatcher.getByLabel('Titre', { exact: true }).fill(title);
    await dispatcher.getByLabel('Destination', { exact: true }).fill('Lyon');
    await dispatcher.getByLabel("Code d'accès entrepôt").fill('CODE-TEST');
    await dispatcher.getByLabel('Chauffeur assigné').selectOption('driver1@gml.local');
    await dispatcher.getByRole('button', { name: 'Créer et notifier le chauffeur' }).click();

    const driverMission = driver.locator('.mission-card').filter({ hasText: title });
    await expect(driverMission).toBeVisible();

    const dispatcherMission = dispatcher.locator('.mission-card').filter({ hasText: title });
    await dispatcherMission.getByRole('button', { name: 'Annuler la mission' }).click();
    await dispatcher.getByRole('button', { name: "Confirmer l'annulation" }).click();

    // Aucun rafraîchissement manuel : le SSE met le statut à jour.
    await expect(driverMission.locator('.status-badge')).toHaveText('Annulée');
  } finally {
    await dispatcherSession.close();
    await driverSession.close();
  }
});

test('MISSION : le chauffeur termine une livraison avec une preuve', async ({ page }) => {
  // 1. Le Dispatcher crée la mission.
  await seConnecter(page, 'dispatcher1@gml.local', 'dispatch123');

  const titre = 'Livraison du test fonctionnel';
  const codeSecret = 'CODE-E2E-2026';

  await page.getByLabel('Titre').fill(titre);
  await page.getByLabel('Destination').fill('Entrepôt de démonstration');
  await page.getByLabel('Description (facultatif)').fill('Mission créée par le test');
  await page.getByLabel("Code d'accès entrepôt").fill(codeSecret);
  await page.getByLabel('Chauffeur assigné').selectOption('driver1@gml.local');
  await page.getByRole('button', { name: 'Créer et notifier le chauffeur' }).click();

  await expect(page.getByText('Mission créée : le chauffeur a été notifié.')).toBeVisible();

  // 2. Le chauffeur se connecte et retrouve la mission.
  await page.getByRole('button', { name: 'Déconnexion' }).click();
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible();
  await seConnecter(page, 'driver1@gml.local', 'driver123');

  const mission = page.locator('.mission-card').filter({ hasText: titre });
  await expect(mission).toBeVisible();

  // Le code et le dépôt de preuve sont indisponibles avant le démarrage.
  await expect(mission).toContainText('Code secret disponible une fois la mission démarrée.');
  await expect(mission.locator('input[type="file"]')).toHaveCount(0);

  // 3. Sans preuve, la clôture est refusée.
  await mission.getByRole('button', { name: /Passer à.*En cours/ }).click();
  await expect(mission.locator('input[type="file"]')).toHaveCount(1);
  await mission.getByRole('button', { name: /Passer à.*Terminée/ }).click();
  await expect(page.getByRole('alert')).toContainText(
    'Une preuve de livraison est requise pour clôturer la mission',
  );
  await expect(mission).toContainText('En cours');

  // 4. Le code est visible à la demande, puis masqué après 30 secondes.
  await page.clock.install();
  await mission.getByRole('button', { name: /Révéler le code secret/ }).click();
  await expect(mission).toContainText('Code secret : ' + codeSecret);
  await page.clock.fastForward(30_100);
  await expect(mission).toContainText('Code secret : ••••••••');
  await expect(mission).not.toContainText('Code secret : ' + codeSecret);

  // 5. Le chauffeur dépose une preuve de livraison.
  const image = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/Tn8AAAAASUVORK5CYII=',
    'base64',
  );

  await mission.locator('input[type="file"]').setInputFiles({
    name: 'preuve.png',
    mimeType: 'image/png',
    buffer: image,
  });
  await mission.getByRole('button', { name: 'Envoyer la preuve de livraison' }).click();
  // Le document doit apparaître dans la liste après son enregistrement.
  await expect(mission.locator('.document-list__name')).toContainText('preuve.png');

  // 6. Le chauffeur peut maintenant terminer la livraison.
  await mission.getByRole('button', { name: /Passer à.*Terminée/ }).click();
  await expect(mission.locator('.status-badge')).toHaveText('Terminée');
  await expect(mission.locator('input[type="file"]')).toHaveCount(0);
  await expect(mission.locator('.document-list__name')).toContainText('preuve.png');
  await expect(mission).not.toContainText('Code secret : ' + codeSecret);
});

test('PREUVE : un fichier non autorisé est refusé', async ({ page }) => {
  // 1. Le Dispatcher crée la mission.
  await seConnecter(page, 'dispatcher1@gml.local', 'dispatch123');

  const titre = 'Livraison avec fichier refusé';

  await page.getByLabel('Titre', { exact: true }).fill(titre);
  await page.getByLabel('Destination', { exact: true }).fill('Rennes');
  await page.getByLabel("Code d'accès entrepôt").fill('CODE-FICHIER');
  await page.getByLabel('Chauffeur assigné').selectOption('driver1@gml.local');
  await page.getByRole('button', { name: 'Créer et notifier le chauffeur' }).click();

  await expect(page.getByText('Mission créée : le chauffeur a été notifié.')).toBeVisible();

  // 2. Le chauffeur démarre la mission.
  await page.getByRole('button', { name: 'Déconnexion' }).click();
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible();
  await seConnecter(page, 'driver1@gml.local', 'driver123');

  const mission = page.locator('.mission-card').filter({ hasText: titre });
  await mission.getByRole('button', { name: /Passer à.*En cours/ }).click();
  await expect(mission.locator('input[type="file"]')).toHaveCount(1);

  // 3. Il envoie un fichier texte renommé en image : le contenu réel est contrôlé.
  await mission.locator('input[type="file"]').setInputFiles({
    name: 'fausse-preuve.png',
    mimeType: 'image/png',
    buffer: Buffer.from('Ceci est un simple texte, pas une image.'),
  });
  await mission.getByRole('button', { name: 'Envoyer la preuve de livraison' }).click();

  await expect(mission.getByRole('alert')).toContainText('Type de fichier non autorisé');
  await expect(mission.locator('.document-list__name')).toHaveCount(0);

  // 4. Sans preuve valable, la mission ne peut pas être terminée.
  await mission.getByRole('button', { name: /Passer à.*Terminée/ }).click();
  await expect(
    page.getByText('Une preuve de livraison est requise pour clôturer la mission'),
  ).toBeVisible();
  await expect(mission.locator('.status-badge')).toHaveText('En cours');
});

test('ADMIN : un compte désactivé ne peut plus se connecter', async ({ page }) => {
  await seConnecter(page, 'admin.e2e@example.test', motDePasseAdmin);
  await expect(page.getByRole('heading', { name: 'Nouveau compte' })).toBeVisible();

  // L'administrateur crée un compte puis le désactive.
  const email = 'chauffeur-desactive@example.test';
  await page.getByLabel('Adresse e-mail').fill(email);
  await page.getByLabel('Mot de passe temporaire').fill('MotDePasseTest123!');
  await page.getByRole('button', { name: 'Créer le compte' }).click();

  const ligne = page.getByRole('row').filter({ hasText: email });
  await expect(ligne).toContainText('Actif');

  page.once('dialog', (dialog) => dialog.accept());
  await ligne.getByRole('button', { name: 'Désactiver' }).click();
  await expect(ligne).toContainText('Désactivé');

  // Le chauffeur désactivé essaie de se connecter avec le bon mot de passe.
  await page.getByRole('button', { name: 'Déconnexion' }).click();
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible();
  await seConnecter(page, email, 'MotDePasseTest123!');

  // La connexion est refusée : il reste sur l'écran de connexion.
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Déconnexion' })).toHaveCount(0);
});

test('ACCES : sans connexion, les missions ne sont pas accessibles', async ({ page }) => {
  // L'application affiche le formulaire de connexion.
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible();

  // Un appel direct à l'API est refusé lui aussi.
  const reponse = await page.request.get('/api/missions');
  expect(reponse.status()).toBe(401);
});
