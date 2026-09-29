# Verso · maquettes

Export du canvas de maquettes (2026-09-25) : les écrans de V1 à V4 et le logo retenu. La spec (`../SPEC.md`) décrit le comportement ; ces fichiers montrent l'apparence. **En cas d'écart, la spec prime.**

## Contenu

| Élément | Rôle |
| --- | --- |
| `index.html` | Tous les écrans par version, à ouvrir dans un navigateur |
| `apercu-v1.png` … `apercu-v4.png` | Planches d'aperçu, une par version |
| `screens/` | Chaque écran en PNG, à 2× (780 × 1688 px pour un écran de 390 × 844 dp) |
| `html/` | Chaque écran en HTML statique, pour mesurer dans les outils de développement du navigateur |
| `screens.json` | Liste des écrans : identifiant, titre, version, thème, fichiers |
| `tokens.json` | Couleurs des quatre thèmes (clair, sombre, nuit, sépia), typographie, arrondis et tailles, repris de la spec |
| `fonts/` | Atkinson Hyperlegible Next et Literata en woff2 (licence OFL), pour que les HTML s'affichent hors ligne |
| `logo/` | Icône Android, icône à thème, PNG Play Store, SVG et logotype. Voir `logo/README.md` |

## Lire les maquettes

- 1 px CSS = 1 dp, et les tailles de texte en px sont des sp. Le cadre fait 390 × 844 dp.
- Les livres, les dates et les durées sont des exemples. Les **libellés d'interface** sont définitifs : même texte, même ponctuation (espaces fines insécables comprises).
- Les HTML servent à inspecter les mesures, pas à porter du code. L'app est en Jetpack Compose.
- Le texte de lecture est rendu ici par le navigateur. Dans l'app, il est rendu par Readium avec les réglages de la spec.
- Un seul état est montré par écran. Par exemple, 1.06 montre le cas « pas un EPUB », et le cas DRM est décrit dans la spec.

## Écrans

| Id | Écran | Version | Fichiers |
| --- | --- | --- | --- |
| 1.01 | Premier lancement | V1 | [PNG](screens/1.01-premier-lancement.png) · [HTML](html/1.01-premier-lancement.html) |
| 1.02 | Bibliothèque — liste | V1 | [PNG](screens/1.02-bibliotheque-liste.png) · [HTML](html/1.02-bibliotheque-liste.html) |
| 1.03 | Bibliothèque — grille | V1 | [PNG](screens/1.03-bibliotheque-grille.png) · [HTML](html/1.03-bibliotheque-grille.html) |
| 1.04 | Import réussi | V1 | [PNG](screens/1.04-import-reussi.png) · [HTML](html/1.04-import-reussi.html) |
| 1.05 | Livre déjà importé | V1 | [PNG](screens/1.05-livre-deja-importe.png) · [HTML](html/1.05-livre-deja-importe.html) |
| 1.06 | Fichier refusé | V1 | [PNG](screens/1.06-fichier-refuse.png) · [HTML](html/1.06-fichier-refuse.html) |
| 1.02b | Menu d’un livre | V1 | [PNG](screens/1.02b-menu-dun-livre.png) · [HTML](html/1.02b-menu-dun-livre.html) |
| 1.07 | Détails du livre | V1 | [PNG](screens/1.07-details-du-livre.png) · [HTML](html/1.07-details-du-livre.html) |
| 1.08 | Supprimer un livre | V1 | [PNG](screens/1.08-supprimer-un-livre.png) · [HTML](html/1.08-supprimer-un-livre.html) |
| 1.09 | Paramètres | V1 | [PNG](screens/1.09-parametres.png) · [HTML](html/1.09-parametres.html) |
| 1.10 | Lecture | V1 | [PNG](screens/1.10-lecture.png) · [HTML](html/1.10-lecture.html) |
| 1.11 | Lecture, barre affichée | V1 | [PNG](screens/1.11-lecture-barre-affichee.png) · [HTML](html/1.11-lecture-barre-affichee.html) |
| 1.12 | Sommaire | V1 | [PNG](screens/1.12-sommaire.png) · [HTML](html/1.12-sommaire.html) |
| 1.13 | Journal de lecture | V1 | [PNG](screens/1.13-journal-de-lecture.png) · [HTML](html/1.13-journal-de-lecture.html) |
| 1.14 | Retour à votre lecture | V1 | [PNG](screens/1.14-retour-a-votre-lecture.png) · [HTML](html/1.14-retour-a-votre-lecture.html) |
| 1.02 | Bibliothèque (sombre) | V1 | [PNG](screens/1.02-bibliotheque-sombre.png) · [HTML](html/1.02-bibliotheque-sombre.html) |
| 1.10 | Lecture (sombre) | V1 | [PNG](screens/1.10-lecture-sombre.png) · [HTML](html/1.10-lecture-sombre.html) |
| 1.10 | Lecture (nuit) | V1 | [PNG](screens/1.10-lecture-nuit.png) · [HTML](html/1.10-lecture-nuit.html) |
| 1.11 | Barre affichée (sombre) | V1 | [PNG](screens/1.11-barre-affichee-sombre.png) · [HTML](html/1.11-barre-affichee-sombre.html) |
| 1.12 | Sommaire (sombre) | V1 | [PNG](screens/1.12-sommaire-sombre.png) · [HTML](html/1.12-sommaire-sombre.html) |
| 1.13 | Journal de lecture (sombre) | V1 | [PNG](screens/1.13-journal-de-lecture-sombre.png) · [HTML](html/1.13-journal-de-lecture-sombre.html) |
| 2.01 | Barre de lecture | V2 | [PNG](screens/2.01-barre-de-lecture.png) · [HTML](html/2.01-barre-de-lecture.html) |
| 2.02 | Réglages de lecture | V2 | [PNG](screens/2.02-reglages-de-lecture.png) · [HTML](html/2.02-reglages-de-lecture.html) |
| 2.03 | Thème sépia | V2 | [PNG](screens/2.03-theme-sepia.png) · [HTML](html/2.03-theme-sepia.html) |
| 2.04 | Thème nuit | V2 | [PNG](screens/2.04-theme-nuit.png) · [HTML](html/2.04-theme-nuit.html) |
| 2.05 | Mode pages | V2 | [PNG](screens/2.05-mode-pages.png) · [HTML](html/2.05-mode-pages.html) |
| 2.06 | Recherche dans le livre | V2 | [PNG](screens/2.06-recherche-dans-le-livre.png) · [HTML](html/2.06-recherche-dans-le-livre.html) |
| 2.07 | Bibliothèque avec états | V2 | [PNG](screens/2.07-bibliotheque-avec-etats.png) · [HTML](html/2.07-bibliotheque-avec-etats.html) |
| 2.07b | Trier et afficher | V2 | [PNG](screens/2.07b-trier-et-afficher.png) · [HTML](html/2.07b-trier-et-afficher.html) |
| 2.08 | Détails du livre | V2 | [PNG](screens/2.08-details-du-livre.png) · [HTML](html/2.08-details-du-livre.html) |
| 2.09 | Paramètres | V2 | [PNG](screens/2.09-parametres.png) · [HTML](html/2.09-parametres.html) |
| 2.02 | Réglages de lecture (sombre) | V2 | [PNG](screens/2.02-reglages-de-lecture-sombre.png) · [HTML](html/2.02-reglages-de-lecture-sombre.html) |
| 3.01 | PDF importé | V3 | [PNG](screens/3.01-pdf-importe.png) · [HTML](html/3.01-pdf-importe.html) |
| 3.02 | Lecture d’un PDF converti | V3 | [PNG](screens/3.02-lecture-dun-pdf-converti.png) · [HTML](html/3.02-lecture-dun-pdf-converti.html) |
| 3.03 | Pages fidèles | V3 | [PNG](screens/3.03-pages-fideles.png) · [HTML](html/3.03-pages-fideles.html) |
| 3.04 | Texte sélectionné | V3 | [PNG](screens/3.04-texte-selectionne.png) · [HTML](html/3.04-texte-selectionne.html) |
| 3.05 | Ajouter une note | V3 | [PNG](screens/3.05-ajouter-une-note.png) · [HTML](html/3.05-ajouter-une-note.html) |
| 3.06 | Notes et surlignages | V3 | [PNG](screens/3.06-notes-et-surlignages.png) · [HTML](html/3.06-notes-et-surlignages.html) |
| 3.07 | Sauvegarde | V3 | [PNG](screens/3.07-sauvegarde.png) · [HTML](html/3.07-sauvegarde.html) |
| 4.01 | Collections | V4 | [PNG](screens/4.01-collections.png) · [HTML](html/4.01-collections.html) |
| 4.02 | Une collection | V4 | [PNG](screens/4.02-une-collection.png) · [HTML](html/4.02-une-collection.html) |
| 4.03 | Nouvelle collection | V4 | [PNG](screens/4.03-nouvelle-collection.png) · [HTML](html/4.03-nouvelle-collection.html) |
| 4.04 | Ajouter à une collection | V4 | [PNG](screens/4.04-ajouter-a-une-collection.png) · [HTML](html/4.04-ajouter-a-une-collection.html) |
| logo | Logo retenu | Logo | [PNG](screens/logo-retenu.png) · [HTML](html/logo-retenu.html) |
