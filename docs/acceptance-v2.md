# Verso V2 — checklist d’acceptation

Référence : « Critères d’acceptation V2 » de `docs/SPEC.md`. Une case de la spec n’est cochée que lorsque **toutes** les vérifications de sa ligne sont faites et conformes.

- **Auto** : test automatisé, lancé par `./gradlew :core:test :app:testDebugUnitTest`.
- **Claude** : fait par Claude sur le téléphone (adb Wi-Fi, `ANDROID_SERIAL=192.168.1.10:5555`) sans toucher aux réglages système (captures dans `build/acceptance/<horodatage>/`).
- **Maxime** : fait à la main par Maxime (réglage système, redémarrage, écoute TalkBack).

| # | Étape | Critère | Auto | Claude | Maxime | Résultat |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 9 | Mise à jour : Literata partout, bibliothèque, positions et journal V1 intacts | `VersoDatabaseMigrationTest`, `SettingsRepositoryTest.v1ThemeValueIsReadUnchanged`, `TypographyTest.literataIsTheDefault` | installation par-dessus la V1 (`installDebug`, sans effacer les données) : livres, carte Reprendre, journal, texte en Literata | — | ☑ |
| 2 | 11 | Atkinson puis police du système : toute l’app et le texte changent, le choix survit au redémarrage | `TypographyTest`, `VersoReadingPreferencesTest.systemFontIsTheWebViewSansSerif` | cartes Police de la feuille « Aa » (étape 11 ; Paramètres à l’étape 14), captures bibliothèque et lecture ; arrêt forcé puis relance | redémarrage du téléphone | ☑ |
| 3 | 11 | Taille, interligne, marges en lecture : effet immédiat, même paragraphe | `ReaderViewModelTest.settingsAndThemeAreSubmittedWithoutMovingTheReadingPosition`, `VersoReadingPreferencesTest.userSettingsAreApplied` | feuille « Aa » : capture avant/après, même premier paragraphe | — | ☑ |
| 4 | 10 | Sépia et Noir sur toute l’app et le texte, sans zone d’une autre couleur ; Automatique suit le téléphone | `V2ScreenshotTest`, `ThemeModeTest` | captures bibliothèque, fiche, Paramètres, lecture, barres, sommaire, journal, carte « Revenir » en sépia et en noir ; barre de navigation système vérifiée dans les quatre thèmes (fenêtre principale et feuilles modales) | Automatique avec le thème du téléphone changé | ☑ |
| 5 | 10 | Contraste ≥ 7:1 dans les cinq thèmes | `PaletteContrastTest` (quatre palettes) | — | — | ☑ |
| 6 | 15 | Barre : Sommaire, Journal, Rechercher, Réglages avec texte | | | | ☐ |
| 7 | 12 | Feuille « Aa » à mi-hauteur sans voile, effet en direct, réglages du bas en glissant | `ReadingSettingsSheetTest` (dont `scrollModeRowSaysItIsForThisBook`), `V2ScreenshotTest` (2.02) | feuille dépliée : Interligne, Marges, Défilement « Pour ce livre » (`03-feuille-depliee.png`) | — | ☑ |
| 8 | 12 | Mode pages : swipe et tap latéral, aucune ligne coupée, pied de page | `FragmentReaderControllerTest`, `PageFooterTest`, `PageLabelTest`, `ReaderScriptsTest`, `V2ScreenshotTest` (2.05) | un tour par tap (dans les deux sens), swipe, aucune ligne coupée ; « Page x sur y » exact (Candide V : 7 pages, Madame Bovary IX : 21, X : 19) ; fin de chapitre et fin de fichier → page 1 du suivant ; tap au centre : la barre recouvre le pied | actions TalkBack « Page suivante » / « Page précédente » du pied de page | ☑ |
| 9 | 12 | Mode mémorisé par livre, position gardée au changement de mode | `BookRepositoryTest.scrollModeIsStoredPerBook`, `ReaderViewModelTest.switchingToPagesIsSavedForThisBookWithoutMovingTheReadingPosition` | continu → pages → continu : même paragraphe à l’écran ; Madame Bovary en pages, Candide et Silo en continu, chacun rouvre dans son mode après un arrêt forcé | — | ☑ |
| 10 | 12 | Feuilletage rapide = navigation et carte « Revenir » ; lecture page par page = progression | `ReadingPositionTrackerTest` (mode pages), `ReaderViewModelTest.bookOpenedInPagesUsesThePagesThresholds`, `switchingToPagesAppliesThePagesThresholds` | lecture page par page (30 s) : la progression suit ; cinq pages en 2 s : carte « Revenir », un tap ramène (à la page qui suit celle de départ : le premier tour compte comme lecture) ; **quatre pages en 2 s : aucune carte, la lecture suit** (la fenêtre ne voit que 3 écrans de déplacement après la première position) | — | ☐ |
| 11 | 13 | États À lire / En cours / Terminé et filtres | | | | ☐ |
| 12 | 13 | État choisi dans la fiche, gardé après redémarrage et relecture | | | | ☐ |
| 13 | 13 | « Trier et afficher » : tri et affichage persistés | | | | ☐ |
| 14 | 14 | Statistiques de la fiche après deux sessions, « Voir le journal de lecture » | | | | ☐ |
| 15 | 14 | Interrupteur des statistiques ; journal toujours tenu | | | | ☐ |
| 16 | 14 | Section Lecture des Paramètres (2.09) | | | | ☐ |
| 17 | 15 | Recherche : résultats au fur et à mesure, par chapitre, mot marqué (fond, gras, soulignement) | | | | ☐ |
| 18 | 15 | Toucher un résultat : passage marqué, carte « Revenir » | | | | ☐ |
| 19 | 16 | Texte à 200 % et commandes ≥ 48 dp intitulées sur les nouveaux écrans | | | | ☐ |
| 20 | 16 | Aucune permission réseau ni autre | | | | ☐ |

Les colonnes vides sont remplies par l’étape qui porte le critère.
