// Alphabet sans caractères ambigus (0/O, 1/l/I) : le mot de passe est souvent dicté ou recopié.
const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789';

/**
 * Génère un mot de passe temporaire à partir du générateur cryptographique du navigateur.
 * La source d'aléa est injectable pour pouvoir tester la fonction.
 */
export function generateTemporaryPassword(
  length = 16,
  fillRandom = (values) => window.crypto.getRandomValues(values),
) {
  const values = new Uint32Array(length);
  fillRandom(values);

  return Array.from(values, (value) => ALPHABET[value % ALPHABET.length]).join('');
}
