-- Détails facultatifs d'une mission, saisis par le dispatcher.
-- Les colonnes sont nullables : la version précédente de l'application fonctionne
-- toujours avec ce schéma, ce qui permet de revenir au code antérieur sans restaurer la base.
ALTER TABLE mission ADD COLUMN description VARCHAR(1000);
ALTER TABLE mission ADD COLUMN scheduled_at TIMESTAMP;

-- Correctif : l'API accepte une destination de 500 caractères (CreateMissionRequest),
-- mais la colonne n'en acceptait que 255, ce qui provoquait une erreur 500 au-delà.
ALTER TABLE mission ALTER COLUMN destination TYPE VARCHAR(500);
