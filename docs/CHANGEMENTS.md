# Changements depuis le kit du 2026-09-25

À lire par l'agent de code avant de reprendre le travail. Ce fichier liste tout ce qui a changé dans `SPEC.md` et `design/` le 2026-09-29. Si une étape est déjà codée, elle doit être mise à jour selon la liste « À faire » plus bas. Sinon, il suffit de suivre la spec, qui est déjà à jour.

## 1. Le logo dans l'interface

| Où | Avant | Maintenant | Écrans |
| --- | --- | --- | --- |
| En-tête de la bibliothèque | Texte « Verso », 28 sp, gras | **Logotype** : le V du logo + « erso », en tracés vectoriels, environ 21 dp de haut, intitulé TalkBack « Verso » | 1.01, 1.02, 1.02 sombre, 1.02b, 1.03, 1.04, 1.05, 1.06, 2.07, 2.07b, 3.01, 4.01 |
| Bibliothèque vide (1.01) | Pictogramme de livre ouvert, trait, 56 dp | **Icône de l'app**, 64 dp, masque arrondi, décorative (ignorée par TalkBack) | 1.01 |
| Paramètres › À propos (1.09) | Ligne « Version » / « 1.0.0 » | Icône 40 dp, puis « Verso » en titre et « Version 1.0.0 » en dessous | 1.09 |

Mise en œuvre :

- Logotype = **un seul vector drawable** (`res/drawable/ic_logotype.xml`), converti depuis `design/logo/svg/verso-logotype-clair.svg`. Il contient deux tracés : le V et « erso ».
- La couleur de chaque tracé vient du thème : le V en `accent`, « erso » en `text`. Il n'y a qu'un seul fichier pour tous les thèmes.
- Le logotype garde sa forme quelle que soit la police de l'app, et sa taille est fixe en dp : il ne suit pas la taille de texte Android.
- L'icône des écrans 1.01 et 1.09 reprend les calques de l'icône de lancement (fond `ic_launcher_background` + `ic_launcher_foreground`), avec un masque aux coins arrondis.

## 2. Thèmes foncés : Sombre adouci, et Nuit à la place de Noir

Raison : avec un contraste de 14:1, le texte clair crée un halo sur le fond foncé (halation), surtout avec un astigmatisme. Le texte vise maintenant 9 à 10:1, et tout texte reste à au moins 7:1.

- **Sombre** (gris chaud) : toutes les valeurs changent.
- **Nuit** (brun, texte beige ambré) : nouveau thème, qui **remplace le thème Noir**. Le Noir disparaît du projet, y compris de la V2.
- **Dès la V1**, quand le système est en sombre, l'app applique le thème foncé choisi dans les Paramètres : Sombre (par défaut) ou Nuit.

| Jeton (`tokens.json`) | Sombre avant | Sombre maintenant | Nuit (nouveau) |
| --- | --- | --- | --- |
| `background` | `#171513` | `#1E1B18` | `#1D1813` |
| `surface` | `#22201D` | `#25221E` | `#241E18` |
| `surfaceHigh` | `#2B2825` | `#2D2924` | `#2C251E` |
| `text` | `#E8E2D8` | `#CBC5BC` | `#CFBCA0` |
| `textSecondary` | `#CFC7BB` | `#BBB5AD` | `#C1B095` |
| `accent` | `#E8C48E` | `#CEB28A` | `#D6AA7A` |
| `onAccent` | `#1B1510` | `#2E271F` | `#2D2319` |
| `progressTrack` | `#3A3530` | `#3D3933` | `#3C342B` |
| `outline` | `#8E857A` | `#7B7670` | `#7E7260` |
| `divider` | `#3A3530` | `#3D3933` | `#3C342B` |
| `selection` | `#463727` | `#534331` | `#4E3B28` |
| `onSelection` | `#F2E3D0` | `#EADFCF` | `#E0D3C3` |
| `danger` | `#F2A99E` | `#E8A89C` | `#E5A48D` |
| `inverse` | `#E8E2D8` | `#CBC5BC` | `#CFBCA0` |
| `onInverse` | `#1F1B16` | `#1E1B18` | `#1D1813` |
| `inverseAccent` | `#7A3021` | `#4F2B22` | `#4C241A` |
| `highlight` | `#5A4520` | `#362D1D` | `#352A19` |

Le thème clair et le sépia ne changent pas.

### Typographie en thème foncé (Sombre et Nuit)

| Réglage | Avant | Maintenant |
| --- | --- | --- |
| Interligne du texte de lecture | 1,6 | **1,7** |
| Espacement des lettres | Atkinson +0,01 em, Literata +0,005 em | **Atkinson +0,02 em, Literata +0,015 em** |
| Graisse | Atkinson 380, Literata 370 | Inchangée |
| Espace entre paragraphes | 0,5 × hauteur de ligne | Inchangé (donc 16 dp en Atkinson 19 sp) |

Ces valeurs s'appliquent aux réglages Readium (couleurs du texte et du fond, interligne, espacement des lettres, graisse), pas seulement aux écrans Compose.

### Nouveau réglage (1.09 Paramètres › Affichage)

- Sous la phrase existante : libellé « Thème sombre » et un bouton segmenté « Sombre / Nuit » à deux choix, avec « Sombre » coché par défaut.
- Préférence DataStore, par exemple `darkThemeVariant` = `SOMBRE` | `NUIT`.
- Le changement s'applique tout de suite, y compris dans un livre ouvert.
- Le thème appliqué se résout ainsi :
  - système en clair → clair ;
  - système en sombre → le choix Sombre ou Nuit.

## 3. Fichiers modifiés

`design/tokens.json` :

- `color.black` est supprimé et `color.night` est ajouté ; `color.dark` a de nouvelles valeurs ;
- `typography.darkThemeAdjust` a une nouvelle forme : graisse et espacement par police, `lineHeight` à 1,7, `appliesTo` = `dark` et `night`.

`design/screens/` et `design/html/` :

- **Nouvel écran** : `1.10-lecture-nuit`.
- **Renommé** : `2.04-theme-noir` devient `2.04-theme-nuit`.
- **Modifiés pour le logo** : 1.01, 1.02, 1.02b, 1.03, 1.04, 1.05, 1.06, 1.09, 2.07, 2.07b, 3.01, 4.01.
- **Modifiés pour les thèmes foncés** : `1.02-bibliotheque-sombre`, `1.10-lecture-sombre`, `1.11-barre-affichee-sombre`, `1.12-sommaire-sombre`, `1.13-journal-de-lecture-sombre`, 2.02 et 2.02 sombre (pastilles « Sombre » et « Nuit »), 2.04.

`design/logo/` :

- les couleurs du logotype sombre sont mises à jour (`verso-logotype-sombre.svg` : V en `#CEB28A`, « erso » en `#CBC5BC`) ;
- les icônes Android ne changent pas.

`SPEC.md` :

- Décisions prises, ligne « Thème » ;
- Lecture, avec le paragraphe sur les thèmes foncés ;
- Paramètres › Affichage ;
- un critère d'acceptation sur le thème sombre ;
- V2 : réglages de lecture et liste des thèmes ;
- Interface : tableau des couleurs et notes, logo et icône ;
- Confort de lecture : ligne « Astigmatisme » et deux sources ;
- Démarrer le développement, étapes 2 et 4 (« en clair, en sombre et en nuit ») ;
- Historique.

## 4. À faire si le code existe déjà

- [x] Régénérer les couleurs Compose depuis `design/tokens.json` : remplacer `dark`, ajouter `night`, supprimer toute trace de `black` ou de `Noir`.
- [x] Ajouter la préférence `darkThemeVariant` et la résolution du thème ; brancher le bouton segmenté de 1.09.
- [x] Passer à Readium, en thème foncé, les couleurs du thème résolu, l'interligne 1,7 et l'espacement des lettres.
- [x] Remplacer le titre texte de la bibliothèque par `ic_logotype`, avec l'intitulé TalkBack « Verso ».
- [x] 1.01 : icône de l'app à 64 dp à la place du pictogramme.
- [x] 1.09 : ligne « Verso » / « Version x.y.z » avec l'icône à 40 dp.
- [x] Ajouter à `strings.xml` : « Thème sombre », « Sombre », « Nuit », « Verso », « Version %1$s ».
- [ ] Comparer à leur PNG, en clair, en sombre et en nuit : 1.01, 1.02, 1.09, 1.10. (captures Roborazzi comparées ; téléphone à faire)
- [x] Vérifier le contraste de tout texte dans les deux thèmes foncés (au moins 7:1), par exemple avec un test unitaire sur les jetons.

Fait le 2026-09-29 (voir l'Historique de `SPEC.md`). Écarts : sépia garde ses jetons ajustés à 7:1 à l'étape 10 ; les jetons propres au code (`onDanger`, `onCover`, `onInverseAccent`, `progressInk`, `scrim`, `coverPalette`) sont gardés dans `tokens.json` ; les Paramètres gardent la liste des thèmes (Automatique, Clair, Sépia, Sombre, Nuit) au-dessus de « Thème sombre », qui ne sert qu'à l'automatique.
