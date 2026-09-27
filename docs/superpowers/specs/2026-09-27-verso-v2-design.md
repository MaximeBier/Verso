# Verso V2 — confort de lecture : design

2026-09-27 · validé par Maxime le 2026-09-27

Référence produit : `docs/SPEC.md`, section « V2 — Confort de lecture », et les maquettes 2.01 à 2.09. Ce document fixe le découpage, les décisions prises en plus de la spec et l'architecture. La spec l'emporte ; les décisions ci-dessous y sont reportées (« Décisions prises », « Critères d'acceptation V2 », « Historique »).

## Découpage

Une étape par commit (`Étape N : …`), à la suite de la V1, poussée et installée sur le téléphone. Une étape est finie quand ses critères V2 sont cochés sur le téléphone.

| # | Étape | Écrans | Critères V2 |
| --- | --- | --- | --- |
| 9 | Réglages de lecture et polices | — | 1 |
| 10 | Thèmes sépia et noir | 2.03, 2.04 | 4, 5 |
| 11 | Barre de lecture V2 et feuille « Aa » | 2.01, 2.02 | 2, 3 |
| 12 | Mode pages | 2.05 | 7, 8, 9, 10 |
| 13 | États des livres, filtres, « Trier et afficher » | 2.07, 2.07b, 2.08 | 11, 12, 13 |
| 14 | Statistiques et Paramètres V2 | 2.08, 2.09 | 14, 15, 16 |
| 15 | Recherche plein texte | 2.06 | 6, 17, 18 |
| 16 | Passe d'acceptation V2 et README | toutes | toutes, plus 19 et 20 |

## Décisions

| Sujet | Décision |
| --- | --- |
| Taille du texte | 20 sp par défaut (maquette 2.02), de 14 à 32, par pas de 1. Multipliée par l'échelle de police d'Android, comme en V1 |
| Interligne | Serré 1,4 / Normal 1,6 (défaut) / Aéré 1,8 |
| Marges latérales | Étroites 16 dp / Normales 24 dp (défaut, valeur V1) / Larges 32 dp |
| Police | Literata (défaut) / Atkinson Hyperlegible Next / police du système. Appliquée à toute l'app et au texte de lecture |
| Portée des réglages | Police, taille, interligne, marges et thème sont globaux (feuille « Aa » et Paramètres écrivent les mêmes valeurs). Le défilement est mémorisé par livre ; les Paramètres fixent le défilement par défaut des livres sans choix propre |
| Mode pages et position | Un tour de page est un geste de lecture (jamais un fling). La machine à états garde ses règles : la fenêtre « 3 écrans en 5 s » fait d’un feuilletage rapide une navigation, avec la carte « Revenir ». Seuils propres au mode pages pour la confirmation (pause maximale 90 s, dérive 2,5 écrans), sinon une lecture page par page ne serait jamais confirmée |
| État d'un livre | Calculé : « À lire » si jamais ouvert, « Terminé » à partir de 99 % (`LibraryRules.FINISHED_PROGRESSION`), « En cours » sinon. Un choix manuel dans la fiche l'emporte jusqu'au prochain choix manuel |
| Vitesse de lecture | Somme des mots lus / somme du temps actif des sessions du livre. Sans session : 250 mots par minute, comme en V1 |
| Recherche | Toucher un résultat est un saut explicite (comme le sommaire) : carte « Revenir ». Le mot trouvé est marqué dans le texte par un fond et un soulignement |
| Sessions à la suppression d'un livre | Supprimées avec le livre (décision V1 confirmée : les statistiques V2 sont par livre) |

## Architecture

### Réglages (étape 9)

- `:core` : `ReadingSettings` (police, taille, interligne, marges, défilement par défaut) et ses bornes nommées (`ReadingSettingsLimits`), `ReadingFont` (LITERATA, ATKINSON, SYSTEM), `LineSpacing`, `Margins`, `ScrollMode` (CONTINUOUS, PAGES). Logique pure, testée.
- `SettingsRepository` expose `readingSettings: Flow<ReadingSettings>` et les écritures ; une valeur inconnue revient au défaut.
- `ReadingStyle` et `VersoReadingPreferences` prennent un `ReadingSettings` au lieu des constantes V1. Les deux polices embarquées sont déclarées au navigateur (`addFontFamilyDeclaration`) ; la police du système est `FontFamily.SANS_SERIF` de Readium. Les changements passent par `EpubNavigatorFragment.submitPreferences` : pas de recréation du fragment, position gardée par Readium.
- Thème Compose : `VersoTheme` reçoit la police choisie et construit la typographie à partir d'elle (mêmes tailles et graisses que la V1). Literata en TTF variable (romain et italique, OFL) dans `res/font` et `assets/fonts`.
- Le calcul des écrans (`ReaderScreenDistance`) utilise la taille et l'interligne réels.

### Thèmes (étape 10)

- `ThemeMode` : AUTO, LIGHT, SEPIA, DARK, BLACK. `VersoPalette` gagne Sepia et Black, tirées de `tokens.json`. AUTO suit le système (clair ou sombre).
- `UiModeManager.setApplicationNightMode` (Android 12 et plus) : sépia → mode jour, noir → mode nuit.
- Readium : couleurs de fond, de texte et de lien de la palette choisie ; graisse allégée et interlettrage des thèmes sombres pour sombre et noir.

### Barre et feuille « Aa » (étape 11)

- Barre du bas : outils avec icône et texte dessous. L'étape 11 pose Sommaire, Journal et Réglages ; « Rechercher » s'insère à l'étape 15, pour ne jamais montrer de commande inactive.
- Feuille « Réglages de lecture » : `VersoBottomSheet` sans voile, ouverte à mi-hauteur, dépliable. En haut : Police (3 cartes), Taille (− et +), Thème (5 pastilles). En dessous : Interligne, Marges, Défilement (segmentés). Chaque changement s'applique en direct au texte derrière.

### Mode pages (étape 12)

- `EpubPreferences.scroll = false` : pagination native de Readium en colonnes CSS (une ligne n'est jamais coupée en bas de page), changement de chapitre géré par Readium.
- `FragmentReaderController` : en mode pages, les tours de page (swipe ou tap dans le tiers gauche ou droit) émettent `GestureSignal(isFling = false)` ; le tap au centre affiche la barre, comme en continu. L'enchaînement des chapitres au bord (propre au continu) est désactivé.
- Pied de page Compose : chapitre à gauche, « Page 2 sur 9 » à droite, calculé à partir de la progression dans le chapitre et du nombre de colonnes (script JavaScript évalué dans la ressource).
- Room : migration 1 → 2, colonnes `scrollMode` (TEXT nullable, null = défaut global) et `stateOverride` (TEXT nullable).

### États (étape 13)

- `LibraryRules.status` renvoie `BookStatus` (TO_READ, IN_PROGRESS, FINISHED) en tenant compte de `stateOverride`. « Nouveau » devient « À lire ».
- Bibliothèque : pastilles « Tous / En cours / À lire / Terminés » (filtre gardé dans le ViewModel, non persisté), bouton « Récents ▾ » qui ouvre la feuille « Trier et afficher » (tri et affichage, persistés comme en V1).
- Fiche : bouton segmenté « À lire / En cours / Terminé » qui écrit `stateOverride`.

### Statistiques et Paramètres (étape 14)

- `:core` : `ReadingStats` (temps total, nombre de sessions, vitesse, temps restant), testée.
- Fiche : section « Statistiques » et « Voir le journal de lecture » (feuille du journal existante), masquée si l'interrupteur « Afficher les statistiques » est désactivé. Le temps restant de la bibliothèque et de la barre utilise la vitesse mesurée.
- Paramètres : section Lecture (phrase d'aide, polices en boutons radio avec la phrase d'exemple, thème, taille, défilement par défaut) et interrupteur des statistiques.

### Recherche (étape 15)

- `publication.search(query)` (service de recherche de Readium pour l'EPUB) ; les pages de résultats arrivent une par une et la liste s'allonge (« 3 résultats pour l'instant »), groupée par chapitre (`locator.title`), avec le pourcentage du livre.
- Écran plein de la lecture (surcouche Compose au-dessus du lecteur) : champ, bouton retour, effacer, barre de progression de la recherche.
- Mot trouvé dans la liste : fond `highlight`, gras et soulignement. Dans le texte, après le saut : décoration Readium avec un gabarit HTML (fond et soulignement), effacée à la fermeture de la recherche ou au prochain geste.
- Toucher un résultat : `ReaderEvent.Jumped` puis saut, comme le sommaire.

## Tests

- JUnit (`:core`) : `ReadingSettings` et bornes, `BookStatus` avec choix manuel, `ReadingStats`, suite de tours de page dans `ReadingPositionTracker` (lecture lente = lecture ; 3 pages en 5 s = navigation).
- Robolectric / Roborazzi (`:app`) : captures des écrans 2.01 à 2.09 en clair et en sombre (et sépia, noir pour 2.03, 2.04), arbre d'accessibilité, migration Room 1 → 2 (`MigrationTestHelper`), `SettingsRepository`.
- Téléphone : chaque critère V2, avec les EPUB réels de `app/src/test/resources/epub/real/`.
