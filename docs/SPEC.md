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
| Thème | Automatique par défaut (suit le système), clair, sombre ou nuit au choix dans les Paramètres ; en automatique, le téléphone en sombre donne le thème foncé choisi, Sombre (par défaut) ou Nuit (V1) ; sépia en plus (V2) | Le texte foncé sur fond clair se lit mieux de jour ; le sombre réduit la lumière totale la nuit. En thème foncé, le texte vise 9 à 10:1 et pas plus : au-delà, le texte clair crée un halo sur le fond foncé (halation), surtout avec un astigmatisme |
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

- Texte refait couler en scroll vertical continu dans chaque chapitre (défilement natif, fluide). Au bout du chapitre, un glissé de plus vers le haut ouvre le début du suivant ; en haut du chapitre, un glissé vers le bas ouvre la fin du précédent. Seul un glissé commencé au bord compte, jamais l’élan d’un fling. Aucun texte d’explication. Un changement de chapitre est de la lecture : pas de carte « Revenir », la progression suit (sauf s’il prolonge un scroll accidentel, qui reste une navigation).
- Réglages par défaut fixés en V1 (les réglages utilisateur arrivent en V2, mais le modèle de position doit déjà les supporter) :

| Réglage | Valeur V1 | Remarque |
| --- | --- | --- |
| Police | Atkinson Hyperlegible Next | Embarquée dans l'APK (licence OFL) |
| Taille | 19 sp | Atkinson est large ; 19 sp donne environ 35 caractères par ligne. Literata passera à 20 sp en V2 |
| Interligne | 1,6 | En sp, pour suivre l'agrandissement du texte |
| Espace entre paragraphes | 0,5 × interligne | Soit 1,5 × l'interligne d'un paragraphe à l'autre (WCAG 1.4.8) |
| Marges latérales | 24 dp | |
| Alignement | À gauche, **pas de justification, pas de césure** | Imposé par-dessus le CSS de l'éditeur (réglages utilisateur Readium) |
| Titres de chapitre | Centrés : partie en 14 sp, numéro en 28 sp | Rendu tel que l'EPUB le permet : le CSS de l'éditeur est gardé pour les titres, et quand le livre ne sépare pas partie et numéro (Gutenberg), ils restent alignés à gauche comme le texte |

- Thèmes foncés (Sombre et Nuit) : le texte est allégé et espacé (Atkinson : graisse 380 au lieu de 400, +0,02 em ; Literata : graisse 370, +0,015 em ; la police du système suit Atkinson) et l'interligne passe à 1,7 (+0,1 sur le réglage choisi en V2 ; l'espace entre paragraphes reste 0,5 × l'interligne, soit 16 dp en Atkinson 19 sp), parce qu'un texte clair sur fond foncé paraît plus gras et crée un halo. Ces valeurs sont imposées à Readium, comme les couleurs du thème.
- Tap n’importe où sur le texte : affiche ou masque la barre de lecture, en continu comme en mode pages.
  - En haut : retour à la bibliothèque, titre du livre, chapitre courant.
  - En bas : « 31 % lu », temps restant estimé, barre de progression, boutons « Sommaire » et « Journal ».
- Sommaire : feuille qui s'ouvre sur le chapitre en cours (surligné, « En cours · 31 % »). Les chapitres déjà lus portent « Lu » avec une coche. Tap sur un chapitre = saut direct.
- Écran maintenu allumé pendant la lecture. L'app ne touche jamais à la luminosité.
- Thème : automatique (suit le système) par défaut, ou clair, sombre ou nuit imposé dans les Paramètres (V1). En automatique, quand le système est en sombre, l'app applique le thème foncé choisi dans Paramètres › Affichage › Thème sombre : Sombre (par défaut) ou Nuit. Le changement s'applique tout de suite, y compris dans un livre ouvert. Sépia attend la V2.
- Temps restant : estimé avec une vitesse par défaut de 250 mots/min en V1, puis avec la vitesse mesurée en V2.
- Livre impossible à ouvrir (EPUB à mise en page fixe que le moteur refuse, fichier devenu illisible) : retour à la bibliothèque avec le message « Impossible d’ouvrir « Titre ». », jamais un écran vide.

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
  - hors sommaire (page de titre, couverture), le titre du livre remplace le chapitre : « Votre lecture : Madame Bovary · 0 % » ;
  - boutons : « Rester ici » (la position affichée devient la position de lecture) et « Revenir » (retour à la position de lecture, bouton principal) ;
  - **pas de minuterie** : la carte reste tant que l'utilisateur n'a pas choisi.
- La carte disparaît aussi si l'on se met à lire à la nouvelle place : après environ 25 secondes de mouvement de lecture à moins d'un écran de ce point, la position de lecture y passe automatiquement. Les 25 secondes courent depuis le premier glissé de lecture au nouvel endroit, pas depuis l'arrivée : la carte laissée affichée, un tap ou le début d'un fling ne comptent pas, et une pause de plus de 15 secondes entre deux glissés les remet à zéro.
- La position de lecture est sauvegardée automatiquement à chaque arrêt de scroll (repos de 500 ms constaté par la machine à états, puis écriture regroupée sur 500 ms) et à chaque mise en arrière-plan (`onStop`).
- À la réouverture, l'app revient à la position de lecture, jamais à l'endroit d'un scroll accidentel.
- Tous les seuils (vitesse de fling, nombre d'écrans, délai de confirmation) sont des constantes nommées, faciles à ajuster à l'usage.

### Journal de lecture

- Chaque session de lecture est enregistrée :
  - elle commence à l'ouverture d'un livre, ou à la reprise après plus de 5 minutes d'inactivité ;
  - elle se termine à la mise en arrière-plan, à la fermeture du livre ou après 5 minutes sans interaction ;
  - le temps actif exclut les intervalles de plus de 2 minutes sans scroll ni toucher ;
  - les mots lus ne comptent que les mouvements de lecture, pas les sauts.
  - une session sans lecture (aucun mot lu : ouverture sans lire, sauts, « Rester ici ») n'est pas gardée : ouvrir un livre sans lire ne remplit pas le journal.
  - une session de moins de 30 secondes de temps actif n'est pas gardée non plus (`SessionThresholds.minActiveMs`).
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
- **Affichage** : « Thème », boutons radio Automatique / Clair / Sombre / Nuit (Automatique par défaut ; Sépia s'y ajoute en V2), puis la phrase « En automatique, Verso suit le thème clair ou sombre de votre téléphone. L’écran reste allumé pendant la lecture. », puis « Thème sombre », bouton segmenté Sombre / Nuit (Sombre par défaut) : le thème foncé de l'automatique quand le téléphone est en sombre. Le choix s’applique à toute l’app, lecture comprise.
- **Confidentialité** :
  - phrase d'explication : « Verso n'utilise pas Internet. Vos livres, vos positions, votre journal et vos réglages restent sur ce téléphone. » ;
  - action « Effacer le journal de lecture », « Vos positions de lecture sont conservées. », avec confirmation.
- **À propos** : l'icône de l'app (40 dp, décorative), « Verso » et « Version 1.0.0 » en dessous ; lien vers le code source sur GitHub ; licences open source.

## Critères d'acceptation V1

Chaque critère se vérifie à la main sur le téléphone, avec un EPUB du domaine public (par exemple un roman de Gutenberg ou Wikisource).

- [x] Au premier lancement, la bibliothèque vide propose un seul bouton « Importer un EPUB » et aucun choix de réglage.
- [x] J'importe un EPUB depuis le sélecteur Android et il apparaît dans le catalogue avec titre, auteur et couverture en moins de 5 secondes.
- [x] J'ouvre un EPUB depuis Drive via « Ouvrir avec » et il est importé sans étape supplémentaire.
- [x] Je lis un chapitre entier en scroll continu sans saccade visible ni saut de mise en page.
- [x] Je ferme l'app brutalement (swipe depuis les récents) au milieu d'un paragraphe ; à la réouverture, le même paragraphe est à l'écran.
- [x] Je redémarre le téléphone ; la position est intacte.
- [x] Je scrolle violemment par erreur de 30 pages : la progression ne bouge pas, et la carte « Revenir » me ramène à ma lecture en un tap.
- [x] Je fais le même scroll accidentel, puis je ferme l'app sans toucher la carte ; à la réouverture, je suis à ma position de lecture.
- [x] Je saute au début du livre par le sommaire ; la carte « Revenir » apparaît et me ramène au chapitre d'où je venais.
- [x] Après 25 secondes de lecture réelle à un nouvel endroit, la carte disparaît et la progression suit.
- [x] Après deux sessions de lecture, le journal les montre avec leur heure, leur durée et leur passage, et « Reprendre ici » mène à la fin de chacune.
- [x] Je change la taille de police via la valeur par défaut du code (simulation V2) ; la position restaurée reste le même paragraphe.
- [x] Je corrige le titre d'un livre ; la correction survit à un redémarrage.
- [x] Un fichier non-EPUB refusé affiche un message et ne laisse aucune trace dans le catalogue.
- [x] Le thème sombre du système est respecté sans zone blanche éblouissante.
- [x] Téléphone en sombre, je choisis Nuit dans Paramètres › Affichage › Thème sombre : toute l'app et le texte de lecture passent en Nuit aussitôt, y compris dans un livre ouvert, et tout texte garde au moins 7:1 dans les deux thèmes foncés.
- [x] Avec la taille de texte Android à 200 %, aucun texte n'est coupé et aucune ligne ne demande de scroll horizontal.
- [x] Toutes les commandes font au moins 48 dp et tous les boutons à icône seule ont un intitulé lu par TalkBack.
- [x] Aucune permission demandée hormis l'accès aux fichiers via le sélecteur (pas de réseau, pas de contacts, rien).

## V2, V3 et V4

La V2 rend la lecture confortable pour moi ; la V3 ouvre aux formats et aux usages secondaires ; la V4 organise la bibliothèque. Rien ici ne doit être codé avant que la V1 passe tous ses critères.

### V2 — Confort de lecture

- **Police sélectionnée partout** : Literata par défaut ; Atkinson Hyperlegible Next et police du système au choix. La police choisie s'applique à toute l'app (interface et lecture).
- **Réglages de lecture** (bouton « Aa » de la barre) : feuille ouverte à mi-hauteur, sans voile sur le texte, pour voir l'effet en direct.
  - En haut : Police (3 cartes « Aa »), Taille du texte (boutons − et +), Thème (Auto, Clair, Sépia, Sombre, Nuit).
  - En dessous, en faisant glisser : Interligne (Serré 1,4 / Normal 1,6 / Aéré 1,8), Marges (Étroites / Normales / Larges), Défilement (Continu / Pages, mémorisé par livre).
- **Thèmes** : clair, sépia, sombre, nuit, et automatique (suit le système ; en sombre, le thème foncé choisi dans les Paramètres). Réglable indépendamment du système. Valeurs dans « Interface ».
- **Mode pages** : pages tournées par swipe (un tap affiche la barre, comme en continu), en alternative au scroll, choix mémorisé par livre. Le texte s'aligne sur une grille de lignes (jamais de ligne coupée en bas de page). Pied de page discret : chapitre à gauche, « Page 2 sur 9 » (dans le chapitre) à droite.
- **Barre de lecture V2** : en bas, quatre outils avec texte sous l'icône : Sommaire, Journal, Rechercher, Réglages (« Aa »).
- **États des livres** : à lire, en cours, terminé. L'état est calculé automatiquement à partir de la progression et modifiable dans la fiche (bouton segmenté). Dans la bibliothèque :
  - filtres « Tous / En cours / À lire / Terminés » en pastilles ;
  - un seul bouton « Récents ▾ » ouvre la feuille « Trier et afficher » (tri Récents / Titre / Auteur, affichage Liste / Grille) ;
  - « Nouveau » devient « À lire ».
- **Statistiques** calculées à partir du journal, affichées dans la fiche : temps de lecture (« 2 h 28 en 5 sessions »), vitesse moyenne (mots par minute), « Temps restant estimé : environ 5 h 30 à votre rythme », et un bouton « Voir le journal de lecture ». Interrupteur « Afficher les statistiques » dans les Paramètres (le journal est toujours tenu ; seul l'affichage se désactive).
- **Recherche plein texte** dans le livre ouvert : résultats affichés au fur et à mesure (« 3 résultats pour l'instant »), groupés par chapitre, le mot trouvé mis en évidence par un fond, du gras et un soulignement (jamais la couleur seule).
- **Paramètres V2** : section Lecture avec le choix de police (boutons radio, chaque police montrée sur une phrase du livre), thème, taille et défilement par défaut. Phrase d'aide : « Pour tous les livres et toute l'application. Pendant la lecture, touchez « Aa » pour changer. »

#### Réglages de la V2 (décidés le 2026-09-27)

- Taille du texte : 20 sp par défaut, de 14 à 32 par pas de 1, multipliée par l'échelle de police d'Android.
- Interligne : Serré 1,4 / Normal 1,6 (défaut) / Aéré 1,8. Marges : Étroites 16 dp / Normales 24 dp (défaut) / Larges 32 dp.
- Police, taille, interligne, marges et thème sont communs à tous les livres ; la feuille « Aa » et les Paramètres écrivent les mêmes valeurs. Le défilement est mémorisé par livre ; les Paramètres fixent celui des livres sans choix propre.
- Mode pages : un tour de page (swipe, action TalkBack « Page suivante » / « Page précédente » du pied de page) est un geste de lecture ; un feuilletage rapide (plus de 3 écrans en 5 s, seuil V1) est une navigation et affiche la carte « Revenir ». Après une navigation, deux pages lues au nouvel endroit (au moins 25 s d'écart entre les deux tours, au plus 90 s de pause, dérive jusqu'à 2,5 écrans) confirment la nouvelle position (`ReadingThresholds.forPages()`) ; les règles de la machine à états ne changent pas, seuls ces deux seuils. Une seule colonne, même en paysage. Changer de défilement garde le texte affiché, sans saut ni geste pour la machine à états.
- État : « À lire » si jamais ouvert, « Terminé » à partir de 99 %, « En cours » sinon ; un choix manuel dans la fiche l'emporte jusqu'au choix manuel suivant.
- Vitesse de lecture : mots lus / temps actif des sessions du livre ; 250 mots par minute tant qu'il n'y a aucune session.
- Recherche : toucher un résultat est un saut explicite (carte « Revenir ») ; dans le texte, le mot trouvé est marqué par un fond et un soulignement.

Design et découpage : `docs/superpowers/specs/2026-09-27-verso-v2-design.md`.

#### Critères d'acceptation V2

Chaque critère se vérifie à la main sur le téléphone, avec les EPUB réels de test.

- [x] Au premier lancement après la mise à jour, l'app et le texte de lecture sont en Literata ; la bibliothèque, les positions et le journal de la V1 sont intacts.
- [x] Je choisis Atkinson puis la police du système dans les Paramètres : toute l'app et le texte de lecture changent de police, et le choix survit à un redémarrage (vérifié après un arrêt forcé ; le redémarrage complet du téléphone reste à faire par Maxime).
- [x] Je change la taille, l'interligne et les marges pendant la lecture : le texte change aussitôt et le même paragraphe reste à l'écran.
- [x] Les thèmes Sépia et Nuit s'appliquent à toute l'app et au texte, sans zone d'une autre couleur ; Automatique suit le thème du téléphone (à vérifier par Maxime : changement du thème système). Vérifié avec Noir le 2026-09-27, puis avec Nuit, qui le remplace, le 2026-09-29.
- [x] Dans les cinq thèmes, tout texte a un contraste d'au moins 7:1 (captures vérifiées).
- [x] La barre de lecture montre Sommaire, Journal, Rechercher et Réglages, chacun avec son texte sous l'icône.
- [x] « Aa » ouvre la feuille à mi-hauteur sans voile ; le texte derrière reste visible et change en direct ; Interligne, Marges et Défilement apparaissent en faisant glisser la feuille.
- [x] En mode pages, je tourne les pages par swipe, aucune ligne n'est coupée en bas, et le pied de page affiche le chapitre et « Page x sur y ».
- [x] Je passe un livre en mode pages et un autre en continu : chacun rouvre dans son mode, et la position est gardée au changement de mode.
- [ ] En mode pages, je feuillette vite plusieurs pages : la progression ne bouge pas et la carte « Revenir » me ramène à ma page en un tap ; en lisant page par page, la progression suit.
- [x] Un livre jamais ouvert est « À lire », un livre commencé « En cours », un livre lu à 99 % « Terminé » ; les filtres en pastilles n'affichent que les livres de l'état choisi.
- [x] Je change l'état d'un livre dans la fiche ; il est rangé sous ce filtre et le reste après un redémarrage, même si je le relis.
- [x] « Récents ▾ » ouvre « Trier et afficher » ; le tri et l'affichage choisis s'appliquent et survivent à un redémarrage.
- [x] Après deux sessions, la fiche affiche le temps de lecture, le nombre de sessions, la vitesse moyenne et le temps restant estimé à mon rythme ; « Voir le journal de lecture » ouvre le journal.
- [x] L'interrupteur « Afficher les statistiques » désactivé masque les statistiques de la fiche ; le journal continue d'être tenu.
- [x] Les Paramètres montrent la section Lecture de la maquette 2.09 : chaque police sur la phrase du livre, thème, taille et défilement par défaut.
- [x] Je cherche un mot : les résultats arrivent au fur et à mesure, groupés par chapitre, avec le mot marqué par un fond, du gras et un soulignement.
- [x] Je touche un résultat : le passage s'affiche avec le mot marqué, et la carte « Revenir » me ramène où j'étais.
- [ ] Avec la taille de texte Android à 200 %, aucun texte des nouveaux écrans n'est coupé, et toutes les nouvelles commandes font au moins 48 dp avec un intitulé TalkBack.
- [ ] Toujours aucune permission réseau ni autre permission.

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
| 1.10 / 1.11 | Lecture / Lecture avec la barre affichée (+ sombre, + nuit pour 1.10) | V1 |
| 1.12 | Sommaire (+ sombre) | V1 |
| 1.13 | Journal de lecture (+ sombre) | V1 |
| 1.14 | Retour à votre lecture (carte « Revenir ») | V1 |
| 2.01 | Barre de lecture V2 | V2 |
| 2.02 | Réglages de lecture (+ sombre) | V2 |
| 2.03 / 2.04 | Thème sépia / Thème nuit | V2 |
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

| Rôle | Clair | Sombre | Nuit | Sépia (V2) |
| --- | --- | --- | --- | --- |
| Fond | `#F5F1E8` | `#1E1B18` | `#1D1813` | `#EFE3CC` |
| Surface (cartes, barres, feuilles) | `#ECE5D9` | `#25221E` | `#241E18` | `#E6D8BD` |
| Surface haute | `#E4DCCE` | `#2D2924` | `#2C251E` | `#DDCDAF` |
| Texte | `#1F1B16` (15,2:1) | `#CBC5BC` (10:1) | `#CFBCA0` (9,5:1) | `#2E2419` (12:1) |
| Texte secondaire | `#4A433B` (8,6:1) | `#BBB5AD` (8,4:1) | `#C1B095` (8,3:1) | `#443826` (7,3:1) |
| Accent | `#7A3021` (8,2:1) | `#CEB28A` (8,5:1) | `#D6AA7A` (8,3:1) | `#66281C` (8,8:1) |
| Texte sur accent | `#FFFFFF` (9,2:1) | `#2E271F` (7,3:1) | `#2D2319` (7,2:1) | `#FFFFFF` |
| Piste des barres | `#DDD4C5` | `#3D3933` | `#3C342B` | `#D8C7A8` |
| Contour | `#7D7366` | `#7B7670` | `#7E7260` | `#7A6A52` |
| Séparateur | `#D3CABB` | `#3D3933` | `#3C342B` | `#D3C2A2` |
| Sélection (fond / texte) | `#EBD7C6` / `#2B170C` | `#534331` / `#EADFCF` | `#4E3B28` / `#E0D3C3` | `#E2C9AE` / `#2B170C` |
| Danger | `#8A2318` | `#E8A89C` | `#E5A48D` | `#7C2016` |
| Inverse (snackbar, carte de retour) | `#2E2A25` / `#F5F1E8` | `#CBC5BC` / `#1E1B18` | `#CFBCA0` / `#1D1813` | `#3A2E22` / `#EFE3CC` |
| Accent sur inverse | `#F0C9A0` | `#4F2B22` | `#4C241A` | `#F0C9A0` |
| Surlignage (V2/V3) | `#F3D9A4` | `#362D1D` | `#352A19` | `#E9C98C` |

- Le fond clair n'est pas du blanc pur (environ 12 % de lumière en moins), et les fonds foncés ne sont pas du noir pur.
- **Thèmes foncés** : Sombre est un gris chaud, Nuit un brun au texte beige ambré. Le texte y vise 9 à 10:1 et pas plus (halo du texte clair sur fond foncé, surtout avec un astigmatisme) ; tout texte reste à au moins 7:1. Sur la sélection, le texte est toujours « texte sur sélection ». Les liens du texte de lecture prennent l'accent de Sombre dans les deux thèmes foncés (8,5:1 et 8,7:1) : leur couleur est fixée à la création du navigateur Readium, qui n'est pas recréé entre Sombre et Nuit.
- Nuit remplace le thème Noir (V2) depuis le 2026-09-29 ; un choix Noir déjà enregistré se lit comme Nuit.
- **Règle de l'accent** : l'accent sert uniquement :
  - au bouton principal de l'écran ;
  - à la progression du livre en cours (barre de 6 dp) ;
  - aux commandes de sélection (interrupteurs, cases, boutons radio, carte de police choisie) et au champ actif.
  - Tout le reste est à l'encre : boutons texte, titres de section, liens d'action.
- **Barres de progression** : 6 dp en accent pour le livre en cours (carte Reprendre, barre de lecture, résumé d'une collection) ; 4 dp à l'encre pour tous les autres.
- **Vignettes générées** : couleur sourde choisie à partir du titre, monogramme ou titre en texte clair, contraste d'au moins 4,5:1.
- **Couleur des liens de lecture** (Readium) : posée une seule fois à la création du navigateur, pas par les réglages — donc partagée dans chaque famille de thème (Clair et Sépia ; Sombre et Noir), qui ne recrée pas l'activité entre elles. Elle utilise l'accent de la palette Clair pour la famille claire (tient 7:1 sur le fond sépia) et l'accent de la palette Sombre pour la famille sombre, jamais l'accent propre au thème affiché.

### Typographie et dimensions

| Élément | Valeurs |
| --- | --- |
| Échelle de texte (sp) | 14 légendes et méta · 16 texte d'interface, boutons · 18 titres de livres · 22 titres d'écran, de feuille et de fenêtre · 28 grands titres (écran vide, numéro de chapitre) |
| Texte de lecture | 19 sp en Atkinson, 20 sp en Literata, interligne 1,6 (1,7 en thème foncé) |
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

- Contraste d'au moins 7:1 pour tout texte, dans tous les thèmes, et pas plus d'environ 10:1 pour le texte en thème foncé. Au moins 3:1 pour les barres, contours et icônes.
- Cibles tactiles de 48 dp minimum.
- Tailles de texte **et interlignes** en sp. L'app reste utilisable avec le texte Android à 200 % (mise à l'échelle non linéaire d'Android 14). Le texte de lecture suit aussi la taille de police d'Android (19 sp convertis par le système) ; la V2 ajoutera son propre réglage « Taille du texte ».
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
| V du logotype | Accent du thème affiché (`#7A3021` en clair, `#CEB28A` en sombre, `#D6AA7A` en nuit) |
| « erso » du logotype | Texte du thème affiché, en Atkinson Hyperlegible Next Bold vectorisée |

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
- **Dans l'app** (depuis le 2026-09-29) :
  - l'en-tête de la bibliothèque montre le **logotype** (le V du logo et « erso », en tracés) : un seul vector drawable, `res/drawable/ic_logotype.xml`, converti de `design/logo/svg/verso-logotype-clair.svg`, environ 21 dp de haut, intitulé TalkBack « Verso ». La couleur de chaque tracé vient du thème (V en accent, « erso » en texte) ; il garde sa forme quelle que soit la police de l'app et sa taille en dp ne suit pas la taille de texte d'Android ;
  - la bibliothèque vide (1.01) montre l'**icône de l'app** à 64 dp, et Paramètres › À propos (1.09) à 40 dp : les calques de l'icône de lancement (`ic_launcher_background` et `ic_launcher_foreground`) sous un masque aux coins arrondis (22 sur 72), décorative.
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
| Astigmatisme | Le texte clair sur fond foncé « bave » (halation), d'autant plus que le contraste est fort et que l'œil est astigmate | Thèmes foncés à 9–10:1 plutôt que 14:1, texte allégé et espacé, interligne 1,7 ; thème Nuit plus chaud au choix |

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
| Polices | Atkinson Hyperlegible Next (V1) et Literata (V2), en TTF variables dans `res/font` pour Compose et `assets/fonts` pour la WebView de Readium (licence OFL) | Même police dans l'interface Compose et dans le rendu Readium. Les woff2 de `design/fonts/` ne servent qu'aux maquettes HTML : Android a besoin des TTF, publiés par les dépôts Google Fonts de chaque police |
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

- Moteur de lecture (risque n° 1) : Verso utilise le navigateur EPUB classique de Readium (`EpubNavigatorFragment`), un chapitre à la fois, défilé nativement par la WebView. Le navigateur Compose `readium-navigator-web-reflowable`, retenu d’abord pour son enchaînement continu des chapitres, dessinait le fling à 30 images/s (une vsync sur deux, en debug comme en release, cause interne au navigateur) ; le classique tient 60 images/s sans image perdue (Pixel 6a, 4 flings mesurés par `dumpsys gfxinfo`). Contrepartie acceptée : le changement de chapitre par un glissé au bord. Détails : `docs/superpowers/specs/2026-09-26-navigateur-classique-design.md` et `docs/superpowers/plans/spike-readium-conclusions.md`.
- Lecture ou navigation (risque n° 2) : la fin de geste et sa vitesse sont mesurées au relâchement du doigt, en écrans par seconde. Lecture : médiane 0,18, 90ᵉ centile 0,32 ; fling : minimum 0,96 (0 pour un scroll rapide tenu avant de lever le doigt), médiane 1,86 — gestes simulés par adb ; les flicks d'un vrai doigt vont de 1,0 à 7,4. Seuil de départ `flingScreensPerSecond = 1.0`, appliqué à la seule vitesse au relâchement (décision du fling) ; la vitesse entre deux positions affichées consécutives a son propre seuil, `displayedSpeedNavigationScreensPerSecond = 4.0` (en plein glissé de lecture, la vitesse instantanée dépasse la vitesse au relâchement) ; la fenêtre « 3 écrans en 5 s » est confirmée côté lecture (0,56 écran au plus), à revérifier avec une vraie séance de lecture avant l'étape 5.
- La sauvegarde de position doit passer par `onStop` en plus du debounce de scroll, sinon la fermeture brutale perd les dernières secondes.
- Les réglages utilisateur Readium doivent l'emporter sur le CSS de l'éditeur (alignement à gauche, pas de césure, police et interligne de Verso).
- La graisse allégée du thème sombre suppose des polices variables (axe `wght`) ou une graisse intermédiaire embarquée.
- Tailles et interlignes en sp, tester avec le texte Android à 200 %. Gérer les insets de la barre d'état et de la barre de gestes, en particulier quand la barre de lecture s'affiche en mode immersif. Les barres (lecture et système) sont une surcouche : le texte reçoit des insets constants (barre d'état et côtés des barres système même masquées, découpe de l'écran ; rien en bas, le texte va jusqu'au bas de l'écran sous la barre de navigation masquée), si bien que les afficher ou les masquer ne change jamais sa mise en page ni la position de lecture.
- Tester avec des EPUB réels et imparfaits (Gutenberg en génère de très variés), pas seulement un fichier propre.
- Le manifeste ne déclare pas `INTERNET`. Vérifier dans le manifeste fusionné qu'aucune dépendance ne l'ajoute, et la retirer avec `tools:node="remove"` si besoin. La sauvegarde Android est désactivée (`allowBackup="false"`, `dataExtractionRules` qui exclut la sauvegarde sur le compte Google et le transfert d'appareil à appareil, `fullBackupContent` qui exclut tout avant Android 12) : sinon le système enverrait livres, base et réglages hors du téléphone sans aucune permission. Le garde-fou `scripts/check-no-internet.sh` vérifie les deux.

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
| 2 | **Squelette** : projet `com.maximebier.verso`, thème généré depuis `design/tokens.json` (clair, sombre et nuit en V1), typographie et Atkinson en TTF variable, icône copiée depuis `design/logo/`, Room, DataStore, navigation, CI | 1.01 | L'écran 1.01 ressemble à son PNG, en clair, en sombre et en nuit, et la CI produit un APK |
| 3 | **Import et catalogue** : sélecteur de fichiers, « Ouvrir avec », empreinte SHA-256, doublon, refus, liste, grille, menu ⋮, fiche, suppression | 1.01 – 1.09 | Les critères d'import et de catalogue sont cochés |
| 4 | **Lecture** : scroll continu, réglages imposés à Readium, barre de lecture, sommaire | 1.10 – 1.12 | Un chapitre entier se lit sans saccade, en clair, en sombre et en nuit |
| 5 | **Position de lecture et carte « Revenir »** : machine à états testée unitairement, sauvegarde au debounce et à `onStop` | 1.14 | Les critères de progression sont cochés, y compris la fermeture brutale et le redémarrage |
| 6 | **Journal et Paramètres** : sessions, feuille du journal, « Reprendre ici », Paramètres | 1.09, 1.13 | Les critères du journal sont cochés |
| 7 | **Passe d'acceptation** : tous les critères V1, TalkBack, texte à 200 %, aucune permission réseau | Tous les V1 | Toutes les cases des « Critères d'acceptation V1 » sont cochées |
| 8 | **README de portfolio** : captures, promesse en une phrase, stack, lien vers cette spec | — | Le README se lit en une minute |

### Règles de travail

- Commencer chaque session par lire `CLAUDE.md` et la partie de la spec qui concerne l'étape.
- Pour chaque écran, comparer le résultat à son PNG de `design/screens/`, en clair, en sombre et en nuit.
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
- 2026-09-26 (revue finale) : sauvegarde Android désactivée (`allowBackup="false"`, `dataExtractionRules` sans sauvegarde sur le compte Google ni transfert d'appareil à appareil), pour tenir « Rien ne quitte le téléphone ».
- 2026-09-26 (revue finale) : un livre que le moteur refuse (mise en page fixe) ou un fichier illisible ramène à la bibliothèque avec « Impossible d’ouvrir « Titre ». » (nouveau message `library_open_failed`).
- 2026-09-26 (revue finale) : écarts acceptés : la carte « Revenir » affiche le titre du livre quand la position est hors sommaire ; les titres de chapitre gardent le CSS de l'éditeur et ne sont pas centrés quand l'EPUB ne sépare pas partie et numéro. La carte « Reprendre » lit le chapitre et l'extrait enregistrés avec la position de lecture.
- 2026-09-26 (retour utilisateur) : le choix du thème (Automatique / Clair / Sombre) passe de la V2 à la V1, dans Paramètres › Affichage ; sépia et noir restent en V2. Sur Android 12 et plus, le choix s’applique aussi à la fenêtre (`UiModeManager.setApplicationNightMode`), ce qui recrée l’activité au changement.
- 2026-09-26 (retour utilisateur) : fling à 30 images/s avec le navigateur Compose de Readium ; passage au navigateur classique (Fragment, 60 images/s). Le défilement reste continu dans un chapitre, et on change de chapitre par un glissé de plus au bord, sans texte d’explication. Le changement de chapitre compte comme lecture (`GestureEnded.chapterTurn`), sauf s’il prolonge un scroll accidentel.
- 2026-09-27 (retour utilisateur) : bande vide trop haute en haut du texte : Readium ajoutait la découpe de l'écran aux insets déjà posés par Verso (`shouldApplyInsetsPadding = false`). Le texte commence sous la barre d'état et finit au-dessus de la barre de navigation, même masquées (correctif G).
- 2026-09-27 (retour utilisateur) : le texte de lecture suit la taille de police d'Android (échelle non linéaire d'Android 14), comme le reste de l'app ; le calcul des écrans utilise la même échelle. Les sessions sans lecture (aucun mot lu) ne sont plus gardées au journal ; celles déjà enregistrées sont retirées à l'ouverture d'un livre. Titres et auteurs abrégés par « … » à 200 % acceptés.
- 2026-09-27 (relecture, lot 1) : le navigateur classique ne rapporte la position qu'après 100 ms sans défilement, jamais pendant l'inertie d'un fling. La fin d'un geste attend donc la fin réelle du défilement natif de la WebView puis la position qui suit (`SCROLL_QUIET_MS`, `POSITION_WAIT_MS`) ; un toucher pendant le défilement n'est ni un tap ni un changement de chapitre (`SCROLL_ACTIVE_MS`). Garde-fous de la machine à états : fling sans position → navigation ouverte jusqu'à l'arrivée (`flingPositionMaxWaitMs`) ; saut vers une ancre déjà à l'écran → arrivée sur le texte affiché (`jumpArrivalMaxWaitMs`) ; mise en arrière-plan → repos immédiat. Entrées du sommaire hors de l'ordre de lecture ignorées.
- 2026-09-27 (retour utilisateur) : lecture en plein écran : le texte descend jusqu'au bas de l'écran, sous la barre de navigation masquée (plus de bande vide en bas) ; la bande du haut, sous la barre d'état et la caméra, reste.
- 2026-09-27 (relecture, lot 2) : un livre à mise en page fixe est refusé avant de devenir le dernier livre ouvert ; un échec d'ouverture ramène toujours à la bibliothèque, même depuis la fiche ; « Ouvrir avec » pendant la lecture ramène à la bibliothèque ; un second tap pendant une transition est ignoré et un seul lecteur est empilé ; la réouverture au lancement montre le livre sans afficher la bibliothèque avant ; un intent relancé depuis les applications récentes n'est pas réimporté.
- 2026-09-27 (relecture, lot 3) : la position de lecture est écrite même si le navigateur ne répond pas pour l'extrait (délai d'une seconde, `EXCERPT_TIMEOUT_MS`), et une écriture commencée va au bout ; « Remplacer » un livre identique garde le fichier en place ; les fichiers sont remplacés de façon atomique ; un import annulé après l'insertion garde ses fichiers ; un fichier de réglages corrompu revient aux valeurs par défaut.
- 2026-09-27 (relecture, lot 4) : comptage des mots corrigé (apostrophe typographique ’ dans un mot, guillemets en entités) ; un seul calcul de pourcentage ; les libellés d'emplacement (« Partie II, chap. I », « Chap. IV », « A → B ») viennent de `strings.xml` ; « Environ 1 min restante » au singulier ; l'extrait de la carte « Reprendre » commence à la première ligne visible ; les sessions d'un livre sans mots comptés se jugent sur la progression. Les livres déjà importés gardent leur total de mots jusqu'à un nouvel import.
- 2026-09-27 (relecture, lot 5) : TalkBack annonce le libellé des champs de la fiche (« Auteur, zone d'édition ») ; lignes de livre et de sommaire annoncées comme boutons ; liens du texte à la couleur d'accent, visités compris (7:1 en clair et en sombre) ; le sélecteur « Importer » accepte aussi les fichiers déclarés `application/octet-stream` ; le livre de la carte « Reprendre » n'est plus répété dans la liste (le compteur les compte tous, maquette 1.02) ; marges latérales de 24 dp quelle que soit la largeur (gouttière de ReadiumCSS par paliers) ; la bascule liste / grille marque le mode choisi d'un trait en plus du fond ; voile de la barre de navigation (API 26 à 28) tiré des jetons.
- 2026-09-27 (relecture, lot 6) : aucun avertissement lint (exceptions justifiées dans `app/lint.xml`) ; « Ouvrir avec » n'accepte que `content://` (sans permission de stockage, `file://` serait illisible) ; la CI contrôle aussi le manifeste release ; couvertures en JPEG ; `FragmentReaderController` dans son propre fichier ; un seul parcours du sommaire (`preorder`, `:core`) ; positions de l'ordre de lecture calculées une fois. Écartés ou reportés, avec leurs raisons, dans `.superpowers/sdd/2026-09-27-relecture/arbitrage.md`.
- 2026-09-27 : critères V1 1, 3 et 17 vérifiés par Maxime, V1 terminée. V2 : valeurs des réglages, défilement par livre, tours de page comme lecture, état manuel prioritaire, vitesse mesurée, recherche comme saut explicite, critères d'acceptation V2 ; étapes 9 à 16 dans `docs/superpowers/specs/2026-09-27-verso-v2-design.md`.
- 2026-09-27 (étape 9) : réglages de lecture et polices — migration Room 1 → 2, remise en page (`relayout`) qui garde le même locator au changement de réglage, Row passant en FlowRow à 200 % pour l'accessibilité, Literata par défaut appliquée à toute l'app et au texte de lecture. Critère d'acceptation V2 1 vérifié.
- 2026-09-27 (étape 10) : thèmes sépia et noir, quatre palettes vérifiées à 7:1 (texte) et 3:1 (non-texte) par `PaletteContrastTest`. Trois jetons ajustés pour tenir 7:1 sans casser le partage de couleur imposé par `ReaderController.submit` (même accent en clair/sépia et en sombre/noir, sans recréer le fragment) : sépia `textSecondary` `#4C3E2A` → `#443826`, sépia `surface` `#E6D8BD` → `#ECE1CB` (accent/surface tenait à 6,6:1 avec l'ancienne valeur), sépia `danger` `#8A2318` → `#7C2016` ; noir `inverseAccent` (carte « Revenir », snackbar) `#7A3021` → `#68291C`. Captures V2 dans `V2ScreenCatalog` / `V2ScreenshotTest` (24 PNG, `build/outputs/roborazzi/v2/`).
- 2026-09-27 (correctif, revue téléphone) : barre de navigation à trois boutons presque blanche en sépia (visible sur `build/acceptance/20260927-223051/05-bibliotheque-sepia.png`) : `SystemBarStyle.auto` force `isNavigationBarContrastEnforced` à vrai sur API 29+ et ignore la couleur donnée ; remplacé par `light()`/`dark()` selon `AppTheme.isDark` dans `MainActivity.enableEdgeToEdge`. Même défaut retrouvé sous les feuilles modales (`Sommaire`, `Journal`), qui vivent dans leur propre fenêtre Dialog non couverte par ce réglage : `VersoBottomSheet` teinte désormais explicitement cette fenêtre (`ui/theme/DialogNavigationBar.kt`). Vérifié dans les quatre thèmes, fenêtre principale et feuilles modales.
- 2026-09-27 (étape 11) : barre de lecture V2 (Sommaire, Journal, Réglages) et feuille « Réglages de lecture » (2.01, 2.02) : réglages et thème écrits depuis le lecteur vers Readium par un seul chemin (`ReaderViewModel.submitStyle()` → `ReaderController.submit`), sans recréer le fragment ; le défilement redevient propre à chaque livre à l'ouverture. Réglages lus avant la création du lecteur (plus de re-création au premier rendu). Vérifié sur le téléphone : taille, police (Atkinson, Système, Literata), interligne, marges et thème changent aussitôt le texte affiché derrière la feuille sans déplacer le paragraphe en haut ; le choix de police survit à un arrêt forcé (redémarrage complet du téléphone laissé à Maxime). Critères d'acceptation V2 2 et 3 vérifiés.
- 2026-09-28 (étape 12) : mode pages (2.05). Seuils de confirmation propres au mode pages (pause jusqu'à 90 s, dérive jusqu'à 2,5 écrans, `ReadingThresholds.forPages()`), posés à l'ouverture selon le mode du livre et à chaque bascule, sans changer les règles de la machine à états ; tours de page (swipe, tap latéral, actions TalkBack « Page suivante » / « Page précédente » portées par le pied de page, seul élément focalisable du mode pages) ; bascule continu ↔ pages par la remise en page du contrôleur (`relayout`, `MODE_SWITCH_SETTLE_MS` = 300 ms, suffisant sur le téléphone), sans saut ni geste pour la machine à états. « Page x sur y » compte les pages du chapitre même quand un fichier en contient plusieurs (parties de Madame Bovary chez Gutenberg) : pages et ancres du sommaire mesurées dans la WebView, et le chapitre du pied de page suit ces ancres. Défilement mémorisé par livre depuis la feuille « Aa ». Vérifié sur le téléphone : même paragraphe à la bascule dans les deux sens, un tour par tap, swipe, aucune ligne coupée, fin de chapitre et fin de fichier vers la page 1 du suivant, compte exact (Candide chapitre V : 7 pages ; Madame Bovary IX 21, X 19), chaque livre rouvre dans son mode après un arrêt forcé. Critères d'acceptation V2 7, 8 et 9 vérifiés ; 10 pas encore (quatre pages feuilletées vite restent de la lecture, voir `docs/acceptance-v2.md`).
- 2026-09-29 (retour utilisateur) : un tap n’importe où sur le texte affiche ou masque la barre de lecture, en continu comme en mode pages ; le tap latéral ne tourne plus les pages (swipe seulement, plus les actions TalkBack du pied de page). Mesuré sur le téléphone : seule la bande centrale (30 à 70 % de la largeur) réagissait, les taps à côté semblaient perdus. Un tap déjà traité par l’app dont Readium envoie le signal après l’appui suivant (taps rapprochés) n’est plus compté deux fois (`TAP_ECHO_MAX_MS` = 1 s).
- 2026-09-29 (retour utilisateur) : les sessions de moins de 30 s de temps actif (la durée affichée au journal) ne sont pas gardées, comme les sessions sans lecture : jamais écrites, et celles déjà enregistrées sont retirées à l'ouverture d'un livre. La session en cours reste affichée « En cours » au journal.
- 2026-09-29 (maquettes, `docs/CHANGEMENTS.md`) : logo dans l'interface (logotype `ic_logotype` dans l'en-tête de la bibliothèque, icône de l'app en 1.01 et en 1.09 avec « Verso » / « Version 1.0.0 ») ; thème Sombre adouci (texte à 10:1 au lieu de 14:1) et thème Nuit, qui remplace Noir (un choix Noir enregistré se lit comme Nuit) ; « Thème sombre : Sombre / Nuit » dans Paramètres › Affichage (préférence `dark_theme_variant`), appliqué par l'automatique quand le téléphone est en sombre ; en thème foncé, Readium reçoit l'interligne +0,1 (1,7 en Normal), la graisse et l'espacement des lettres par police. Sépia garde ses jetons de l'étape 10 (l'export les ramenait aux valeurs d'avant l'ajustement à 7:1) ; les jetons propres au code (`onDanger`, `onCover`, `onInverseAccent`, `progressInk`, `scrim`, `coverPalette`), absents de l'export, sont gardés et définis pour Nuit. Le couple texte/sélection sort de `PaletteContrastTest` (le texte sur la sélection est toujours `onSelection`) ; un test borne le texte des thèmes foncés entre 9 et 10:1. Vérifié sur le téléphone (système en sombre, captures dans `build/acceptance/20260929-maquette/`) : un choix Noir enregistré ressort en Nuit ; Sombre, Nuit et Auto changent aussitôt le livre ouvert (fond mesuré `#1E1B18` en Sombre, `#1D1813` en Nuit, texte `#CBC5BC` / `#CFBCA0`) ; « Thème sombre : Nuit » fait passer l’automatique en Nuit dans les Paramètres, la bibliothèque et le livre rouvert ; interligne Serré 1,4 + 0,1 mesuré à 30 dp en 20 sp ; logotype, ligne À propos et thème Clair conformes aux maquettes. 1.01 (bibliothèque vide) vérifiée sur les captures Roborazzi seulement.
- 2026-09-28 (correctif, retour utilisateur) : surface sépia presque identique au fond (feuille « Réglages de lecture » indistincte du texte). Cause : l'étape 10 avait éclairci `surface` sépia (`#E6D8BD` → `#ECE1CB`) pour que l'accent (`#7A3021`) tienne 7:1 dessus. Correctif inverse : `surface` sépia revient à `#E6D8BD` (valeur de la maquette) et c'est l'accent sépia qui s'assombrit (`#7A3021` → `#66281C`, 8,8:1 sur le fond, 7,1:1 sur `surfaceHigh`) pour tenir 7:1 en texte sur `background`, `surface` et `surfaceHigh` sépia et ≥ 3:1 en non-texte, sans changer la teinte. La couleur des liens de lecture (`ReadingStyle`, posée une fois par famille de thème) ne dépend donc plus de l'accent propre à chaque thème : elle est fixée à l'accent de la palette Clair pour la famille claire (Clair et Sépia) et à celui de la palette Sombre pour la famille sombre (7:1 vérifié sur le fond sépia). `PaletteContrastTest` gagne un contrôle « surface distincte du fond » (`MIN_BACKGROUND_SURFACE_CONTRAST`, contraste ≥ 1,05 entre `background` et `surface` dans chaque palette), qui aurait détecté la régression (sépia buguée : ≈ 1,02:1 ; après correctif et maquettes du 2026-09-29 : Nuit 1,07, Sombre 1,08, Sépia et Clair 1,11).
- 2026-09-30 (étape 13) : filtre de la bibliothèque non enregistré, compteur des livres du filtre choisi, carte « Reprendre » gardée sous tous les filtres, « Aucun livre pour ce filtre. » quand un filtre est vide ; le tri et l’affichage passent dans la feuille « Trier et afficher ». Seuil `MIN_BACKGROUND_SURFACE_CONTRAST` abaissé à 1,05 à l’intégration pour les palettes Sombre et Nuit des maquettes du 2026-09-29.
- 2026-09-30 (étape 14) : vitesse mesurée utilisée pour tous les temps restants, même statistiques masquées ; « Voir le journal de lecture » ouvre le livre avec le journal ; section « Statistiques » absente sans session, vitesse et temps restant absents sans vitesse mesurée (moins d’une minute de lecture active) ; Paramètres : « Affichage » remplacé par la section Lecture (thème, taille et défilement par dialogues). « Thème sombre : Sombre / Nuit » (maquettes du 2026-09-29) passe dans la section Lecture, sous « Thème », la maquette 2.09 étant antérieure à ce réglage. Vérifié sur le téléphone (`build/acceptance/20260930/`) : critères V2 11 à 16.
- 2026-09-30 (étape 15) : recherche par le service de Readium (casse et accents ignorés, deux caractères au moins) ; statuts de fin « N résultats dans le livre » et « Aucun résultat dans le livre » (absents des maquettes) ; le mot trouvé est marqué dans le texte jusqu’au geste suivant ; la recherche rouverte garde sa requête. Sur le téléphone, le fond de la marque n’apparaissait pas : ReadiumCSS rend transparent le `background-color` de tout élément du texte dès qu’une couleur de fond est choisie (`--USER__backgroundColor`), décorations comprises ; le fond est posé par une ombre intérieure (`box-shadow: inset`), fondue comme avant (`darken` / `lighten`), texte inchangé. Relectures : pied de page du mode pages empilé à 200 % (le chapitre était coupé), écran de recherche qui ne laisse plus passer les touchers au texte dessous. Critères V2 6, 17 et 18 vérifiés.
