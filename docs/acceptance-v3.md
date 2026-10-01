# Verso V3 — checklist d’acceptation

Référence : « Critères d’acceptation V3 » de `docs/SPEC.md`. Une case de la spec n’est cochée que lorsque **toutes** les vérifications de sa ligne sont faites et conformes.

- **Auto** : test automatisé, lancé par `./gradlew :core:test :app:testDebugUnitTest`.
- **Claude** : fait par Claude sur le téléphone (adb Wi-Fi, `ANDROID_SERIAL=192.168.1.10:5555`) sans toucher aux réglages système (captures dans `build/acceptance/<horodatage>/`).
- **Maxime** : fait à la main par Maxime (réglage système, redémarrage, écoute TalkBack).

| # | Étape | Critère | Auto | Claude | Maxime | Résultat |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 17 | Appui long : barre « Surligner / Note / Copier » à la place du menu d’Android, début de la sélection | `SelectionBarTest`, `SelectionActionModeCallbackTest`, `V3ScreenshotTest` (3.04) | aucun menu d’Android, poignées présentes, « Sélection : « the » » puis le début de la sélection étendue, en continu et en pages (2026-10-01, `build/acceptance/20261001-e17/` 03, 04, 21) ; « Note » arrive à l’étape 18 | — | ☑ |
| 2 | 17 | Surligner : fond et soulignement dans les cinq thèmes, gardé au redémarrage, continu et pages | `HighlightDecorationTest`, `HighlightCoordinatorTest`, `HighlightDaoTest`, `VersoDatabaseMigrationTest` | fond et soulignement en Sépia, Clair, Sombre, Nuit (Automatique = l’un d’eux), présent après un arrêt forcé, en continu et en pages (05, 16, 18, 22) | redémarrage complet du téléphone | ☑ |
| 3 | 17 | Copier : passage dans le presse-papiers | `HighlightCoordinatorTest.copyEmitsThePassageWithoutCreatingAnything` | aperçu du presse-papiers d’Android avec le passage (10) | — | ☑ |
| 4 | 18 | Note : feuille avec le passage ; Enregistrer crée, Annuler ne crée rien | | | | ☐ |
| 5 | 17 | Passage qui en recoupe un autre : un seul surlignage, notes gardées | `HighlightMergeTest`, `TextQuotesTest`, `HighlightCoordinatorTest.overlappingHighlightMergesIntoOne` | « clothes » étendu jusqu’à « deliveries » par-dessus un surlignage : un seul surlignage, plus long (06 à 08) ; notes gardées vérifiées par les tests (la note arrive à l’étape 18) | — | ☑ |
| 6 | 18 | Toucher un surlignage : Modifier la note, Supprimer, Copier ; Annuler rétablit | | feuille « Surlignage » avec le passage et « Copier » (09) | | ☐ |
| 7 | 17 | Sélection, poignées, toucher un surlignage : position de lecture inchangée, pas de carte | `ReadingPositionTrackerTest` (sélection), `ReadingPositionCoordinatorTest.selectionNeverMovesReadingNorShowsTheCard`, `FragmentReaderControllerTest` (sélection) | 5 % lu avant et après sélections, poignées, surlignages, toucher d’un surlignage et toucher qui annule ; jamais de carte « Revenir » ni de barre de lecture (02, 11, 15) | — | ☑ |
| 8 | 19 | « Notes et surlignages » depuis la fiche et la barre ; ordre, chapitre, pourcentage ; aller au passage et « Revenir » | | | | ☐ |
| 9 | 19 | Exporter : Markdown lisible, groupé par chapitre | | | | ☐ |
| 10 | 20 | Créer une sauvegarde : zip, carte date, taille, nom | | | | ☐ |
| 11 | 20 | Restaurer après suppression de deux livres : tout revient à l’identique | | | | ☐ |
| 12 | 20 | Fichier invalide ou restauration interrompue : bibliothèque intacte, message | | | | ☐ |
| 13 | 21 | Texte à 200 % et commandes ≥ 48 dp intitulées sur les nouveaux écrans | | | | ☐ |
| 14 | 21 | Aucune permission réseau ni autre | `NoPermissionsTest` | | | ☐ |

Remarque (étape 17) : une poignée tirée vers le bas de l’écran s’arrête sous la barre de sélection ; le texte ne défile pas tout seul. La machine à états ignore de toute façon ce défilement (tests).
