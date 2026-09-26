# Spec — Verso, lecteur d'ebook Android

2026-09-25 · @Maxime BIER · remplace la version du 2026-09-24 · prête pour le développement

## Vision

Verso est un lecteur d'ebook Android minimaliste dont la promesse tient en une phrase : **j'ouvre l'app, je suis exactement où j'étais, et le texte est agréable à lire.**

Utilisateur cible : moi, lecteur de romans et de livres du domaine public sur mon téléphone. Pas de public externe en V1 ; le code est publié sur GitHub comme pièce de portfolio.

Ce que l'app n'est pas : ni un lecteur de PDF fidèle, ni un outil d'annotation pour articles universitaires, ni une librairie en ligne. Le PDF arrive plus tard, converti en texte, et reste secondaire.

Différenciation visée par rapport aux lecteurs existants (Apple Books, Google Play Livres, Moon+ Reader) :

- zéro friction entre « j'ai un fichier » et « je lis » ;
- une progression qui ne se perd jamais, même après un scroll accidentel ;
- un confort de lecture fondé sur la recherche (voir « Confort de lecture »), pas sur des effets.

## Décisions prises

| Sujet | Décision | Pourquoi |
| --- | --- | --- |
| Nom | **Verso** | Choisi le 2026-09-23. |
| Nature du produit | Lecteur d'ebook, pas lecteur de PDF | Romans + texte refait couler = exactement le cas d'usage de l'EPUB |
| Format V1 | EPUB uniquement | Format structuré (HTML), reflow natif, bibliothèques mûres. Le PDF n'a pas de notion de paragraphe : reconstruire un texte propre est un chantier à part |
| Affichage | Texte refait couler à la largeur de l'écran | Mise en page fixe illisible sur 6 pouces sans zoom |
| Navigation V1 | Scroll vertical continu | La pagination arrive en V2 ; aucune différence de compréhension mesurée sur téléphone entre les deux |
| Police V1 | **Atkinson Hyperlegible Next partout** (interface et lecture) | Une seule police dans toute l'app. Lettres très distinctes, conçue pour la basse vision. Préférence de l'utilisateur |
| Police V2 | **Literata par défaut partout**, sélecteur : Literata / Atkinson Hyperlegible Next / police du système | Aucune police n'est la meilleure pour tout le monde (voir « Confort de lecture »). La police choisie s'applique à toute l'app, pas seulement au texte |
| Choix de police au premier lancement | Non | Onboarding sans décision ; le sélecteur est dans les Paramètres et dans les réglages de lecture |
| Thème | Clair par défaut, sombre en suivant le système (V1) ; clair, sépia, sombre, noir et automatique au choix (V2) | Le texte foncé sur fond clair se lit mieux de jour ; le sombre réduit la lumière totale la nuit |
| Progression | **Position de lecture distincte de la position affichée** + carte « Revenir » + journal des sessions | Remplace « 5 dernières positions » et le bouton « Revenir » minuté. Voir « Marque-page » |
| Import | Sélecteur de fichiers Android (Storage Access Framework) | Couvre Fichiers, Drive et Nextcloud sans coder de synchro |
| Catalogue | Titre/auteur extraits de l'EPUB, corrigeables à la main, couverture, progression. Affichage liste ou grille | Les métadonnées EPUB sont fiables, contrairement au PDF |
| Accessibilité | Contraste ≥ 7:1 pour tout texte (WCAG AAA), cibles tactiles 48 dp, texte agrandissable à 200 % | Confort de lecture sur de longues durées, pour tous |
| Plateforme | Android seul | Pas d'iPhone pour tester, pas de compte App Store |
| Stack | Kotlin + Jetpack Compose + Readium + Room | Stack la mieux documentée d'Android, donc la plus fiable pour coder assisté par IA ; crédible en portfolio |
| Identifiant de l'app | `com.maximebier.verso` | Fixé avant le premier commit. Il identifie l'app sur le téléphone et ne change plus ensuite |
| Versions Android | minSdk 26, targetSdk 36, **compileSdk 37** | À partir de 26 : polices variables et icône adaptative sans code de compatibilité (Readium demande 24). Cible 36 : niveau exigé par Google Play depuis le 31 août 2026, si l'app y est publiée un jour. compileSdk 37 : exigé par Readium 3.4.0 et Compose 1.12 ; il ne change pas le comportement de l'app (décidé le 2026-09-25) |
| Licence du code | Apache 2.0 | Licence habituelle d'Android et de Kotlin, avec une clause sur les brevets. Compatible avec Readium (BSD-3) et les polices (OFL) |
| Ouverture au lancement | Rouvre le dernier livre s'il a été lu il y a moins de 24 h, **activé par défaut** | Confirmé le 2026-09-25 : on retombe dans sa lecture. Réglable dans les Paramètres |
| Logo | V formé de deux pages, variante Papier | Voir « Logo et icône » |
| Distribution | APK installé à la main, code sur GitHub | Usage perso, pas de contrainte Play Store |
| PDF | Repoussé en V3, converti en texte, marqué expérimental | Aucun PDF concret à lire aujourd'hui ; le nettoyage du texte coûterait autant que toute la V1 |
| Collections | Ajoutées en V4 | Regrouper une série (ex. Les Rougon-Macquart) avec une progression d'ensemble |

## V1 — Fonctionnalités

La V1 est finie quand je peux importer un EPUB, le lire d'un bout à l'autre en scroll, fermer l'app n'importe quand et la rouvrir au même endroit, sans qu'un scroll accidentel puisse faire perdre ma place.

### Premier lancement

- Bibliothèque vide : titre « Votre bibliothèque est vide », une phrase d'explication, un seul bouton principal « Importer un EPUB » (56 dp, seule exception à la hauteur de 48 dp), et une ligne sur « Ouvrir avec Verso ».
- Aucun choix à faire : pas de police, pas de thème, pas de compte.
- Le bouton « Importer » de l'en-tête est masqué tant que la bibliothèque est vide (le bouton principal le remplace).

### Import

- Bouton « Importer » dans l'en-tête de la bibliothèque, qui ouvre le sélecteur de fichiers Android (SAF). Tout fournisseur visible dans le sélecteur fonctionne : stockage local, Drive, Nextcloud, etc.
- Réception d'un EPUB via « Ouvrir avec » / « Partager vers » depuis une autre app (navigateur, mail, gestionnaire de fichiers).
- Le fichier est copié dans le stockage privé de l'app. L'original peut être déplacé ou supprimé sans casser la bibliothèque.
- Import réussi : snackbar « « Titre » a été ajouté. » avec l'action « Commencer ».
- Import du même fichier deux fois : détection par empreinte (SHA-256). Fenêtre « Ce livre est déjà dans votre bibliothèque », boutons « Remplacer » (texte) et « Ignorer » (principal). La position de lecture est conservée dans les deux cas.
- Fichier refusé : fenêtre « Impossible d'importer ce fichier », avec un message selon le cas (pas un EPUB, ou protégé par un DRM Adobe ou LCP), puis « Rien n'a été ajouté à votre bibliothèque. » et le bouton « Compris ». Rien n'est ajouté au catalogue.

### Catalogue

- En haut, une carte « Reprendre » sur le dernier livre ouvert : couverture, titre, auteur, chapitre, **extrait de 2 lignes du passage où l'on s'est arrêté**, progression (barre d'accent 6 dp), « 31 % lu », « Environ 5 h 30 restantes », bouton « Reprendre ». Toute la carte est un seul bouton.
- Liste des livres importés : couverture (image de l'EPUB, sinon vignette générée), titre, auteur, état de lecture (barre 4 dp + %, « Terminé » avec une coche, ou « Nouveau »).
- Affichage **liste ou grille** (bascule à deux icônes à droite du titre « Bibliothèque »). La grille est en 2 colonnes, pour garder des titres lisibles.
- Tri : bouton segmenté « Récents / Titre / Auteur » (choix unique), « Récents » par défaut (dernier ouvert en premier).
- Chaque livre a un bouton ⋮ (« Options pour « Titre » ») qui ouvre un **menu** : « Détails du livre », « Supprimer ». La V4 y ajoute « Ajouter à une collection ».
- Fiche livre (« Détails du livre ») :
  - couverture, progression, temps restant, bouton « Reprendre » (ou « Commencer » à 0 %) ;
  - titre et auteur modifiables, enregistrés automatiquement ;
  - date d'import, taille, format, nom du fichier d'origine ;
  - bouton « Supprimer le livre », avec confirmation : « Supprimer « Titre » ? Le livre et votre position de lecture seront effacés de Verso. Le fichier d'origine, sur votre téléphone ou votre Drive, n'est pas touché. » Boutons « Annuler » et « Supprimer ».
- Au lancement, si un livre a été lu il y a moins de 24 h, l'app s'ouvre directement dedans (réglable dans les Paramètres, activé par défaut).

### Lecture

- Texte refait couler en scroll vertical continu, chapitres enchaînés sans coupure.
- Réglages par défaut fixés en V1 (les réglages utilisateur arrivent en V2, mais le modèle de position doit déjà les supporter) :

| Réglage | Valeur V1 | Remarque |
| --- | --- | --- |
| Police | Atkinson Hyperlegible Next | Embarquée dans l'APK (licence OFL) |
| Taille | 19 sp | Atkinson est large ; 19 sp donne environ 35 caractères par ligne. Literata passera à 20 sp en V2 |
| Interligne | 1,6 | En sp, pour suivre l'agrandissement du texte |
| Espace entre paragraphes | 0,5 × interligne | Soit 1,5 × l'interligne d'un paragraphe à l'autre (WCAG 1.4.8) |
| Marges latérales | 24 dp | |
| Alignement | À gauche, **pas de justification, pas de césure** | Imposé par-dessus le CSS de l'éditeur (réglages utilisateur Readium) |
| Titres de chapitre | Centrés : partie en 14 sp, numéro en 28 sp | |

- Thème sombre : le texte est allégé (graisse 380 au lieu de 400, espacement des lettres +0,01 em), parce qu'un texte clair sur fond sombre paraît plus gras.
- Tap au centre : affiche ou masque la barre de lecture.
  - En haut : retour à la bibliothèque, titre du livre, chapitre courant.
  - En bas : « 31 % lu », temps restant estimé, barre de progression, boutons « Sommaire » et « Journal ».
- Sommaire : feuille qui s'ouvre sur le chapitre en cours (surligné, « En cours · 31 % »). Les chapitres déjà lus portent « Lu » avec une coche. Tap sur un chapitre = saut direct.
- Écran maintenu allumé pendant la lecture. L'app ne touche jamais à la luminosité.
- Respect du thème système clair/sombre en V1 (les thèmes au choix attendent la V2).
- Temps restant : estimé avec une vitesse par défaut de 250 mots/min en V1, puis avec la vitesse mesurée en V2.

### Marque-page et progression

L'app distingue deux positions :

- **La position affichée** : ce qui est à l'écran, qui suit chaque scroll.
- **La position de lecture** : celle qui compte pour la progression, la carte « Reprendre », les pourcentages et la réouverture de l'app.

Règles :

- La position est stockée sous forme de **locator Readium** (identifiant de chapitre + progression dans le chapitre + extrait de texte), jamais en pixels. La restauration reste exacte si la taille de police change.
- La position de lecture suit la position affichée **tant que le mouvement ressemble à de la lecture** : petits scrolls réguliers, dans les deux sens.
- Un mouvement est de la **navigation** si c'est un geste lancé (fling), si plus de 3 écrans défilent en moins de 5 secondes, ou si c'est un saut explicite (sommaire, recherche, journal). La navigation ne touche pas la position de lecture.
- Après une navigation, si la position affichée est à plus d'un écran de la position de lecture, une **carte de retour** apparaît en bas :
  - texte : « Vous avez quitté votre lecture » et « Votre lecture : Partie II, chap. I · 31 % » ;
  - boutons : « Rester ici » (la position affichée devient la position de lecture) et « Revenir » (retour à la position de lecture, bouton principal) ;
  - **pas de minuterie** : la carte reste tant que l'utilisateur n'a pas choisi.
- La carte disparaît aussi si l'on se met à lire à la nouvelle place : après environ 25 secondes de mouvement de lecture à moins d'un écran de ce point, la position de lecture y passe automatiquement. Les 25 secondes courent depuis le premier glissé de lecture au nouvel endroit, pas depuis l'arrivée : la carte laissée affichée, un tap ou le début d'un fling ne comptent pas, et une pause de plus de 15 secondes entre deux glissés les remet à zéro.
- La position de lecture est sauvegardée automatiquement à chaque arrêt de scroll (debounce d'environ 500 ms) et à chaque mise en arrière-plan (`onStop`).
- À la réouverture, l'app revient à la position de lecture, jamais à l'endroit d'un scroll accidentel.
- Tous les seuils (vitesse de fling, nombre d'écrans, délai de confirmation) sont des constantes nommées, faciles à ajuster à l'usage.

### Journal de lecture

- Chaque session de lecture est enregistrée :
  - elle commence à l'ouverture d'un livre, ou à la reprise après plus de 5 minutes d'inactivité ;
  - elle se termine à la mise en arrière-plan, à la fermeture du livre ou après 5 minutes sans interaction ;
  - le temps actif exclut les intervalles de plus de 2 minutes sans scroll ni toucher ;
  - les mots lus ne comptent que les mouvements de lecture, pas les sauts.
- Accès : bouton « Journal » de la barre de lecture. Feuille « Journal de lecture », sous-titre « Touchez une session pour reprendre là où elle s'est arrêtée. »
- Les sessions sont groupées par jour (« Aujourd'hui », « Hier », « Lundi 21 septembre »…). Chaque session affiche :
  - l'heure de début et de fin, et la durée ;
  - le passage lu (« Partie I, chap. VI → VIII ») ;
  - les pourcentages de début et de fin (« 18 → 25 % ») ;
  - « Reprendre ici », qui mène à la fin de la session.
- La session en cours est marquée « En cours », sans action.
- Le journal est la source des statistiques de la V2.

### Paramètres

On y accède par la roue dentée dans l'en-tête de la bibliothèque.

- **Au démarrage** : « Rouvrir le dernier livre », « Si vous l'avez lu il y a moins de 24 heures » (interrupteur, activé par défaut).
- **Affichage** : phrase d'explication, sans ligne cliquable. « Verso suit le thème clair ou sombre de votre téléphone et garde l'écran allumé pendant la lecture. »
- **Confidentialité** :
  - phrase d'explication : « Verso n'utilise pas Internet. Vos livres, vos positions, votre journal et vos réglages restent sur ce téléphone. » ;
  - action « Effacer le journal de lecture », « Vos positions de lecture sont conservées. », avec confirmation.
- **À propos** : version, lien vers le code source sur GitHub, licences open source.

## Critères d'acceptation V1

Chaque critère se vérifie à la main sur le téléphone, avec un EPUB du domaine public (par exemple un roman de Gutenberg ou Wikisource).

- [ ] Au premier lancement, la bibliothèque vide propose un seul bouton « Importer un EPUB » et aucun choix de réglage.
- [x] J'importe un EPUB depuis le sélecteur Android et il apparaît dans le catalogue avec titre, auteur et couverture en moins de 5 secondes.
- [ ] J'ouvre un EPUB depuis Drive via « Ouvrir avec » et il est importé sans étape supplémentaire.
- [x] Je lis un chapitre entier en scroll continu sans saccade visible ni saut de mise en page.
- [x] Je ferme l'app brutalement (swipe depuis les récents) au milieu d'un paragraphe ; à la réouverture, le même paragraphe est à l'écran.
- [ ] Je redémarre le téléphone ; la position est intacte.
- [ ] Je scrolle violemment par erreur de 30 pages : la progression ne bouge pas, et la carte « Revenir » me ramène à ma lecture en un tap.
- [x] Je fais le même scroll accidentel, puis je ferme l'app sans toucher la carte ; à la réouverture, je suis à ma position de lecture.
- [ ] Je saute au début du livre par le sommaire ; la carte « Revenir » apparaît et me ramène au chapitre d'où je venais.
- [x] Après 25 secondes de lecture réelle à un nouvel endroit, la carte disparaît et la progression suit.
- [x] Après deux sessions de lecture, le journal les montre avec leur heure, leur durée et leur passage, et « Reprendre ici » mène à la fin de chacune.
- [x] Je change la taille de police via la valeur par défaut du code (simulation V2) ; la position restaurée reste le même paragraphe.
- [ ] Je corrige le titre d'un livre ; la correction survit à un redémarrage.
- [x] Un fichier non-EPUB refusé affiche un message et ne laisse aucune trace dans le catalogue.
- [x] Le thème sombre du système est respecté sans zone blanche éblouissante.
- [ ] Avec la taille de texte Android à 200 %, aucun texte n'est coupé et aucune ligne ne demande de scroll horizontal.
- [ ] Toutes les commandes font au moins 48 dp et tous les boutons à icône seule ont un intitulé lu par TalkBack.
- [x] Aucune permission demandée hormis l'accès aux fichiers via le sélecteur (pas de réseau, pas de contacts, rien).

## V2, V3 et V4

La V2 rend la lecture confortable pour moi ; la V3 ouvre aux formats et aux usages secondaires ; la V4 organise la bibliothèque. Rien ici ne doit être codé avant que la V1 passe tous ses critères.

### V2 — Confort de lecture

- **Police sélectionnée partout** : Literata par défaut ; Atkinson Hyperlegible Next et police du système au choix. La police choisie s'applique à toute l'app (interface et lecture).
- **Réglages de lecture** (bouton « Aa » de la barre) : feuille ouverte à mi-hauteur, sans voile sur le texte, pour voir l'effet en direct.
  - En haut : Police (3 cartes « Aa »), Taille du texte (boutons − et +), Thème (Auto, Clair, Sépia, Sombre, Noir).
  - En dessous, en faisant glisser : Interligne (Serré 1,4 / Normal 1,6 / Aéré 1,8), Marges (Étroites / Normales / Larges), Défilement (Continu / Pages, mémorisé par livre).
- **Thèmes** : clair, sépia, sombre, noir, et automatique (suit le système). Réglable indépendamment du système. Valeurs dans « Interface ».
- **Mode pages** : pages tournées par swipe ou tap latéral, en alternative au scroll, choix mémorisé par livre. Le texte s'aligne sur une grille de lignes (jamais de ligne coupée en bas de page). Pied de page discret : chapitre à gauche, « Page 2 sur 9 » (dans le chapitre) à droite.
- **Barre de lecture V2** : en bas, quatre outils avec texte sous l'icône : Sommaire, Journal, Rechercher, Réglages (« Aa »).
- **États des livres** : à lire, en cours, terminé. L'état est calculé automatiquement à partir de la progression et modifiable dans la fiche (bouton segmenté). Dans la bibliothèque :
  - filtres « Tous / En cours / À lire / Terminés » en pastilles ;
  - un seul bouton « Récents ▾ » ouvre la feuille « Trier et afficher » (tri Récents / Titre / Auteur, affichage Liste / Grille) ;
  - « Nouveau » devient « À lire ».
- **Statistiques** calculées à partir du journal, affichées dans la fiche : temps de lecture (« 2 h 28 en 5 sessions »), vitesse moyenne (mots par minute), « Temps restant estimé : environ 5 h 30 à votre rythme », et un bouton « Voir le journal de lecture ». Interrupteur « Afficher les statistiques » dans les Paramètres (le journal est toujours tenu ; seul l'affichage se désactive).
- **Recherche plein texte** dans le livre ouvert : résultats affichés au fur et à mesure (« 3 résultats pour l'instant »), groupés par chapitre, le mot trouvé mis en évidence par un fond, du gras et un soulignement (jamais la couleur seule).
- **Paramètres V2** : section Lecture avec le choix de police (boutons radio, chaque police montrée sur une phrase du livre), thème, taille et défilement par défaut. Phrase d'aide : « Pour tous les livres et toute l'application. Pendant la lecture, touchez « Aa » pour changer. »

### V3 — Formats et extras

- **Import PDF converti en texte** : extraction du texte, reconstitution des paragraphes, suppression des numéros de page et en-têtes courants, recollage des césures.
  - Après l'import : fenêtre « PDF converti en texte », badge « Expérimental », boutons « Fermer » et « Commencer ».
  - En lecture : bandeau « Texte extrait d'un PDF · expérimental » avec le bouton « Pages fidèles ».
  - Mode « pages fidèles » de repli : page rendue telle quelle, boutons « Précédente » / « Suivante », « 3 / 64 », bouton « Texte » pour revenir au texte extrait.
- **TXT, Markdown et HTML** : conversion triviale en EPUB à l'import.
- **Surlignages et notes** :
  - la sélection de texte ouvre une barre en bas : « Surligner », « Note », « Copier » ;
  - « Note » ouvre une feuille avec le passage et un champ « Votre note » ;
  - le surlignage est un fond et un soulignement (jamais la couleur seule) ;
  - l'écran « Notes et surlignages » d'un livre liste les éléments dans l'ordre du livre, avec « Exporter » en Markdown.
- **Sauvegarde et restauration** : un fichier unique (livres, positions, notes, journal, réglages), premier pas vers une synchro entre appareils.
  - L'écran affiche la dernière sauvegarde (date, taille, nom du fichier) et deux boutons : « Créer une sauvegarde » et « Restaurer une sauvegarde ».
  - Avertissement : la restauration remplace la bibliothèque actuelle, avec confirmation.

### V4 — Collections

- Les collections sont créées par l'utilisateur pour regrouper des livres, par exemple une série. Un livre peut appartenir à plusieurs collections.
- **Bibliothèque** : onglets « Livres / Collections ». Chaque collection affiche :
  - une pile de 3 couvertures, le nom, l'auteur et le nombre de livres ;
  - une barre de progression et « 44 % · 2 terminés sur 5 ».
  - Bouton « Nouvelle collection ».
- **Progression d'une collection** : pondérée par la longueur des livres (en mots), pour qu'un roman court ne compte pas autant qu'un long. Ce calcul n'est **pas expliqué dans l'interface**, ni en texte ni derrière une icône.
- **Écran d'une collection** :
  - pourcentage de la collection lue, décompte « 2 terminés · 2 en cours · 1 à lire », « Environ 26 h de lecture restantes » ;
  - carte « Reprendre » sur le premier livre non terminé, dans l'ordre de la collection ;
  - liste numérotée dans l'ordre de lecture, que l'utilisateur change avec « Réordonner ».
  - Menu ⋮ de la collection : renommer, réordonner, supprimer.
- **Création** : écran « Nouvelle collection », avec le champ « Nom de la collection », un filtre des livres (titre ou auteur), des cases à cocher et le bouton « Créer ».
- **Ajout** : depuis le menu ⋮ d'un livre ou depuis sa fiche (ligne « Collections » + « Modifier »). Feuille « Ajouter à une collection » avec des cases à cocher, « Nouvelle collection » et « Terminé ».

## Interface

Les maquettes de tous les écrans sont exportées dans `design/` : un PNG et un HTML statique par écran, une planche d'aperçu par version, et `design/index.html` pour tout parcourir. Chaque écran est dans `design/screens/<id>-<nom>.png` (par exemple `1.14-retour-a-votre-lecture.png`) ; la liste complète est dans `design/README.md` et `design/screens.json`. La source des maquettes reste le canvas « Verso » (claude.ai, privé).

Pour lire les maquettes : 1 px = 1 dp, les tailles de texte en px sont des sp, le cadre fait 390 × 844 dp. Les livres et les dates sont des exemples, mais les libellés d'interface sont définitifs. Si une maquette et cette spec divergent, la spec l'emporte.

### Écrans

| Id | Écran | Version |
| --- | --- | --- |
| 1.01 | Premier lancement (bibliothèque vide) | V1 |
| 1.02 / 1.03 | Bibliothèque en liste / en grille (+ 1.02 sombre) | V1 |
| 1.02b | Menu d'un livre (⋮) | V1 |
| 1.04 | Import réussi (snackbar) | V1 |
| 1.05 | Livre déjà importé | V1 |
| 1.06 | Fichier refusé (pas un EPUB / DRM) | V1 |
| 1.07 / 1.08 | Détails du livre / Supprimer un livre | V1 |
| 1.09 | Paramètres | V1 |
| 1.10 / 1.11 | Lecture / Lecture avec la barre affichée (+ sombre) | V1 |
| 1.12 | Sommaire (+ sombre) | V1 |
| 1.13 | Journal de lecture (+ sombre) | V1 |
| 1.14 | Retour à votre lecture (carte « Revenir ») | V1 |
| 2.01 | Barre de lecture V2 | V2 |
| 2.02 | Réglages de lecture (+ sombre) | V2 |
| 2.03 / 2.04 | Thème sépia / Thème noir | V2 |
| 2.05 | Mode pages | V2 |
| 2.06 | Recherche dans le livre | V2 |
| 2.07 / 2.07b | Bibliothèque avec états / Trier et afficher | V2 |
| 2.08 / 2.09 | Détails du livre (états, statistiques) / Paramètres | V2 |
| 3.01 – 3.03 | PDF importé / Lecture d'un PDF converti / Pages fidèles | V3 |
| 3.04 – 3.06 | Texte sélectionné / Ajouter une note / Notes et surlignages | V3 |
| 3.07 | Sauvegarde | V3 |
| 4.01 – 4.04 | Collections / Une collection / Nouvelle collection / Ajouter à une collection | V4 |

### Couleurs

Tous les couples texte/fond ont été vérifiés par calcul : chaque texte atteint au moins 7:1 (WCAG AAA), et les éléments non textuels au moins 3:1. Les mêmes valeurs, avec la typographie, les arrondis et les tailles, sont dans `design/tokens.json`. Le thème Compose se génère à partir de ce fichier.

| Rôle | Clair | Sombre | Sépia (V2) | Noir (V2) |
| --- | --- | --- | --- | --- |
| Fond | `#F5F1E8` | `#171513` | `#EFE3CC` | `#000000` |
| Surface (cartes, barres, feuilles) | `#ECE5D9` | `#22201D` | `#E6D8BD` | `#141312` |
| Surface haute | `#E4DCCE` | `#2B2825` | `#DDCDAF` | `#1E1C1A` |
| Texte | `#1F1B16` (15,2:1) | `#E8E2D8` (14,1:1) | `#2E2419` (12:1) | `#D9D3C9` (14,1:1) |
| Texte secondaire | `#4A433B` (8,6:1) | `#CFC7BB` (10,9:1) | `#4C3E2A` (8,1:1) | `#B8B0A4` (9,8:1) |
| Accent | `#7A3021` (8,2:1) | `#E8C48E` (11:1) | `#7A3021` | `#E8C48E` |
| Texte sur accent | `#FFFFFF` (9,2:1) | `#1B1510` (11:1) | `#FFFFFF` | `#1B1510` |
| Piste des barres | `#DDD4C5` | `#3A3530` | `#D8C7A8` | `#2E2B28` |
| Contour | `#7D7366` | `#8E857A` | `#7A6A52` | `#857D72` |
| Séparateur | `#D3CABB` | `#3A3530` | `#D3C2A2` | `#2E2B28` |
| Sélection (fond / texte) | `#EBD7C6` / `#2B170C` | `#463727` / `#F2E3D0` | `#E2C9AE` / `#2B170C` | `#3E3122` / `#F2E3D0` |
| Danger | `#8A2318` | `#F2A99E` | `#8A2318` | `#F2A99E` |
| Inverse (snackbar, carte de retour) | `#2E2A25` / `#F5F1E8` | `#E8E2D8` / `#1F1B16` | `#3A2E22` / `#EFE3CC` | `#D9D3C9` / `#141312` |
| Accent sur inverse | `#F0C9A0` | `#7A3021` | `#F0C9A0` | `#7A3021` |
| Surlignage (V2/V3) | `#F3D9A4` | `#5A4520` | `#E9C98C` | `#4A3A1C` |

- Le fond clair n'est pas du blanc pur (environ 12 % de lumière en moins), et le fond sombre n'est pas du noir pur.
- Le thème noir atténue le texte pour limiter l'éblouissement sur fond noir pur.
- **Règle de l'accent** : l'accent sert uniquement :
  - au bouton principal de l'écran ;
  - à la progression du livre en cours (barre de 6 dp) ;
  - aux commandes de sélection (interrupteurs, cases, boutons radio, carte de police choisie) et au champ actif.
  - Tout le reste est à l'encre : boutons texte, titres de section, liens d'action.
- **Barres de progression** : 6 dp en accent pour le livre en cours (carte Reprendre, barre de lecture, résumé d'une collection) ; 4 dp à l'encre pour tous les autres.
- **Vignettes générées** : couleur sourde choisie à partir du titre, monogramme ou titre en texte clair, contraste d'au moins 4,5:1.

### Typographie et dimensions

| Élément | Valeurs |
| --- | --- |
| Échelle de texte (sp) | 14 légendes et méta · 16 texte d'interface, boutons · 18 titres de livres · 22 titres d'écran, de feuille et de fenêtre · 28 grands titres (logo, écran vide, numéro de chapitre) |
| Texte de lecture | 19 sp en Atkinson, 20 sp en Literata, interligne 1,6 |
| Arrondis | 4 couvertures · 12 champs, pastilles, boutons segmentés, menus, petites cartes · 20 cartes · 28 feuilles et fenêtres · boutons en pilule |
| Hauteurs | 48 dp pour toutes les commandes ; 56 dp seulement pour le bouton principal d'un écran vide |
| Icônes | Trait de 2, extrémités arrondies, 20 à 24 dp |
| Sélection | Fond + gras + **coche**, jamais la couleur seule. Tri et états : boutons segmentés (choix unique). Filtres par état : pastilles défilantes |

### Vocabulaire et typographie française

| Action | Mot | Où |
| --- | --- | --- |
| Ouvrir un livre jamais lu | **Commencer** | Snackbar d'import, fiche d'un livre à 0 %, PDF converti |
| Continuer un livre entamé | **Reprendre** | Carte Reprendre, fiche, collection |
| Revenir à la position de lecture après un saut | **Revenir** | Carte de retour |
| Faire de l'endroit affiché la position de lecture | **Rester ici** | Carte de retour |
| Aller à la fin d'une session | **Reprendre ici** | Journal |

- Espace fine insécable avant `; : ! ?` et `%` (« 31 % »).
- Espace insécable à l'intérieur des guillemets (« … »), dans les durées (« 5 h 30 ») et après le tiret de dialogue.
- Apostrophe typographique (’).
- Toujours « Environ » plutôt que « ≈ », que les lecteurs d'écran lisent mal.

### Accessibilité

- Contraste d'au moins 7:1 pour tout texte, dans tous les thèmes. Au moins 3:1 pour les barres, contours et icônes.
- Cibles tactiles de 48 dp minimum.
- Tailles de texte **et interlignes** en sp. L'app reste utilisable avec le texte Android à 200 % (mise à l'échelle non linéaire d'Android 14).
- Jamais la couleur seule pour porter une information : coches, texte (« Terminé », « À lire »), gras.
- Tous les boutons à icône seule ont un intitulé (« Paramètres », « Options pour « Titre » », « Retour à la bibliothèque », « Fermer », etc.). Les interrupteurs, onglets et groupes de choix exposent leur rôle et leur état.
- Le réglage Android « Supprimer les animations » est respecté.
- La barre du haut, les feuilles et la carte de retour tiennent compte de la barre d'état et de la barre de gestes (insets).

### Logo et icône

Le logo est un V formé par deux pages qui se croisent à la reliure : le livre ouvert, vu par la tranche. La page de devant (à gauche) passe sur celle de derrière. Chaque page porte deux traits, comme la tranche d'un paquet de feuilles. Maquette : `design/screens/logo-retenu.png`. Les fichiers sont dans `design/logo/` (voir son README).

| Élément | Couleur |
| --- | --- |
| Fond de l'icône | Accent clair `#7A3021` |
| Pages | Fond clair `#F5F1E8` |
| Traits de pages | Séparateur clair `#D3CABB` |
| V du logotype | Accent du thème affiché (`#7A3021` en clair, `#E8C48E` en sombre) |
| « erso » du logotype | Texte du thème affiché, en Atkinson Hyperlegible Next Bold |

- **Géométrie**, sur le canevas de 108 unités de l'icône adaptative :
  - page de gauche de (30, 32)–(41, 32) en haut à (48, 78)–(60, 78) en bas ;
  - page de droite de (67, 32)–(78, 32) en haut à (48, 78)–(60, 78) en bas ;
  - hauts de page légèrement bombés (point de contrôle 2,4 unités plus haut) ;
  - espace de 1,2 entre les pages là où elles se croisent ;
  - traits de 1,3 (0,9 découpés dans la version une couleur).
  - Tout tient dans la zone sûre de 66 dp (rayon maximal 32,6).
- **Icône adaptative** : fond `@color/ic_launcher_background`, premier plan `ic_launcher_foreground`, calque `monochrome` pour les icônes à thème d'Android 13+.
- **Version une couleur** (icône à thème, notifications, V du logotype) : les traits sont découpés dans la forme, les deux pages restent séparées par l'espace.
- **Petites tailles** : l'icône reste lisible jusqu'à 24 dp. La barre d'état et les notifications utilisent la version une couleur (`verso-v.svg`).
- **Dans l'app**, le nom reste du texte (Atkinson, couleur Texte), comme dans les maquettes. Le logotype vectorisé sert hors de l'app : README, fiche Play Store.
- Ne pas redessiner le logo : les XML Android et les SVG sont générés à partir de la même géométrie.

## Confort de lecture

Les réglages par défaut de Verso reposent sur ce consensus :

| Sujet | Ce que dit la recherche | Choix de Verso |
| --- | --- | --- |
| Texte foncé sur clair ou clair sur foncé | Le texte foncé sur fond clair se lit mieux, surtout en petite taille ; en lumière faible, pas de différence nette de fatigue | Clair par défaut, sombre la nuit |
| Luminosité | Un écran très lumineux fait moins cligner des yeux, ce qui fatigue | Fond crème, pas de blanc pur ; l'app ne force jamais la luminosité |
| Lumière bleue | Les filtres n'ont pas d'effet démontré sur la fatigue ; Night Shift n'améliore pas le sommeil | Pas de filtre ; le soir, c'est la quantité totale de lumière qui compte (thème sombre) |
| Taille | Au-delà d'une taille minimale (0,2° d'angle visuel), la vitesse plafonne ; téléphone tenu à 32–36 cm | 19–20 sp, environ 0,27°, au-dessus de la taille d'un journal |
| Police | Pas de différence prouvée entre polices avec et sans empattements ; la hauteur des minuscules et l'habitude comptent ; chacun a « sa » police | Une police claire par défaut et un sélecteur en V2 |
| Mise en page | Pas de justification, interligne ≥ 1,5, lignes courtes, texte agrandissable à 200 % (WCAG 1.4.8) | Aligné à gauche sans césure, interligne 1,6, environ 35 caractères par ligne |
| Italique, majuscules | Ils ralentissent la lecture de passages longs | Extraits en romain, pas de libellés en capitales |
| Pauses | La règle 20-20-20 réduit les symptômes tant qu'on l'applique | Hors V1 ; rappel optionnel possible plus tard |

Sources principales :

- [Piepenbrock et al. 2014](https://journals.sagepub.com/doi/abs/10.1177/0018720813515509)
- [NN/g — Dark Mode](https://www.nngroup.com/articles/dark-mode/)
- [Benedetto et al. 2014](https://lead.ube.fr/wp-content/uploads/2023/09/Benedetto_et_al._2014.pdf)
- [Cochrane 2023](https://www.cochrane.org/about-us/news/blue-light-filtering-spectacles-probably-make-no-difference-eye-strain-eye-health-or-sleep)
- [Chang et al. 2015](https://www.pnas.org/doi/10.1073/pnas.1418490112)
- [Duraccio et al. 2021](https://gwern.net/doc/zeo/2021-duraccio.pdf)
- [Legge & Bigelow](https://jov.arvojournals.org/article.aspx?articleid=2191906)
- [Bababekova 2011](https://journals.lww.com/optvissci/Fulltext/2011/07000/Font_Size_and_Viewing_Distance_of_Handheld_Smart.5.aspx)
- [Wallace et al. 2022](https://dl.acm.org/doi/10.1145/3502222)
- [WCAG 1.4.8](https://www.w3.org/WAI/WCAG21/Understanding/visual-presentation.html)
- [Joshi — scroll ou pagination](https://nikhitajoshi.ca/papers/scroll-vs-page.pdf)
- [Talens-Estarelles 2023](https://www.sciencedirect.com/science/article/pii/S1367048422001990)

## Hors périmètre

Ces points sont exclus volontairement, pas oubliés. Les rouvrir demande une décision explicite.

- Lecteur de PDF fidèle avec zoom et pan : les apps existantes le font déjà bien.
- Boutique, catalogue en ligne ou téléchargement direct depuis Gutenberg : l'import de fichiers suffit, et cela ajouterait du réseau et des questions légales.
- Synchronisation cloud de la position entre appareils : un seul téléphone pour l'instant.
- Compte utilisateur, connexion, analytics, publicité : rien ne quitte le téléphone.
- Gestion des DRM (Adobe, LCP) : les livres visés sont libres de droits ou acquis sans DRM.
- Lecture audio, synthèse vocale.
- Choix de police ou de thème au premier lancement : l'onboarding ne demande rien.
- Filtre « lumière bleue » : aucun bénéfice démontré (voir « Confort de lecture »).
- Version iPhone : possible plus tard si la stack le permet, mais aucun compromis fait pour ça aujourd'hui.

## Orientations techniques

Ces orientations sont des propositions à valider par Claude Code lors du plan technique ; la spec produit ci-dessus prime si un choix technique entre en conflit.

| Brique | Choix proposé | Rôle |
| --- | --- | --- |
| Langage / UI | Kotlin, Jetpack Compose, Material 3 | Toute l'interface, avec un thème personnalisé généré depuis `design/tokens.json` |
| Moteur EPUB | [Readium Kotlin toolkit](https://github.com/readium/kotlin-toolkit) 3.4.0 (septembre 2026, minSdk 24) | Ouverture de l'EPUB, rendu du texte, table des matières, locators, réglages utilisateur (police, taille, interligne, marges, alignement, césure), recherche (V2). Le navigateur EPUB stable est un Fragment, à intégrer dans l'écran Compose ; les navigateurs Compose de Readium sont encore en alpha |
| Polices | Atkinson Hyperlegible Next (V1) et Literata (V2), en TTF variables dans `res/font` (licence OFL) | Même police dans l'interface Compose et dans le rendu Readium. Les woff2 de `design/fonts/` ne servent qu'aux maquettes HTML : Android a besoin des TTF, publiés par les dépôts Google Fonts de chaque police |
| Base locale | Room (SQLite) | Livres, positions de lecture, sessions, puis notes et collections |
| Préférences | DataStore | Rouvrir le dernier livre, puis réglages de lecture et statistiques (V2) |
| Import | Storage Access Framework + intent filters `application/epub+zip` | Sélecteur de fichiers et « Ouvrir avec » |
| Images | Coil | Couvertures |
| Architecture | MVVM, une activité, navigation Compose | Simple, standard, lisible pour un recruteur |
| SDK | minSdk 26, targetSdk 36, compileSdk 37 | Voir « Décisions prises » |
| Textes | `res/values/strings.xml` en français, jamais de texte en dur | Libellés repris tels quels des maquettes, ponctuation française comprise |
| Tests | JUnit pour la logique pure, tests Compose pour les écrans clés | La détection lecture/navigation et la carte de retour sont une machine à états sans dépendance Android, testée unitairement avec les seuils nommés |
| CI | GitHub Actions : compilation, tests, lint, APK debug en artefact | Chaque commit sur `master` produit un APK installable |

### Modèle de données (première ébauche)

| Table | Champs principaux |
| --- | --- |
| `books` | id, titre, auteur, chemin du fichier copié, empreinte SHA-256, chemin de la couverture, taille, nom du fichier d'origine, date d'import, date de dernière ouverture, **locator de lecture** (JSON Readium), progression (0–1), nombre de mots total ; V2 : état (à lire / en cours / terminé, avec indicateur « modifié à la main »), mode de défilement du livre |
| `sessions` | id, book_id, début, fin, temps actif (ms), locator de début, locator de fin, progression de début et de fin, mots lus |
| `highlights` (V3) | id, book_id, locator de la plage, texte, note (facultative), date de création |
| `collections` (V4) | id, nom, date de création |
| `collection_books` (V4) | collection_id, book_id, position dans la collection |

- La table `positions` et l'historique des 5 positions de la version précédente sont supprimés.
- La position affichée et l'état de la carte de retour ne sont pas persistés. À la réouverture, on revient au locator de lecture.
- Supprimer un livre supprime ses sessions (suppression en cascade sur `book_id`).

### Points de vigilance

- Scroll continu entre chapitres (risque n° 1, tranché par le prototype du 2026-09-26) : Verso utilise le navigateur Compose `readium-navigator-web-reflowable`. Sur les deux EPUB Gutenberg, l'enchaînement des chapitres est continu dans les deux sens (décalage au pixel près, aucun écran blanc), la réouverture après fermeture brutale revient au pixel près (10/10, par progression seule : ce navigateur ne fournit pas d'extrait de texte), le locator suit chaque frame sans recul ni valeur hors bornes, et le tap au centre bascule l'interface (29/30 : un tap perdu juste après une restauration) sans aucun faux tap pendant un scroll. Détails : `docs/superpowers/plans/spike-readium-conclusions.md`.
- Lecture ou navigation (risque n° 2) : la fin de geste et sa vitesse sont mesurées au relâchement du doigt, en écrans par seconde. Lecture : médiane 0,18, 90ᵉ centile 0,32 ; fling : minimum 0,96 (0 pour un scroll rapide tenu avant de lever le doigt), médiane 1,86 — gestes simulés par adb ; les flicks d'un vrai doigt vont de 1,0 à 7,4. Seuil de départ `flingScreensPerSecond = 1.0`, appliqué à la seule vitesse au relâchement (décision du fling) ; la vitesse entre deux positions affichées consécutives a son propre seuil, `displayedSpeedNavigationScreensPerSecond = 4.0` (en plein glissé de lecture, la vitesse instantanée dépasse la vitesse au relâchement) ; la fenêtre « 3 écrans en 5 s » est confirmée côté lecture (0,56 écran au plus), à revérifier avec une vraie séance de lecture avant l'étape 5.
- La sauvegarde de position doit passer par `onStop` en plus du debounce de scroll, sinon la fermeture brutale perd les dernières secondes.
- Les réglages utilisateur Readium doivent l'emporter sur le CSS de l'éditeur (alignement à gauche, pas de césure, police et interligne de Verso).
- La graisse allégée du thème sombre suppose des polices variables (axe `wght`) ou une graisse intermédiaire embarquée.
- Tailles et interlignes en sp, tester avec le texte Android à 200 %. Gérer les insets de la barre d'état et de la barre de gestes, en particulier quand la barre de lecture s'affiche en mode immersif. Les barres (lecture et système) sont une surcouche : le texte reçoit des insets constants (barres système même masquées, découpe de l'écran), si bien que les afficher ou les masquer ne change jamais sa mise en page ni la position de lecture.
- Tester avec des EPUB réels et imparfaits (Gutenberg en génère de très variés), pas seulement un fichier propre.
- Le manifeste ne déclare pas `INTERNET`. Vérifier dans le manifeste fusionné qu'aucune dépendance ne l'ajoute, et la retirer avec `tools:node="remove"` si besoin.

## Questions ouvertes

### À trancher

Aucun de ces points ne bloque le démarrage.

- ~~Adresse du dépôt GitHub~~ : `https://github.com/MaximeBier/Verso` (privé pour l'instant ; le lien des Paramètres fonctionnera quand il sera public).
- Seuils de la détection lecture/navigation (vitesse de fling, 3 écrans en 5 secondes, confirmation après 25 secondes) : valeurs de départ à ajuster à l'usage.
- Supprimer un livre supprime-t-il aussi ses sessions du journal ? Par défaut : **oui**. C'est le plus simple, et la V1 n'a pas de statistiques globales. À revoir avec les statistiques de la V2.

L'écran « Licences open source » liste Readium (BSD-3) et les deux polices (OFL), en plus des bibliothèques AndroidX.

## Démarrer le développement

### Dépôt de départ

Le kit livré avec cette spec est rangé ainsi dans le dépôt (décidé le 2026-09-25 : seul `CLAUDE.md` est à la racine) :

```
Verso/
├── CLAUDE.md         consignes permanentes pour Claude Code
├── docs/SPEC.md      cette spec
├── docs/design/      maquettes exportées, jetons de design, logo et icône
├── core/             logique pure (Kotlin JVM), créé à l'étape 2
└── app/              projet Android, créé à l'étape 2
```

Avant la première session avec Claude Code : `git init`, ajouter une `LICENSE` Apache 2.0 et un `.gitignore` Android, puis un premier commit « Spec et maquettes ».

### Étapes

Tout se fait sur `master`, avec un commit par étape, poussé sur GitHub et installé sur le téléphone à la fin de chaque étape pour suivre l'avancement en direct (décidé le 2026-09-25, remplace « une branche et une pull request par étape »). Seul le prototype de l'étape 1 vit sur sa branche, jamais fusionnée. Une étape n'est finie que lorsque tout ce qui est indiqué dans « Finie quand » est vrai sur le téléphone.

| # | Étape | Écrans | Finie quand |
| --- | --- | --- | --- |
| 1 | **Prototype jetable** (branche `spike/readium`, jamais fusionnée) : un EPUB dans Readium en scroll continu, sauvegarde et restauration d'un locator, distinction entre scroll de lecture et fling | 1.10 | Les trois fonctionnent avec deux EPUB de Gutenberg. Les conclusions sont notées dans « Points de vigilance » |
| 2 | **Squelette** : projet `com.maximebier.verso`, thème généré depuis `design/tokens.json` (clair et sombre en V1), typographie et Atkinson en TTF variable, icône copiée depuis `design/logo/`, Room, DataStore, navigation, CI | 1.01 | L'écran 1.01 ressemble à son PNG, en clair et en sombre, et la CI produit un APK |
| 3 | **Import et catalogue** : sélecteur de fichiers, « Ouvrir avec », empreinte SHA-256, doublon, refus, liste, grille, menu ⋮, fiche, suppression | 1.01 – 1.09 | Les critères d'import et de catalogue sont cochés |
| 4 | **Lecture** : scroll continu, réglages imposés à Readium, barre de lecture, sommaire | 1.10 – 1.12 | Un chapitre entier se lit sans saccade, en clair et en sombre |
| 5 | **Position de lecture et carte « Revenir »** : machine à états testée unitairement, sauvegarde au debounce et à `onStop` | 1.14 | Les critères de progression sont cochés, y compris la fermeture brutale et le redémarrage |
| 6 | **Journal et Paramètres** : sessions, feuille du journal, « Reprendre ici », Paramètres | 1.09, 1.13 | Les critères du journal sont cochés |
| 7 | **Passe d'acceptation** : tous les critères V1, TalkBack, texte à 200 %, aucune permission réseau | Tous les V1 | Toutes les cases des « Critères d'acceptation V1 » sont cochées |
| 8 | **README de portfolio** : captures, promesse en une phrase, stack, lien vers cette spec | — | Le README se lit en une minute |

### Règles de travail

- Commencer chaque session par lire `CLAUDE.md` et la partie de la spec qui concerne l'étape.
- Pour chaque écran, comparer le résultat à son PNG de `design/screens/`, en clair et en sombre.
- Ne rien coder de la V2 à la V4 avant la fin de l'étape 7. Le modèle de données et les réglages de lecture doivent seulement ne pas l'empêcher.
- Toute décision qui modifie la spec est reportée dans ce fichier dans le même commit, avec une ligne dans « Historique ».

## Historique

- 2026-09-20 : première version (V1 à V3).
- 2026-09-24 : nom Verso ; police Atkinson en V1 et Literata par défaut en V2, appliquées à toute l'app ; position de lecture distincte, carte « Revenir » et journal des sessions à la place des 5 dernières positions ; affichage liste/grille et menu ⋮ ; Paramètres V1 ; V2 à V3 détaillées écran par écran ; V4 collections ; sections « Interface » et « Confort de lecture ».
- 2026-09-25 : logo retenu (V de deux pages, variante Papier), avec l'icône adaptative, l'icône à thème et le logotype ; maquettes exportées dans `design/` avec `tokens.json` ; identifiant `com.maximebier.verso`, licence Apache 2.0, SDK 26 / 36, Readium 3.4.0 ; ouverture automatique confirmée ; sessions supprimées avec le livre par défaut ; section « Démarrer le développement ».
- 2026-09-25 (plan) : dépôt GitHub privé `MaximeBier/Verso` ; `CLAUDE.md` à la racine, spec et maquettes dans `docs/` ; compileSdk 37 (Readium 3.4.0, Compose 1.12), targetSdk 36 inchangé ; tout sur `master`, un commit par étape, sans pull request ; plan V1 dans `docs/superpowers/plans/`.
- 2026-09-26 : conclusions du prototype Readium (étape 1) : navigateur Compose `readium-navigator-web-reflowable` retenu, seuil de fling 1,0 écran/s, points de vigilance 1 et 2 mis à jour.
- 2026-09-26 (étape 5) : deux seuils de vitesse distincts : fling au relâchement du doigt (1,0 écran/s) et navigation entre deux positions affichées consécutives (4,0 écrans/s) ; un lien interne suivi dans le livre est un saut explicite.
- 2026-09-26 (correctif A) : la confirmation « 25 secondes de lecture » compte la lecture effective (glissés sans fling près du point d'arrivée), depuis le premier glissé et non depuis l'arrivée ; une pause de plus de 15 secondes entre deux glissés (`confirmMaxIdleGapMs`) remet la fenêtre à zéro.
- 2026-09-26 (correctif G) : insets constants pour la lecture (barres système même masquées, découpe de l'écran) ; la barre de lecture et les barres système se superposent au texte, les afficher ou les masquer ne change ni sa mise en page ni la position de lecture.
