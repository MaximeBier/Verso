# Verso V4 — collections : design

2026-10-01 · validé par Maxime le 2026-10-01

Référence produit : `docs/SPEC.md`, section « V4 — Collections », et les maquettes 4.01 à 4.04. Ce document fixe le découpage, les décisions prises en plus de la spec et l'architecture. La spec l'emporte ; les décisions ci-dessous y sont reportées (« V4 », « Critères d'acceptation V4 », « Historique »).

## Découpage

Une étape par commit (`Étape N : …`), à la suite de la V3, poussée et installée sur le téléphone. Une étape est finie quand ses critères V4 sont cochés sur le téléphone.

| # | Étape | Écrans | Critères V4 |
| --- | --- | --- | --- |
| 22 | Données et logique : tables Room (migration 3 → 4), DAO, dépôt, calculs `:core` (progression, décompte, temps restant, « Reprendre », auteur commun, ordre) | — | 3 (tests) |
| 23 | Onglets « Livres / Collections » et liste des collections | 4.01 | 1, 2 (affichage) |
| 24 | Création et ajout : écran « Nouvelle collection », feuille « Ajouter à une collection » depuis le ⋮ d'un livre et depuis la fiche | 4.03, 4.04 | 2, 7 |
| 25 | Écran d'une collection : statistiques, « Reprendre », liste, « Réordonner », renommer, supprimer, retirer un livre | 4.02 | 3, 4, 5, 6, 8 |
| 26 | Sauvegarde des collections, passe d'acceptation V4 et README | toutes | toutes, plus 9, 10 et 11 |

## Décisions

| Sujet | Décision |
| --- | --- |
| Auteur d'une collection | Calculé, jamais saisi : l'auteur commun à tous ses livres (comparaison sans tenir compte de la casse ni des espaces autour), sinon aucun auteur, et la ligne n'affiche que « 5 livres » |
| Ordre à la création | L'ordre où les livres apparaissent dans la bibliothèque, avec le tri choisi à ce moment-là |
| Ordre des ajouts | Un livre ajouté à une collection existante se place en dernier |
| Réordonner | « Réordonner » (bouton ou menu ⋮) passe la liste en mode édition : chaque ligne a une poignée de glisser et des boutons « Monter » et « Descendre » (48 dp, intitulés TalkBack). « Terminé » quitte ce mode. L'ordre est enregistré à chaque déplacement |
| ⋮ d'un livre dans une collection | « Détails du livre », « Retirer de la collection ». Le retrait est immédiat, sans confirmation, et le livre reste dans la bibliothèque |
| Progression | Somme des mots lus divisée par la somme des mots de la collection. Mots lus d'un livre = nombre de mots × progression, ou tous ses mots si son état est « Terminé ». Un livre sans nombre de mots compte pour 0 mot. Arrondi à l'entier inférieur, et 100 % seulement si tous les livres sont terminés |
| Décompte | D'après l'état de chaque livre (le même que dans la bibliothèque, y compris l'état choisi à la main) : « 2 terminés · 2 en cours · 1 à lire ». Les catégories à zéro sont omises |
| Temps restant | La somme des temps restants des livres non terminés, chacun à la vitesse utilisée dans sa fiche (la vitesse mesurée, sinon 250 mots/min). Ligne masquée quand tout est terminé |
| « Reprendre » | Le premier livre non terminé dans l'ordre de la collection, ouvert à sa position de lecture. La carte est masquée si tous les livres sont terminés ou si la collection est vide |
| Pile de couvertures | Les trois premiers livres dans l'ordre, le premier devant. Avec moins de trois livres, la pile est plus courte. Une collection vide montre une seule couverture typographique, avec l'initiale du nom |
| Ordre des collections | Par date de création, la plus récente en haut. Pas de tri au choix |
| Onglet au lancement | Toujours « Livres ». L'onglet n'est pas mémorisé, mais il est conservé quand on revient d'un écran ouvert depuis l'onglet « Collections » |
| Onglet « Collections » vide | Une phrase et le bouton « Nouvelle collection » (texte absent des maquettes, proposé) |
| Création | « Créer » est actif dès que le nom contient autre chose que des espaces, même sans livre coché. Deux collections peuvent porter le même nom. Le filtre cherche dans le titre et l'auteur, comme celui de la bibliothèque ; un livre coché puis masqué par le filtre reste coché. Après « Créer », on arrive sur l'écran de la collection |
| « Nouvelle collection » depuis la feuille 4.04 | Ouvre l'écran 4.03 avec le livre déjà coché. Après « Créer », retour à la feuille, où la nouvelle collection apparaît cochée |
| Feuille « Ajouter à une collection » | Chaque case agit tout de suite (ajout en dernier, ou retrait). « Terminé » et la croix ferment la feuille. Sans collection, la feuille ne montre que « Nouvelle collection » |
| Fiche du livre | Ligne « Collections » : les noms séparés par des virgules, ou « Aucune » (proposé), et « Modifier », qui ouvre la feuille 4.04. Elle est placée sous « État », comme dans la maquette |
| Renommer | Dialogue avec le champ « Nom de la collection », « Annuler » et « Renommer » (proposé) |
| Supprimer une collection | Dialogue « Supprimer « Nom » ? Les livres restent dans votre bibliothèque. », « Annuler » et « Supprimer » (proposé), puis retour à l'onglet « Collections » |
| Collection vide | Écran de la collection avec « 0 % », sans décompte, sans temps restant et sans « Reprendre », et une phrase qui explique comment ajouter un livre depuis son menu ⋮ (proposé) |
| Supprimer un livre | Il quitte toutes ses collections (suppression en cascade) ; les livres suivants remontent d'un rang. Une collection peut rester vide |
| Sauvegarde | Les collections et leur ordre entrent dans `donnees.json`, au format 2. Une sauvegarde au format 1 (V3) se restaure sans collection ; une sauvegarde au format 3 ou plus est refusée, comme avant |

## Architecture

### `:core` (logique pure, testée)

- `collections/CollectionSummary` : à partir de la liste ordonnée des livres (nombre de mots, progression, état, vitesse), calcule le pourcentage, le décompte par état, les minutes restantes (`remainingMinutes` existant), l'index du livre à reprendre et l'auteur commun.
- `collections/CollectionOrder` : déplacer d'un rang vers le haut ou vers le bas, déplacer à un index (glisser), positions denses 0..n-1 après un retrait.

### Données

- Room : migration 3 → 4.
  - `collections` : `id`, `name`, `createdAt`.
  - `collection_books` : `collectionId`, `bookId` (clé primaire composée, clés étrangères avec suppression en cascade des deux côtés, index sur `bookId`), `position`.
- `CollectionDao` : collections avec le nombre de livres, livres d'une collection dans l'ordre, collections d'un livre, ajout en dernier, retrait avec renumérotation, réordonnancement en une transaction.
- `CollectionRepository`. La suppression d'un livre renumérote les collections touchées : la cascade supprime les lignes, puis le dépôt referme les trous dans la même transaction que la suppression du livre.

### Interface

- `LibraryScreen` : onglets construits sur `VersoSegmentedButton`. L'onglet « Livres » reste celui d'aujourd'hui, avec la carte « Reprendre », le tri et le filtre. L'onglet « Collections » est un nouveau composable (`CollectionsTab`) qui suit le même gabarit d'en-tête (« Collections » et « 2 collections »).
- Routes : `CollectionRoute(collectionId)`, `NewCollectionRoute(preselectedBookId: Long? = null)`.
- `CollectionScreen` et `CollectionViewModel` (4.02), avec un mode édition pour « Réordonner ». `NewCollectionScreen` et `NewCollectionViewModel` (4.03).
- `AddToCollectionSheet` (4.04), sur `VersoBottomSheet`, partagée entre le menu ⋮ de la bibliothèque (nouvelle entrée « Ajouter à une collection ») et la fiche. Le retour de 4.03 vers la feuille passe par un résultat de navigation (`savedStateHandle`).
- Couvertures : `BookCover` existant ; la pile est un nouveau composant (`CoverStack`).

### Sauvegarde (étape 26)

- `BackupManifest.VERSION` passe à 2, et la lecture accepte les formats 1 et 2. Les DTO gagnent `collections` (id, nom, date de création, livres dans l'ordre par empreinte SHA-256), avec une liste vide par défaut pour lire le format 1.
- `BackupRestorer` remplace les collections dans la même transaction que le reste. La validation vérifie que chaque livre référencé existe dans la sauvegarde.

### Tests

- JVM : `CollectionSummary` (pondération : un long livre à moitié lu et un court terminé, état choisi à la main, livre sans nombre de mots, collection vide, arrondi et 100 %) et `CollectionOrder`.
- Robolectric : DAO et migration 3 → 4, cascade et renumérotation à la suppression d'un livre, aller-retour de sauvegarde avec des collections, restauration d'une sauvegarde au format 1.
- Roborazzi : 4.01 à 4.04 en clair, en sombre et en nuit. `AccessibilityTreeTest` étendu aux nouveaux écrans, à 200 %.

## Critères d'acceptation V4

Reportés dans `docs/SPEC.md`, « Critères d'acceptation V4 ».
