# Verso

Lecteur d'ebook Android (EPUB), personnel et publié en portfolio. Promesse : j'ouvre l'app, je suis exactement où j'étais, et le texte est agréable à lire.

## À lire avant de coder

- `docs/SPEC.md` : la référence. Comportement, critères d'acceptation, décisions, étapes de développement (section « Démarrer le développement »). Elle l'emporte sur tout le reste, maquettes comprises.
- `docs/design/README.md` : les maquettes. Un PNG et un HTML par écran dans `docs/design/screens/` et `docs/design/html/`, identifiés comme dans la spec (1.01, 1.14…).
- `docs/design/tokens.json` : couleurs des thèmes, typographie, arrondis, tailles. Le thème Compose en est tiré, aucune couleur en dur ailleurs.
- `docs/design/logo/README.md` : icône de l'app, déjà prête pour `app/src/main/res/`.

## Projet

- Kotlin, Jetpack Compose, Material 3, Readium Kotlin toolkit 3.4.0, Room, DataStore, Coil.
- Package `com.maximebier.verso`. minSdk 26, targetSdk 36, compileSdk 37 (exigé par Readium 3.4.0).
- Une activité, navigation Compose, MVVM.
- Licence Apache 2.0.

## Règles

- **V1 seulement** tant que tous les critères d'acceptation V1 ne sont pas cochés. Ne pas coder la V2, la V3 ou la V4 par anticipation.
- **Position de lecture** : toujours un locator Readium, jamais des pixels. La logique lecture/navigation et la carte « Revenir » forment une machine à états sans dépendance Android, couverte par des tests unitaires. Les seuils sont des constantes nommées.
- **Textes** : en français, dans `res/values/strings.xml`, repris mot pour mot des maquettes. Espace fine insécable avant `; : ! ?` et `%`, apostrophe typographique, « Environ » plutôt que « ≈ ».
- **Accessibilité** : texte à au moins 7:1 de contraste, commandes de 48 dp minimum, tailles et interlignes en sp, texte à 200 % sans coupure, un intitulé TalkBack sur chaque bouton à icône seule, jamais la couleur seule pour porter une information.
- **Confidentialité** : aucune permission réseau, aucune analytics. Rien ne quitte le téléphone.
- **Lecture** : alignement à gauche, sans justification ni césure, imposé par-dessus le CSS de l'éditeur.
- Tout sur `master`, un commit par étape (`Étape N : …`), poussé et installé sur le téléphone à la fin de chaque étape. Pas de pull request. Toute décision qui change la spec est reportée dans `docs/SPEC.md` dans le même commit.
- Plan d'implémentation V1 : `docs/superpowers/plans/2026-09-25-verso-v1.md`.
- Téléphone branché en adb : installer, lancer, capturer l'écran uniquement ; ne jamais modifier ses réglages système.

## Vérifier un écran

Comparer le rendu sur le téléphone ou l'émulateur (390 × 844 dp si possible) au PNG de l'écran, en clair et en sombre. 1 px de maquette = 1 dp ; les tailles de texte en px sont des sp.
