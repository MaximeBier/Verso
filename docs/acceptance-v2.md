# Verso V2 — checklist d’acceptation

Référence : « Critères d’acceptation V2 » de `docs/SPEC.md`. Une case de la spec n’est cochée que lorsque **toutes** les vérifications de sa ligne sont faites et conformes.

- **Auto** : test automatisé, lancé par `./gradlew :core:test :app:testDebugUnitTest`.
- **Claude** : fait par Claude sur le téléphone (adb Wi-Fi, `ANDROID_SERIAL=192.168.1.10:5555`) sans toucher aux réglages système (captures dans `build/acceptance/<horodatage>/`).
- **Maxime** : fait à la main par Maxime (réglage système, redémarrage, écoute TalkBack).

| # | Étape | Critère | Auto | Claude | Maxime | Résultat |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 9 | Mise à jour : Literata partout, bibliothèque, positions et journal V1 intacts | `VersoDatabaseMigrationTest`, `SettingsRepositoryTest.v1ThemeValueIsReadUnchanged`, `TypographyTest.literataIsTheDefault` | installation par-dessus la V1 (`installDebug`, sans effacer les données) : livres, carte Reprendre, journal, texte en Literata | — | ☑ |
| 2 | 11 | Atkinson puis police du système : toute l’app et le texte changent, le choix survit au redémarrage | `TypographyTest`, `VersoReadingPreferencesTest.systemFontIsTheWebViewSansSerif` | cartes Police de la feuille « Aa » (étape 11 ; Paramètres à l’étape 14), captures bibliothèque et lecture ; arrêt forcé puis relance | redémarrage du téléphone | ☐ |
| 3 | 11 | Taille, interligne, marges en lecture : effet immédiat, même paragraphe | `ReaderViewModelTest.settingsAndThemeAreSubmittedWithoutMovingTheReadingPosition`, `VersoReadingPreferencesTest.userSettingsAreApplied` | feuille « Aa » : capture avant/après, même premier paragraphe | — | ☐ |
| 4 | 10 | Sépia et Noir sur toute l’app et le texte, sans zone d’une autre couleur ; Automatique suit le téléphone | `V2ScreenshotTest`, `ThemeModeTest` | captures bibliothèque, fiche, Paramètres, lecture, barres, sommaire, journal en sépia et en noir | Automatique avec le thème du téléphone changé | ☐ |
| 5 | 10 | Contraste ≥ 7:1 dans les cinq thèmes | `PaletteContrastTest` (quatre palettes) | — | — | ☐ |
| 6 | 15 | Barre : Sommaire, Journal, Rechercher, Réglages avec texte | | | | ☐ |
| 7 | 12 | Feuille « Aa » à mi-hauteur sans voile, effet en direct, réglages du bas en glissant | | | | ☐ |
| 8 | 12 | Mode pages : swipe et tap latéral, aucune ligne coupée, pied de page | | | | ☐ |
| 9 | 12 | Mode mémorisé par livre, position gardée au changement de mode | | | | ☐ |
| 10 | 12 | Feuilletage rapide = navigation et carte « Revenir » ; lecture page par page = progression | | | | ☐ |
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
