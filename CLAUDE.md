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

- **V4 en cours** (collections, décidée le 2026-10-01 ; V3 terminée le 2026-10-01, V2 le 2026-09-30, V1 le 2026-09-27) : ne pas coder « Plus tard » sans décision explicite.
- **Position de lecture** : toujours un locator Readium, jamais des pixels. La logique lecture/navigation et la carte « Revenir » forment une machine à états sans dépendance Android, couverte par des tests unitaires. Les seuils sont des constantes nommées.
- **Textes** : en français, dans `res/values/strings.xml`, repris mot pour mot des maquettes. Espace fine insécable avant `; : ! ?` et `%`, apostrophe typographique, « Environ » plutôt que « ≈ ».
- **Accessibilité** : texte à au moins 7:1 de contraste (et pas plus d'environ 10:1 en thème foncé), commandes de 48 dp minimum, tailles et interlignes en sp, texte à 200 % sans coupure, un intitulé TalkBack sur chaque bouton à icône seule, jamais la couleur seule pour porter une information.
- **Confidentialité** : aucune permission réseau, aucune analytics. Rien ne quitte le téléphone.
- **Lecture** : alignement à gauche, sans justification ni césure, imposé par-dessus le CSS de l'éditeur.
- Tout sur `master`, un commit par étape (`Étape N : …`), poussé et installé sur le téléphone à la fin de chaque étape. Pas de pull request. Toute décision qui change la spec est reportée dans `docs/SPEC.md` dans le même commit.
- Plan d'implémentation V1 : `docs/superpowers/plans/2026-09-25-verso-v1.md`. Design V2 : `docs/superpowers/specs/2026-09-27-verso-v2-design.md`. Design et plan V3 : `docs/superpowers/specs/2026-09-30-verso-v3-design.md`, `docs/superpowers/plans/2026-10-01-verso-v3.md`. Design V4 : `docs/superpowers/specs/2026-10-01-verso-v4-design.md`.
- Téléphone branché en adb : installer, lancer, capturer l'écran uniquement ; ne jamais modifier ses réglages système.
- Un seul agent pilote le téléphone à la fois : avant la première commande adb, prendre le verrou `mkdir .superpowers/phone.lock` (attendre s'il existe), le rendre à la fin (`rm -rf .superpowers/phone.lock`), même en cas d'échec. Les agents lancés en parallèle se limitent aux tests JVM/Robolectric sauf autorisation explicite.

## Rythme de travail

Objectif : moins de 10 minutes par tâche (code, tests, commit). Mesuré le 2026-09-28 : test ciblé 5 s, après une modification du code 30 s, suite complète de l'app 57 s, lint complet 78 s.

- Exécution directe dans la conversation, tâche après tâche : le contexte et le démon Gradle restent chauds. Sous-agents seulement pour de gros morceaux indépendants, deux au plus en même temps.
- Pendant une tâche, seulement les tests concernés : `./gradlew :app:testDebugUnitTest --tests '<classe>'` ou `:core:test`. La suite complète, le lint, les captures Roborazzi et `assembleDebug` une fois par étape, avant le commit d'étape.
- Relecture : une par étape, sur tout le diff de l'étape. Relecture par tâche seulement pour la machine à états et le moteur de lecture.
- Téléphone : une seule passe groupée en fin d'étape.
- Build : `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` depuis Git Bash ; cache de configuration et cache de build activés dans `gradle.properties`.

## Vérifier un écran

Comparer le rendu sur le téléphone ou l'émulateur (390 × 844 dp si possible) au PNG de l'écran, en clair, en sombre et en nuit. 1 px de maquette = 1 dp ; les tailles de texte en px sont des sp.
