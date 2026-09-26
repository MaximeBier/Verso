# Lecteur : passage au navigateur classique de Readium

Date : 2026-09-26. Décidé avec Maxime après mesure et prototype.

## Pourquoi

Retour utilisateur : le défilement manque de fluidité. Mesure sur le Pixel 6a (60 Hz) : un glissé lent tient 60 images/s, mais le fling du navigateur Compose de Readium (`readium-navigator-web-reflowable`, expérimental) ne dessine qu'une vsync sur deux (30 images/s), en debug comme en release. Causes exclues par des tests : build debug, calque matériel, code de Verso (position, gestes), WebView (son fling natif tient 60), animation Compose seule, pager. La cause exacte est interne au navigateur Compose.

Prototype (branche `spike/fragment-navigator`, jamais fusionnée) : le navigateur classique `EpubNavigatorFragment`, qui laisse la WebView défiler elle-même, tient 60 images/s sur 4 flings sur 4 (aucune image perdue), avec le même rendu (police, couleurs, marges).

Contrepartie acceptée : le défilement n'est plus continu entre chapitres. On change de chapitre par un glissé de plus au bord, sans texte explicatif (l'étirement de fin de défilement d'Android suffit comme retour).

## Comportement

- Défilement continu **dans** un chapitre, fling natif de la WebView.
- **Chapitre suivant** : un glissé vers le haut **commencé au bord bas** du chapitre et plus long que `ReaderGestures.CHAPTER_CHAIN_DRAG_DP` ouvre le début du chapitre suivant (titre en haut de l'écran). **Chapitre précédent** : glissé vers le bas commencé au bord haut, ouvre la fin du chapitre précédent. L'élan d'un fling qui atteint le bord ne change jamais de chapitre. Premier et dernier fichiers : rien ne se passe.
- Aucune indication affichée en fin de chapitre. L'effet d'étirement de fin de défilement n'est pas désactivé.
- Pour la machine à états, un changement de chapitre est de la **lecture**, pas un saut. Il déplace l’affiché d’environ un écran en quelques millisecondes, ce qui ressemblerait à une navigation (seuil de la carte : 1 écran) : il est donc signalé explicitement (`GestureSignal.chapterTurn` → `GestureEnded.chapterTurn`). En suivi, la lecture passe à la nouvelle position, sans carte ; en AWAY, la carte reste et le point d’arrivée suit.
- Sommaire, journal, « Revenir » et liens internes restent des sauts explicites, inchangés. Le sommaire vise l'ancre exacte (le navigateur classique suit les fragments).
- Extrait de la carte « Reprendre » : texte réellement visible (`firstVisibleElementLocator`), à la place de l'estimation par progression.
- Réglages imposés inchangés : Atkinson, 19 sp, interligne 1,6, alignement à gauche sans césure, CSS de l'éditeur écarté, couleurs des thèmes clair et sombre, graisse allégée en sombre, marges.
- Insets constants (correctif G) : le texte est placé sous les barres système même masquées ; barres de lecture et système en surcouche.

## Architecture

- `ReaderController` (interface vue par le reste de l’app) ne change que par le champ `chapterTurn` de `GestureSignal`. Machine à états, `ReadingPositionCoordinator`, `PositionSaver`, `SessionCoordinator`, barres, sommaire, journal et carte « Revenir » ne changent pas.
- `ReaderSurface.kt` : le navigateur Compose est remplacé par `EpubNavigatorFragment` dans un `AndroidFragment`. La fabrique du fragment est posée avant sa création ; recréation de l'activité (thème, rotation) vérifiée sur le téléphone.
- Nouveau contrôleur `FragmentReaderController` (remplace `ReflowableReaderController`) :
  - `displayed` : `currentLocator` du navigateur, avec la progression totale fine (`ReadingOrderPositions`) ;
  - `gestures` : observateur de gestes actuel conservé (fling ou lecture au relâchement, stabilisation) ;
  - tap au centre : `InputListener.onTap` du navigateur, plus l'observateur si le navigateur ne le signale pas ;
  - `go` : `navigator.go(locator, animated = false)` ; la retenue et le recalage des sauts vers un autre fichier (anomalie F) sont retirés, à revérifier sur le téléphone ;
  - `excerptLocator` : `firstVisibleElementLocator()`.
- Décision de changement de chapitre : fonction pure `chapterChain(atTopEdge, atBottomEdge, dragDy, thresholdPx)` → `Next` / `Previous` / `None`, testée seule. Bords lus par JavaScript au début du glissé.
- Supprimés : `ScreenTop.kt`, la lecture par réflexion du pager de Readium, la dépendance `readium-navigator-web-reflowable`, les constantes de retenue (`JUMP_SETTLE_QUIET_MS`, `JUMP_REALIGN_TOLERANCE`) si elles ne servent plus.

## Tests

- Supprimés avec leur code : `ScreenTopTest`, les cas de retenue et de recalage de `ReflowableReaderControllerTest`.
- Nouveaux : `chapterChain` (bords, sens, seuil, pas de saut sur l'élan), `FragmentReaderController` avec un faux navigateur (position affichée et progression totale, stabilisation des gestes, tap au centre, changement de chapitre sans `Jumped`, extrait visible).
- Inchangés : machine à états, coordinateurs, écrans.
- Téléphone : fling à 60 images/s (4 flings, `dumpsys gfxinfo framestats`) ; critères V1 de lecture revérifiés : 4 (lecture sans saccade), 5 (fermeture brutale), 7 et 8 (scroll accidentel, « Revenir »), 9 (sommaire), 10 (25 s de lecture), 11 (journal), 15 (thème sombre), 16 (texte à 200 %) ; changement de thème et rotation pendant la lecture.

## Spec générale

`docs/SPEC.md` : choix du moteur et point de vigilance n° 1 (navigateur classique, chapitres enchaînés par glissé au bord), comportement de lecture, historique du 2026-09-26.
