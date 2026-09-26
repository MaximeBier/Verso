# Verso V1 — checklist d’acceptation

Référence : « Critères d’acceptation V1 » de `docs/SPEC.md`. Une case de la spec n’est cochée que lorsque **toutes** les vérifications de sa ligne sont faites et conformes.

- **Auto** : test automatisé (Robolectric, Roborazzi, JUnit), lancé par `./gradlew :core:test :app:testDebugUnitTest`.
- **Claude** : fait par Claude sur le téléphone `29081JEGR09520` sans toucher aux réglages système (`scripts/acceptance-phone.sh`, captures dans `build/acceptance/<horodatage>/`).
- **Maxime** : fait à la main par Maxime, parce qu’il faut changer un réglage système, redémarrer le téléphone ou utiliser une autre application. Après chaque vérification qui change un réglage, remettre le réglage habituel.

Fichiers de test à copier sur le téléphone ou sur Drive : `app/src/test/resources/epub/real/`.

| # | Critère | Auto | Claude | Maxime | Résultat |
| --- | --- | --- | --- | --- | --- |
| 1 | Premier lancement : un seul bouton « Importer un EPUB », aucun choix | capture `1.01-premier-lancement-*`, `AccessibilityTreeTest[1.01…]`, `EmptyLibraryScreenTest` (2.2) | — (exige une installation propre : effacer les données est interdit à Claude) | procédure 1 | ☐ |
| 2 | Import par le sélecteur, titre, auteur, couverture en moins de 5 s | `EpubImporterTest` (3.1), `RealCorpusTest` (7.2) | procédure 2C : conforme (Les Fleurs du mal 0,58 s, vignette générée ; Candide, Bovary, Notre-Dame 0,17–0,31 s avec couverture, rapport du correctif D) | facultative | ✅ |
| 3 | « Ouvrir avec » depuis Drive, sans étape supplémentaire | `IncomingIntentTest`, `IntentFiltersTest` (3.2) | — (autre application) | procédure 3 | ☐ |
| 4 | Un chapitre entier en scroll continu, sans saccade | — | procédure 4C : conforme (105 glissés lents, Deuxième partie I → II → III en entier : 0,12 % d’images en retard, 99ᵉ centile 13 ms, aucune carte) ; frontière de fichier continue (correctif B, revérifié) | facultative (œil humain) | ✅ |
| 5 | Fermeture brutale au milieu d’un paragraphe : même paragraphe à la réouverture | `ReaderViewModelTest.restoresSavedLocatorAfterRecreation` (4.3), `PositionSaverTest` (5.4) | procédure 5C : conforme (balayage depuis les récents, même paragraphe, décalage d’environ une ligne et demie) | facultative | ✅ |
| 6 | Redémarrage du téléphone : position intacte | persistance Room (`BookDaoTest`, 2.3) | — (redémarrage interdit à Claude) ; réouverture après arrêt forcé et après réinstallation : conforme | procédure 6 | ☐ |
| 7 | Scroll violent de 30 pages : progression inchangée, « Revenir » en un tap | `ReadingPositionTrackerTest.violentThirtyPageScrollNeverMovesReadingAndGoBackReturnsInOneTap` (5.1), `ReturnCardTest` (5.3) | script, section 4 : conforme dans un même fichier (`14b` = `10`, au pixel près, 6 essais) ; après un scroll qui change de fichier, anomalie F2 corrigée (correctif F, vérifié sur le téléphone : « Revenir » en un tap, captures identiques au pixel, rapport `fix-f-report.md`) ; revérification sur le build final de la revue finale non faite (téléphone verrouillé) | procédure 7 | ☐ |
| 8 | Même scroll puis fermeture sans toucher la carte : retour à la position de lecture | `ReadingPositionTrackerTest` (5.1), `PositionSaverTest` (5.4) | procédure 8C : conforme (balayage depuis les récents, réouverture identique au pixel à la position de lecture) ; script, section 5 : `15` = `14b` | facultative | ✅ |
| 9 | Saut au début par le sommaire : la carte ramène au chapitre d’origine | `ReadingPositionTrackerTest.jumpToBeginningThenGoBackReturnsToReading` (5.1), `ReaderViewModelTest.tocJumpShowsReturnCardAndGoBackReturnsToReading`, `TocSheetTest` (4.2) | procédure 9C : non conforme avant le correctif F (deux taps) ; anomalie F1 corrigée (correctif F, vérifié sur le téléphone : un seul tap, carte disparue, captures identiques au pixel, rapport `fix-f-report.md`) ; revérification sur le build final de la revue finale non faite (téléphone verrouillé) | procédure 9 | ☐ |
| 10 | Après 25 s de lecture réelle ailleurs, la carte disparaît et la progression suit | `ReadingPositionTrackerTest.confirmationAfter25SecondsOfReadingAtTheNewPlace` et suivants (5.1, correctif A) | procédure 10C : conforme (après fling 77 → 83 % et après sommaire 94 → 19 %) ; correctif A revérifié : 30 s d’attente puis un petit glissé ne confirment pas | facultative | ✅ |
| 11 | Deux sessions au journal avec heure, durée, passage ; « Reprendre ici » mène à leur fin | `SessionCoordinatorTest.twoSessionsArePersistedSeparately`, `JournalMappingTest`, `JournalSheetTest.tappingFinishedSessionResumesAtItsEnd`, `JournalResumeTest` (6.2, 6.4) | procédure 11C : conforme (3 puis 4 sessions, heures, durée, passage « Chap. XI → III », « x → y % » ; « Reprendre ici » mène à la fin de chacune, carte « Revenir ») | facultative | ✅ |
| 12 | Taille de police changée dans le code : même paragraphe restauré | `ReadiumOpenerTest`, `LocatorsTest` (4.1) | procédure 12C : conforme (`12a` / `12b`) | — | ✅ |
| 13 | Titre corrigé : la correction survit à un redémarrage | `DetailsViewModelTest.blankTitleKeepsPrevious`, `DetailsViewModelTest.titleIsSavedTrimmedAfterDebounce` (3.4) | — (redémarrage interdit à Claude) | procédure 13 | ☐ |
| 14 | Fichier non EPUB refusé, message, aucune trace | `EpubImporterTest.textFileIsRejectedWithoutTrace`, `orphanFileIsDeletedOnFailure` (3.1), capture `1.06-fichier-refuse-*` | procédure 14C : conforme (fenêtre « Impossible d’importer ce fichier », bibliothèque inchangée, `files/books` et `cache/import` sans reste) | facultative | ✅ |
| 15 | Thème sombre du système sans zone blanche | captures `*-sombre` de `V1ScreenshotTest` (7.1) | procédure 15C : conforme (téléphone déjà en thème sombre, aucun réglage touché : bibliothèque liste et grille, carte « Reprendre », menu ⋮, fiche, suppression, Paramètres, Licences, import refusé, lecture, barres, sommaire, journal, carte « Revenir ») | facultative | ✅ |
| 16 | Texte Android à 200 % : rien de coupé, aucun scroll horizontal | `AccessibilityTreeTest[*-texte-200]`, captures `*-texte-200` (7.1) | — (réglage système) | procédure 16 | ☐ |
| 17 | Commandes ≥ 48 dp, boutons à icône seule intitulés pour TalkBack | `AccessibilityTreeTest` (7.1), `ReturnCardTest.buttonsInvokeCallbacksAndAreAtLeast48Dp` | procédure 17C : toutes les zones cliquables ≥ 126 px (48 dp) et les icônes seules ont un `content-desc` sur 11 écrans ; l’écoute TalkBack reste à faire | procédure 17 | ☐ |
| 18 | Aucune permission hormis le sélecteur de fichiers | `NoPermissionsTest`, `scripts/check-no-internet.sh` (CI, 2.4), `scripts/acceptance-phone.sh --apk-only` (`aapt2 dump permissions`) | script, section 7 : conforme (`requested permissions` = seule la permission interne d’AndroidX, `install permissions` vide) | facultative (écran Autorisations) | ✅ |

## Procédures

### 1. Premier lancement (Maxime ; efface la bibliothèque du téléphone, à faire en premier ou pas du tout)
1. Réglages Android › Applications › Verso › Stockage et cache › Effacer le stockage.
2. Ouvrir Verso.
3. Attendu : « Votre bibliothèque est vide », une phrase d’explication, un seul bouton « Importer un EPUB », la ligne sur « Ouvrir avec Verso », pas de bouton « Importer » en haut, aucune question (police, thème, compte).

### 2. Import par le sélecteur (Maxime)
1. Copier `gutenberg-14155-madame-bovary-fr.epub` et `wikisource-les-fleurs-du-mal-1868-fr.epub` dans le dossier Téléchargements du téléphone.
2. Dans Verso, toucher « Importer » (ou « Importer un EPUB »), choisir Madame Bovary, lancer un chronomètre au moment du choix.
3. Attendu : en moins de 5 s, « Madame Bovary », « Gustave Flaubert » et la couverture de l’EPUB dans la liste ; snackbar « « Madame Bovary » a été ajouté. » avec « Commencer ».
4. Recommencer avec Les Fleurs du mal : vignette générée (pas de couverture dans ce fichier).

### 3. « Ouvrir avec » depuis Drive (Maxime)
1. Déposer `gutenberg-4650-candide-fr.epub` sur Google Drive.
2. Dans l’application Drive : ⋮ du fichier › Ouvrir avec › Verso.
3. Attendu : Verso s’ouvre, le livre est ajouté, snackbar « « Candide, ou l'optimisme » a été ajouté. », aucune autre étape.

### 4. Lecture continue (Maxime)
1. Ouvrir Madame Bovary et lire un chapitre entier en faisant défiler lentement.
2. Attendu : aucune saccade, aucun saut de mise en page, y compris au passage au chapitre suivant ; texte aligné à gauche, sans césure.

### 5. Fermeture brutale (Maxime) — 5C : variante de Claude
1. Au milieu d’un paragraphe, retenir les trois premiers mots de la ligne du haut.
2. Ouvrir les applications récentes et balayer Verso vers le haut.
3. Rouvrir Verso. Attendu : le même paragraphe est à l’écran.
4. **5C (Claude)** : deux petits scrolls de lecture (`adb -s 29081JEGR09520 shell input swipe 540 1600 540 1200 900`), attendre 1 s, capturer ; `adb -s 29081JEGR09520 shell input keyevent KEYCODE_APP_SWITCH`, balayer la vignette de Verso (`input swipe 540 1300 540 200 200`), vérifier que le processus est mort (`pidof com.maximebier.verso` vide) ; relancer (`am start -n com.maximebier.verso/.MainActivity`), capturer : même paragraphe en haut des deux captures.

### 6. Redémarrage (Maxime)
1. Retenir le paragraphe affiché, revenir à l’écran d’accueil.
2. Bouton marche › Redémarrer. Déverrouiller, ouvrir Verso.
3. Attendu : Verso rouvre le livre (lu il y a moins de 24 h) au même paragraphe.

### 7. Scroll violent (Maxime)
1. Toucher le centre, noter « xx % lu », toucher le centre pour masquer la barre.
2. Faire une série de flings rapides (environ 30 pages).
3. Attendu : la carte « Vous avez quitté votre lecture » apparaît ; au centre, la barre affiche toujours le même pourcentage.
4. Toucher « Revenir ». Attendu : retour au paragraphe de départ en un tap.
5. Recommencer en changeant de fichier : dans Madame Bovary, lire au début de la Troisième partie, chapitre VII (environ 83 %), puis flings vers le haut jusqu’à la Troisième partie, chapitre V (environ 77 %, fichier précédent). Attendu : « Revenir » ramène au paragraphe de départ, pas un écran plus haut (anomalie F2).

### 8. Scroll accidentel puis fermeture (Maxime)
1. Comme 7.1–7.3, sans toucher la carte.
2. Balayer Verso depuis les récents, le rouvrir.
3. Attendu : la position de lecture (le paragraphe d’avant le scroll), pas l’endroit du scroll.

### 9. Saut par le sommaire (Maxime)
1. Au milieu du livre, toucher le centre › « Sommaire » › premier chapitre.
2. Attendu : saut au début ; la carte « Revenir » apparaît.
3. Toucher « Revenir ». Attendu : retour au chapitre d’origine.
4. Cas qui échouait (anomalie F1) : Madame Bovary, lecture au début de la Troisième partie, chapitre XI (94 %), Sommaire › « PREMIÈRE PARTIE ». Attendu : un seul tap sur « Revenir » ramène au chapitre XI.

### 10. Acceptation automatique (Maxime)
1. Après un saut par le sommaire (procédure 9, sans toucher la carte), lire au nouvel endroit en petits scrolls pendant 30 s.
2. Attendu : la carte disparaît seule ; au centre, le pourcentage correspond au nouvel endroit.

### 11. Journal (Maxime)
1. Lire 2 minutes, revenir à l’écran d’accueil, attendre une minute, rouvrir Verso, lire 2 minutes.
2. Toucher le centre › « Journal ».
3. Attendu : sous « Aujourd’hui », une session « En cours » (sans action) et la précédente avec heures de début et de fin, durée, passage, « x → y % », « Reprendre ici ».
4. Toucher la session précédente. Attendu : la feuille se ferme, le texte est à la fin de cette session (carte « Revenir » si c’est à plus d’un écran).

### 12C. Taille de police changée dans le code (Claude, sans réglage système)
1. Ouvrir un livre au milieu d’un chapitre, capturer (`12a.png`).
2. Dans `ReadingStyle.kt`, remplacer `READING_FONT_SIZE_SP = 19.0` par `19.0 * 1.25` (valeur par défaut de `VersoReadingPreferences.reflowableWeb`) ; `export JAVA_HOME="$LOCALAPPDATA/Programs/jdk17" && ./gradlew :app:installDebug` ; relancer Verso (réouverture automatique) ; capturer (`12b.png`).
3. Attendu : le paragraphe en haut de `12a.png` est visible en haut de `12b.png`.
4. Remettre la valeur d’origine, réinstaller, **ne pas committer** la modification.

### 13. Titre corrigé (Maxime)
1. ⋮ d’un livre › « Détails du livre » › champ Titre : ajouter « (test) », revenir.
2. Redémarrer le téléphone (peut être combiné avec la procédure 6), rouvrir Verso.
3. Attendu : le titre corrigé dans la bibliothèque et la fiche. Remettre le titre d’origine.

### 14. Fichier refusé (Maxime)
1. Copier un PDF quelconque sur le téléphone et le renommer `faux.epub`.
2. Dans Verso : « Importer » › `faux.epub`.
3. Attendu : fenêtre « Impossible d’importer ce fichier », « « faux.epub » n’est pas un livre EPUB. … », « Rien n’a été ajouté à votre bibliothèque. », bouton « Compris » ; la bibliothèque est inchangée.

### 15. Thème sombre (Maxime)
1. Réglages Android › Affichage › Thème sombre : activé.
2. Parcourir bibliothèque (liste et grille), menu ⋮, fiche, suppression (annuler), Paramètres, Licences, lecture, barre, sommaire, journal, carte « Revenir ».
3. Attendu : aucune zone blanche éblouissante, aucun texte illisible. Remettre le réglage habituel.

### 16. Texte à 200 % (Maxime)
1. Réglages Android › Affichage › Taille de la police : au maximum (200 % sur Android 14 et plus).
2. Parcourir les mêmes écrans qu’en 15.
3. Attendu : aucun texte coupé, aucune ligne à faire défiler horizontalement, tous les boutons atteignables. Remettre le réglage habituel.

### 17. TalkBack (Maxime)
1. Réglages Android › Accessibilité › TalkBack : activé.
2. Parcourir les écrans en balayant vers la droite.
3. Attendu : chaque bouton à icône annonce un intitulé (« Paramètres », « Options pour « … » », « Retour », « Retour à la bibliothèque », « Fermer », « Liste », « Grille ») ; l’interrupteur annonce « Rouvrir le dernier livre », son état et son rôle ; la carte « Reprendre » est lue d’un bloc. Désactiver TalkBack.

### 18. Permissions (Maxime)
1. Réglages Android › Applications › Verso › Autorisations.
2. Attendu : « Aucune autorisation demandée ».

## Procédures de Claude (variantes « C », sans réglage système)

Téléphone `29081JEGR09520` (1080 × 2400 px, 420 dpi, soit 2,625 px par dp ; thème système sombre, laissé tel quel). Gestes par `adb shell input` : « petit glissé » = `input swipe 540 1400 540 1250 600` ; « glissé de lecture » = `input swipe 540 1700 540 1000 800` ; « fling » = `input swipe 540 1920 540 480 60` (vers le bas du livre) ou `540 480 540 1920 60` (vers le haut). Boutons trouvés par `uiautomator dump`. Deux captures sont dites « identiques » quand leur `md5sum` est le même.

- **2C** : pousser `wikisource-les-fleurs-du-mal-1868-fr.epub` dans `/sdcard/Download`, `logcat -c`, « Importer » › le fichier, capturer toutes les 0,35 s ; lire la ligne `VersoImport` (`total=`). Attendu : livre, auteur, couverture ou vignette et snackbar en moins de 5 s.
- **4C** : `dumpsys gfxinfo com.maximebier.verso reset`, glissés de lecture espacés de 0,4 s sur un chapitre entier, `dumpsys gfxinfo` ; attendu : moins de 1 % d’images en retard, aucune carte, chapitre suivant atteint.
- **8C** : lecture, capture ; 12 flings (carte affichée) ; récents › balayer Verso ; relancer ; capture identique à la première.
- **9C** : lecture, capture ; Sommaire › « PREMIÈRE PARTIE » ; carte ; « Revenir » ; capture identique à la première, carte disparue.
- **10C** : après un fling ou un saut par le sommaire, petits glissés toutes les 2 à 5 s jusqu’à disparition de la carte, puis barres : le pourcentage est celui du nouvel endroit. Contrôle du correctif A : carte laissée 30 s, un seul petit glissé : la carte reste et le pourcentage ne bouge pas.
- **11C** : sessions séparées par un balayage depuis les récents (la fermeture termine la session) ; Journal ; toucher chaque session terminée et comparer à la capture prise à la fin de cette session.
- **14C** : pousser un faux PDF nommé `verso-test-faux.epub`, l’importer ; `run-as com.maximebier.verso ls files/books files/covers cache/import` avant et après.
- **15C** : parcourir les écrans listés en procédure 15 dans le thème sombre déjà actif et regarder chaque capture.
- **17C** : sur chaque écran, `uiautomator dump`, lister les nœuds `clickable="true"` de moins de 126 px de côté et les icônes sans `content-desc`.

## Anomalies trouvées

### F — « Revenir » vers un autre fichier du livre atterrit trop haut (corrigée)

Corrigée par le correctif F (retenue de la position affichée après un saut vers un autre fichier, puis recalage une fois la page mise en page) et vérifiée sur le téléphone : F1 et F2 en un tap, captures identiques au pixel, y compris avec un tap au centre pendant la retenue (`.superpowers/sdd/2026-09-25-verso-v1/fix-f-report.md`). Constat d'origine :

Livre : Madame Bovary (Gutenberg 14155, un fichier XHTML par groupe de chapitres). Dans un même fichier, « Revenir » est exact au pixel près (6 essais, dont des flings de 30 à 40 écrans vers le haut et vers le bas). Quand la lecture est dans un autre fichier que l’endroit affiché :

- **F1 (critère 9)** : lecture au début de la Troisième partie, chapitre XI (94 %) ; Sommaire › « PREMIÈRE PARTIE » (ou « MADAME BOVARY ») ; carte « Votre lecture : Chap. XI · 94 % » ; « Revenir » → le texte affiché est l’enterrement d’Emma (fin du chapitre X, plusieurs écrans plus haut) et **la carte reste affichée**. Un second tap sur « Revenir » arrive exactement au chapitre XI. Reproduit deux fois, captures identiques au pixel (`c9-revenir.png`, `f-revenir2.png`).
- **F2 (critère 7)** : même lecture ; Sommaire › Deuxième partie XIII ; « Revenir » → un écran plus haut que la lecture (le paragraphe lu est juste sous le bas de l’écran) ; la carte disparaît (moins d’un écran d’écart), la lecture n’a pas bougé (un fling puis « Revenir » ramène ensuite exactement). Observé aussi après 12 flings vers le haut depuis la Troisième partie, chapitre VII (83 %), probablement à travers la frontière de fichier de la Troisième partie, chapitre VI : « Revenir » un écran trop haut, puis, après un toucher au centre, la lecture a suivi ce nouvel endroit (un écran en arrière).
- Piste : `goTo` vers une ressource qui n’est pas encore mise en page (hauteur de la WebView provisoire) ; le prototype notait déjà que la position n’est fiable qu’une fois la page stabilisée. Un recalage après stabilisation, comme pour les sauts du sommaire vers une ancre (correctif C), réglerait probablement les deux cas.

Captures : dossier `acc/` du répertoire temporaire de la session de Claude (non versionné).

### Remarques sans défaut

- Balayer vite vers le bas dans le sommaire ferme la feuille dès que la liste arrive en haut, et le reste du geste fait défiler le texte (lecture). Comportement standard d’une feuille modale.
- Dans le journal, une session commencée sur la page de titre (0 %, hors sommaire) n’affiche que le chapitre de fin (« Chap. XI »).
- La vignette générée des Fleurs du mal est conforme (le fichier n’a pas de couverture). Les couvertures Gutenberg ont un haut blanc : c’est l’image du livre, pas l’interface.

## Résultats

| Date | Qui | Critères vérifiés | Remarques |
| --- | --- | --- | --- |
| 2026-09-26 | Claude (tâche 7.3), build `master` f1e8542 | 2, 4, 5, 8, 10, 11, 12, 14, 15, 18 conformes ; 7 et 9 non conformes (anomalie F) ; 17 partiel (48 dp et intitulés présents, écoute TalkBack à faire) | 544 tests, lint et `assembleDebug` verts ; correctifs A, B, C, D revérifiés sur le téléphone ; restent à Maxime : 1, 3, 6, 13, 16, 17, puis 7 et 9 après correction de F |
| 2026-09-26 | Claude (revue finale, branche `wip/final-fixes`) | aucun critère revérifié sur le téléphone : il s’est verrouillé (empreinte) pendant la passe ; 7 et 9 restent à cocher après revérification sur le build final | anomalie F corrigée et vérifiée pendant le correctif F ; 572 tests, lint et `assembleDebug` verts ; sauvegarde Android désactivée, carte « Reprendre » avec chapitre et extrait, livre refusé par le moteur ramené à la bibliothèque avec un message |
