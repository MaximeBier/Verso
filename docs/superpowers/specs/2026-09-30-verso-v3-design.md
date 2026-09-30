# Verso V3 — notes et sauvegarde : design

2026-09-30 · validé par Maxime le 2026-09-30

Référence produit : `docs/SPEC.md`, section « V3 — Notes et sauvegarde », et les maquettes 3.04 à 3.07. Ce document fixe le découpage, les décisions prises en plus de la spec et l'architecture. La spec l'emporte ; les décisions ci-dessous y sont reportées (« V3 », « Critères d'acceptation V3 », « Historique »).

Hors V3 (décidé le 2026-09-30, section « Plus tard » de la spec) : import PDF (maquettes 3.01 à 3.03, encore étiquetées V3 dans l'export, qui n'est pas refait) et import TXT, Markdown et HTML. Verso ne lit que des EPUB.

## Découpage

Une étape par commit (`Étape N : …`), à la suite de la V2, poussée et installée sur le téléphone. Une étape est finie quand ses critères V3 sont cochés sur le téléphone.

| # | Étape | Écrans | Critères V3 |
| --- | --- | --- | --- |
| 17 | Surlignages : sélection, barre, surligner, copier, rendu dans le texte, toucher un surlignage | 3.04 | 1, 2, 3, 5, 7 |
| 18 | Notes : feuille « Ajouter une note », modification, suppression avec « Annuler » | 3.05 | 4, 6 |
| 19 | Écran « Notes et surlignages », accès depuis la fiche et la barre de lecture, export Markdown | 3.06 | 8, 9 |
| 20 | Sauvegarde et restauration, ligne dans les Paramètres | 3.07 | 10, 11, 12 |
| 21 | Passe d'acceptation V3 et README | toutes | toutes, plus 13 et 14 |

## Décisions

| Sujet | Décision |
| --- | --- |
| Sélection | Un appui long sélectionne ; la barre de la maquette 3.04 (« Sélection : « … » », Surligner, Note, Copier) remplace le menu natif d'Android. Même comportement en continu et en mode pages ; une sélection reste dans un seul chapitre (limite de Readium) |
| Surligner | Crée le surlignage aussitôt, efface la sélection et ferme la barre, sans snackbar |
| Note | Ouvre la feuille 3.05 ; « Enregistrer » crée le surlignage avec sa note ; « Annuler » ou la croix ne crée rien |
| Copier | Presse-papiers ; snackbar « Passage copié » sous Android 13 (Android 13 et plus affiche sa propre confirmation). Texte absent des maquettes |
| Chevauchement | Surligner un passage qui recoupe un surlignage existant fusionne les deux ; les notes sont mises bout à bout. Jamais deux surlignages empilés |
| Rendu | Fond de la couleur « Surlignage » des jetons et soulignement, posés par le gabarit de `SearchMatchDecoration` (ombre intérieure, ReadiumCSS rendant transparent tout `background-color`) |
| Position de lecture | La sélection n'est ni lecture ni navigation : appui long, poignées, défilement automatique pendant la sélection et toucher qui annule la sélection sont ignorés par la machine à états (pas de carte « Revenir », pas de changement de position, pas d'affichage de la barre) |
| Toucher un surlignage | Ouvre une feuille avec le passage, la note et trois actions : « Modifier la note » (« Ajouter une note » sans note), « Supprimer », « Copier ». Un toucher ailleurs affiche la barre de lecture, comme en V2 |
| Suppression | Immédiate, snackbar « Surlignage supprimé » avec « Annuler ». Pas de confirmation |
| Accès à « Notes et surlignages » | Ligne « Notes et surlignages · 3 » dans la fiche, sous « Voir le journal de lecture », et 5ᵉ outil « Notes » dans la barre de lecture. Si les cinq outils ne tiennent pas à 200 %, repli sur la fiche seule (décision à reporter dans la spec) |
| Menu ⋮ d'un élément (3.06) | « Aller au passage », « Modifier la note » (ou « Ajouter une note »), « Supprimer ». Toucher l'élément va aussi au passage : saut explicite, carte « Revenir » |
| Export Markdown | Sélecteur Android (création de document), nom proposé `<Titre> – notes.md`. Pas de bouton « Partager ». Contenu : `# Titre`, ligne auteur · nombre d'éléments · date d'export, puis un `## Chapitre` par chapitre, chaque passage en citation (`>`) suivi de sa note |
| Livre sans surlignage | « Exporter » masqué ; « Aucun surlignage pour l'instant. Sélectionnez un passage pendant la lecture pour le surligner. » (texte absent des maquettes) |
| Accès à la sauvegarde | Ligne « Sauvegarde » dans les Paramètres |
| Contenu de la sauvegarde | Un zip `verso-sauvegarde-AAAA-MM-JJ.zip` : fichiers EPUB et couvertures, `donnees.json` (livres et métadonnées corrigées, positions, états, défilement par livre, sessions, surlignages et notes), réglages DataStore. Numéro de format dans le zip ; un format plus récent que l'app est refusé avec un message |
| Format des données | JSON plutôt qu'une copie de la base Room, pour qu'une version future au schéma différent relise une sauvegarde de la V3 |
| Création | Sélecteur Android (création de document) ; la carte « Dernière sauvegarde » affiche date, taille et nom. Sans sauvegarde : « Aucune sauvegarde pour l'instant » (texte absent des maquettes). Aucun réseau dans Verso : c'est le fournisseur choisi (Drive…) qui envoie le fichier |
| Restauration | Sélecteur de fichiers ; lecture et validation complètes avant toute modification ; confirmation « Remplacer votre bibliothèque ? » avec le nombre de livres actuels et de la sauvegarde ; remplacement tout ou rien ; arrivée sur la bibliothèque restaurée. Le lecteur est toujours fermé (la sauvegarde est dans les Paramètres) |

## Architecture

### `:core` (logique pure, testée)

- `HighlightMerge` : fusion de plages qui se recouvrent dans un même chapitre, notes mises bout à bout. Les plages se comparent par la progression du locator dans la ressource et par le texte.
- `NotesMarkdown` : fichier exporté à partir des surlignages ordonnés et des libellés de chapitre.
- `BackupManifest` : numéro de format, validation du contenu (livres référencés présents, identifiants cohérents).

### Données

- Room : migration 2 → 3, table `highlights` : `id`, `bookId` (clé étrangère, suppression en cascade), `locator` (JSON Readium de la plage), `text`, `note` (nullable), `progression` (dans le livre, pour l'ordre et le pourcentage), `createdAt`, `updatedAt`. `HighlightDao`, `HighlightRepository`.
- DataStore : date, taille et nom de la dernière sauvegarde.

### Lecteur (étapes 17 à 19)

- `FragmentReaderController` : `selectionActionModeCallback` dans la configuration du navigateur, qui masque le menu natif et signale la sélection (`currentSelection()`) à Compose ; `clearSelection()` après une action.
- Surlignages : décorations Readium du groupe `highlights`, dessinées par le gabarit de `SearchMatchDecoration` ; toucher une décoration arrive par `addDecorationListener`.
- `ReaderGestures` ignore l'appui long et les gestes pendant une sélection.
- `HighlightCoordinator` : état de la sélection, feuilles (actions, note), création, fusion, suppression et annulation. Séparé de `ReaderViewModel` (778 lignes), comme `SessionCoordinator`.
- Barre de lecture : 5ᵉ outil « Notes ». Écran « Notes et surlignages » (route Compose), ouvert depuis la barre et depuis la fiche ; aller au passage passe par le saut explicite existant (carte « Revenir »).

### Sauvegarde (étape 20)

- `BackupWriter` : zip écrit en flux vers l'URI choisie (EPUB, couvertures, `donnees.json`, réglages), sans tout charger en mémoire.
- `BackupRestorer` : décompression dans un dossier temporaire, validation (`BackupManifest`), puis remplacement des fichiers, de la base (une transaction Room) et des réglages ; en cas d'échec, retour à l'état d'avant et dossier temporaire effacé.
- Conversion JSON par `kotlinx.serialization`, déjà dans le projet. Aucune nouvelle dépendance, aucune permission.
- Écran « Sauvegarde » (3.07), ouvert depuis les Paramètres.

### Tests

- JVM : `HighlightMerge`, `NotesMarkdown`, `BackupManifest` ; DAO et migration 2 → 3 (Robolectric) ; aller-retour sauvegarde → restauration sur une bibliothèque de test ; restauration interrompue ou fichier invalide qui laisse la bibliothèque intacte ; machine à états qui ignore la sélection.
- Roborazzi : 3.04 à 3.07 en clair, sombre et nuit.

## Critères d'acceptation V3

Reportés dans `docs/SPEC.md`, « Critères d'acceptation V3 ».
