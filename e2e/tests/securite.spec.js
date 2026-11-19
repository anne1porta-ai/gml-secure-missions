import { test, expect } from '@playwright/test';

// Les scénarios sont exécutés sur l'application locale, jamais sur OVH.
async function connecterDispatcher(page) {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible();

  await page.getByLabel('Adresse e-mail').fill('dispatcher1@gml.local');
  await page.getByLabel('Mot de passe').fill('dispatch123');
  await page.getByRole('button', { name: 'Se connecter' }).click();

  await expect(page.getByRole('heading', { name: 'Nouvelle mission' })).toBeVisible();
}

async function creerMission(page, titre) {
  await page.getByLabel('Titre', { exact: true }).fill(titre);
  await page.getByLabel('Destination', { exact: true }).fill('Lyon');
  await page.getByLabel("Code d'accès entrepôt").fill('CODE-TEST-SECURITE');
  await page.getByLabel('Chauffeur assigné').selectOption('driver1@gml.local');

  await page.getByRole('button', { name: 'Créer et notifier le chauffeur' }).click();
  await expect(page.getByText('Mission créée : le chauffeur a été notifié.')).toBeVisible();

  const titreAffiche = page.locator('.mission-card__title').filter({ hasText: titre });
  await expect(titreAffiche).toHaveText(titre);

  return titreAffiche;
}

test('SECURITE : une injection dans l’adresse e-mail est refusée par le formulaire', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible();

  const email = page.getByLabel('Adresse e-mail');
  await email.fill("' OR '1'='1' --");
  await page.getByLabel('Mot de passe').fill('mot-de-passe');

  // L'adresse n'est pas valide : le navigateur ne doit pas envoyer le formulaire.
  let requetesConnexion = 0;
  page.on('request', (requete) => {
    if (requete.method() === 'POST' && new URL(requete.url()).pathname === '/api/auth/login') {
      requetesConnexion += 1;
    }
  });

  await page.getByRole('button', { name: 'Se connecter' }).click();

  expect(await email.evaluate((champ) => champ.validity.valid)).toBe(false);
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible();
  expect(requetesConnexion).toBe(0);
});

test('SECURITE : une injection dans le mot de passe ne permet pas la connexion', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible();

  await page.getByLabel('Adresse e-mail').fill('dispatcher1@gml.local');
  await page.getByLabel('Mot de passe').fill("' OR 1=1 --");

  const reponseConnexion = page.waitForResponse(
    (reponse) =>
      new URL(reponse.url()).pathname === '/api/auth/login' &&
      reponse.request().method() === 'POST',
  );

  await page.getByRole('button', { name: 'Se connecter' }).click();

  expect((await reponseConnexion).status()).toBe(401);
  await expect(page.getByRole('alert')).toContainText('Identifiants invalides');
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible();
});

test('SECURITE : une instruction SQL dans un titre reste du texte', async ({ page }) => {
  await connecterDispatcher(page);

  const titre = "Test SQL x'); DROP TABLE mission; -- " + Date.now();
  await creerMission(page, titre);

  // La base fonctionne toujours : une nouvelle mission peut être enregistrée.
  const autreTitre = 'Mission après essai SQL ' + Date.now();
  await creerMission(page, autreTitre);

  await expect(page.locator('.mission-card__title').filter({ hasText: titre })).toHaveText(titre);
});

test('SECURITE : un titre contenant une balise script ne lance pas de code', async ({ page }) => {
  await connecterDispatcher(page);

  const titre = '<script>window.__gmlTestXss = true</script> ' + Date.now();
  const titreAffiche = await creerMission(page, titre);

  // React doit afficher les caractères tels quels, sans créer de balise script.
  await expect(titreAffiche.locator('script')).toHaveCount(0);
  const scriptExecute = await page.evaluate(() => window.__gmlTestXss === true);
  expect(scriptExecute).toBe(false);
});
