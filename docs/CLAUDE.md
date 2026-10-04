# Verso

Lecteur d'ebook Android (EPUB), personnel et publié en portfolio. Promesse : j'ouvre l'app, je suis exactement où j'étais, et le texte est agréable à lire.

## À lire avant de coder

- `SPEC.md` : la référence. Comportement, critères d'acceptation, décisions, étapes de développement (section « Démarrer le développement »). Elle l'emporte sur tout le reste, maquettes comprises.
- `design/README.md` : les maquettes. Un PNG et un HTML par écran dans `design/screens/` et `design/html/`, identifiés comme dans la spec (1.01, 1.14…).
- `design/tokens.json` : couleurs des thèmes, typographie, arrondis, tailles. Le thème Compose en est tiré, aucune couleur en dur ailleurs.
- `design/logo/README.md` : icône de l'app, déjà prête pour `app/src/main/res/`.
- `CHANGEMENTS.md` : ce qui a changé depuis le kit précédent (logo dans l'interface, thèmes Sombre et Nuit), avec la liste de ce qu'il faut reprendre si du code existe déjà.

## Projet

- Kotlin, Jetpack Compose, Material 3, Readium Kotlin toolkit 3.4.0, Room, DataStore, Coil.
- Package `com.maximebier.verso`. minSdk 26, targetSdk et compileSdk 36.
- Une activité, navigation Compose, MVVM.
- Licence Apache 2.0.

## Règles

- **V1 seulement** tant que tous les critères d'acceptation V1 ne sont pas cochés. Ne pas coder la V2, la V3 ou la V4 par anticipation.
- **Position de lecture** : toujours un locator Readium, jamais des pixels. La logique lecture/navigation et la carte « Revenir » forment une machine à états sans dépendance Android, couverte par des tests unitaires. Les seuils sont des constantes nommées.
- **Textes** : en français, dans `res/values/strings.xml`, repris mot pour mot des maquettes. Espace fine insécable avant `; : ! ?` et `%`, apostrophe typographique, « Environ » plutôt que « ≈ ».
- **Accessibilité** : texte à au moins 7:1 de contraste (et pas plus de 10:1 environ en thème foncé, voir la spec), commandes de 48 dp minimum, tailles et interlignes en sp, texte à 200 % sans coupure, un intitulé TalkBack sur chaque bouton à icône seule, jamais la couleur seule pour porter une information.
- **Confidentialité** : aucune analytics. Pas de permission réseau jusqu'à la V2 incluse ; en V3, `INTERNET` sert uniquement à la traduction de la sélection. Rien d'autre ne quitte le téléphone.
- **Lecture** : alignement à gauche, sans justification ni césure, imposé par-dessus le CSS de l'éditeur.
- Une étape de la spec = une branche = une pull request. Toute décision qui change la spec est reportée dans `SPEC.md` dans la même pull request.

## Vérifier un écran

Comparer le rendu sur le téléphone ou l'émulateur (390 × 844 dp si possible) au PNG de l'écran, en clair, en sombre et en nuit. 1 px de maquette = 1 dp ; les tailles de texte en px sont des sp.
