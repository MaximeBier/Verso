# Verso V1 — Référence d’interface pour Jetpack Compose

Document de travail qui remplace les maquettes HTML de la V1 (`docs/design/html/1.*.html`) pour l’implémentation. Sources : les 20 HTML V1 (1.01 à 1.14, variantes sombres comprises), les PNG de `docs/design/screens/`, `tokens.json`, `screens.json` et `docs/SPEC.md` (« V1 — Fonctionnalités », « Interface »). **Si ce document et la spec divergent, la spec prime** ; les écarts connus sont listés en section 4.

Conventions :

- 1 px CSS = 1 dp ; les tailles de texte en px sont des sp. Cadre de référence : 390 × 844 dp.
- Les couleurs sont données par **nom de jeton** de `tokens.json` (`background`, `surface`, `text`, `textSecondary`, `accent`, `onAccent`, `progressTrack`, `outline`, `divider`, `selection`, `onSelection`, `danger`, `inverse`, `onInverse`, `inverseAccent`). Une valeur brute n’apparaît que si aucun jeton ne correspond ; elle est alors marquée ⚠.
- Les hauteurs de ligne sont données en multiplicateur puis en sp (ex. `16 sp / lh 1,5 = 24 sp`). En Compose, utiliser `lineHeight = X.sp` et `LineHeightStyle(alignment = Center, trim = None)` pour coller au rendu CSS.
- Graisses : 380 (lecture en sombre), 400, 500, 600, 700. Atkinson Hyperlegible Next est une police variable : déclarer un `Font(R.font.atkinson_next, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))` par graisse utilisée (minSdk 26 : pas de compatibilité à gérer).
- Les maquettes ne dessinent **aucune barre système**. Tout est à placer en edge-to-edge avec les insets décrits écran par écran (spec, Accessibilité : « La barre du haut, les feuilles et la carte de retour tiennent compte de la barre d’état et de la barre de gestes »).

---

## 1. `res/values/strings.xml` complet

Relevé des caractères réellement présents dans les HTML (analyse des octets) :

| Caractère | Où dans les maquettes | Encodage XML |
| --- | --- | --- |
| U+202F espace fine insécable | avant `%` (« 31 % »), avant `:` `;` `?` (« Votre lecture : », « Supprimer « … » ? », texte de lecture) | ` ` |
| U+00A0 espace insécable (`&nbsp;`) | à l’intérieur des guillemets « … », dans les durées et heures (« 5 h 30 », « 31 min », « 22 h 10 »), avant l’unité (« 1,2 Mo ») | ` ` |
| U+2019 apostrophe typographique ’ | partout (jamais d’apostrophe ASCII dans les libellés) | littérale, aucun échappement |
| U+2192 → | journal (« chap. VI → VIII », « 18 → 25 % »), entouré d’espaces **normales** | littérale |
| U+2013 – | plages horaires du journal (« 21 h 05 – 21 h 36 »), entouré d’espaces **normales** | littérale |
| U+00B7 · | « Partie II, chap. I · 31 % », « En cours · 31 % », « Madame Bovary · 3 parties, 35 chapitres », entouré d’espaces **normales** | littérale |
| U+2026 … | fin de l’extrait (donnée, pas un libellé) | — |

Remarques d’implémentation :

- Dans les chaînes avec paramètres, le `%` littéral s’écrit `%%` (ex. `%1$d %% lu`).
- Les `aria-label` des HTML (« Options pour « Titre » ») utilisent des espaces normales dans les guillemets ; la spec impose l’espace insécable : les chaînes ci-dessous suivent la spec (voir Écarts).
- Les chaînes marquées `<!-- PROPOSÉ -->` n’existent dans aucune maquette : elles couvrent un cas décrit par la spec (DRM, confirmation d’effacement du journal, état « Nouveau »…) ou un état indispensable non dessiné. À valider par Maxime avant fusion.
- `<b>` dans une chaîne formatée : `getString()` perd le style. La chaîne `import_duplicate_body` est en CDATA ; l’afficher avec `AnnotatedString.fromHtml(stringResource(R.string.import_duplicate_body, htmlEncode(fichier), htmlEncode(titre), date))`.

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
  Verso — libellés V1. Source : maquettes docs/design/html/1.*.html + SPEC.md.
    = espace fine insécable (avant ; : ! ? % et ?)
    = espace insécable (dans « … », durées, heures, unités)
  Apostrophe typographique ’ : aucun échappement nécessaire.
-->
<resources>

    <!-- ===================== Application ===================== -->
    <string name="app_name" translatable="false">Verso</string>

    <!-- ===================== Commun ===================== -->
    <!-- Actions -->
    <string name="common_resume">Reprendre</string>
    <string name="common_start">Commencer</string>
    <string name="common_cancel">Annuler</string>
    <string name="common_delete">Supprimer</string>
    <string name="common_close">Fermer</string>
    <string name="common_back">Retour</string>

    <!-- Progression : « 31 % lu », « 64 % » -->
    <string name="common_percent_read">%1$d %% lu</string>
    <string name="common_percent">%1$d %%</string>
    <!-- « Environ 5 h 30 restantes » ; %1$s = durée formatée (common_duration_*) -->
    <string name="common_time_remaining">Environ %1$s restantes</string>
    <!-- PROPOSÉ : moins d’une minute restante -->
    <string name="common_time_remaining_less_than_minute">Moins d’une minute restante</string>

    <!-- Durées : « 5 h 30 », « 5 h », « 45 min » -->
    <string name="common_duration_hours_minutes">%1$d h %2$02d</string>
    <string name="common_duration_hours">%1$d h</string>
    <string name="common_duration_minutes">%1$d min</string>
    <!-- Heure d’horloge 24 h : « 22 h 10 », « 7 h 42 » (heure sans zéro initial, minutes sur 2 chiffres) -->
    <string name="common_clock_time">%1$d h %2$02d</string>

    <!-- Emplacement dans le livre -->
    <!-- Forme longue : « Deuxième partie, chapitre I » (partie + titre de chapitre, initiale du chapitre en minuscule) -->
    <string name="common_location_long">%1$s, %2$s</string>
    <!-- Forme courte : « Partie II, chap. I » -->
    <string name="common_location_short">Partie %1$s, chap. %2$s</string>
    <!-- PROPOSÉ : forme courte sans partie (livre sans parties) : « Chap. IV » -->
    <string name="common_location_short_no_part">Chap. %1$s</string>
    <!-- « Partie II, chap. I · 31 % » -->
    <string name="common_location_with_percent">%1$s · %2$d %%</string>

    <!-- Extrait entre guillemets : « « Yonville-l’Abbaye… » » -->
    <string name="common_quoted">« %1$s »</string>

    <!-- Motifs de date (java.time, Locale.FRENCH) -->
    <string name="common_date_pattern_full" translatable="false">d MMMM yyyy</string>

    <!-- ===================== Bibliothèque (1.01 – 1.03) ===================== -->
    <string name="library_settings">Paramètres</string>
    <string name="library_import">Importer</string>
    <string name="library_title">Bibliothèque</string>
    <plurals name="library_book_count">
        <item quantity="one">%1$d livre</item>
        <item quantity="many">%1$d livres</item>
        <item quantity="other">%1$d livres</item>
    </plurals>

    <!-- Bibliothèque vide (1.01) -->
    <string name="library_empty_title">Votre bibliothèque est vide</string>
    <string name="library_empty_body">Importez un livre au format EPUB depuis votre téléphone, Google Drive ou Nextcloud. Verso le garde et retient toujours votre page.</string>
    <string name="library_empty_import">Importer un EPUB</string>
    <string name="library_empty_open_with_hint">Depuis une autre application, vous pouvez aussi toucher un fichier EPUB et choisir « Ouvrir avec Verso ».</string>

    <!-- Affichage liste / grille -->
    <string name="library_view_mode_group">Affichage</string>
    <string name="library_view_list">Liste</string>
    <string name="library_view_grid">Grille</string>

    <!-- Tri -->
    <string name="library_sort_group">Trier par</string>
    <string name="library_sort_recent">Récents</string>
    <string name="library_sort_title">Titre</string>
    <string name="library_sort_author">Auteur</string>

    <!-- États d’un livre -->
    <string name="library_state_finished">Terminé</string>
    <!-- Spec : état « Nouveau » (absent des maquettes) -->
    <string name="library_state_new">Nouveau</string>

    <!-- Bouton ⋮ et son menu (1.02b) -->
    <string name="library_book_options">Options pour « %1$s »</string>
    <string name="library_menu_details">Détails du livre</string>
    <string name="library_menu_delete">Supprimer</string>

    <!-- Carte « Reprendre » -->
    <string name="resume_card_label">Reprendre</string>
    <string name="resume_card_button">Reprendre</string>
    <!-- PROPOSÉ : intitulé TalkBack de la carte entière (un seul bouton) -->
    <string name="resume_card_content_description">Reprendre « %1$s », %2$d %% lu</string>

    <!-- ===================== Import (1.04 – 1.06) ===================== -->
    <!-- Snackbar -->
    <string name="import_success">« %1$s » a été ajouté.</string>
    <string name="import_success_action">Commencer</string>

    <!-- Doublon (1.05) : %1$s = nom du fichier, %2$s = titre du livre existant (gras), %3$s = date d’import -->
    <string name="import_duplicate_title">Ce livre est déjà dans votre bibliothèque</string>
    <string name="import_duplicate_body"><![CDATA[« %1$s » est identique à <b>%2$s</b>, importé le %3$s.]]></string>
    <string name="import_duplicate_position_kept">Votre position de lecture est conservée dans les deux cas.</string>
    <string name="import_duplicate_replace">Remplacer</string>
    <string name="import_duplicate_ignore">Ignorer</string>

    <!-- Fichier refusé (1.06) -->
    <string name="import_error_title">Impossible d’importer ce fichier</string>
    <string name="import_error_not_epub">« %1$s » n’est pas un livre EPUB. Verso ne lit que les fichiers EPUB pour l’instant.</string>
    <!-- PROPOSÉ : variante DRM décrite par la spec (« protégé par un DRM Adobe ou LCP ») -->
    <string name="import_error_drm">« %1$s » est protégé par un DRM (Adobe ou LCP). Verso ne lit que les livres sans DRM.</string>
    <!-- PROPOSÉ : EPUB endommagé ou illisible (cas réel non couvert par la spec) -->
    <string name="import_error_unreadable">« %1$s » est un EPUB endommagé ou illisible.</string>
    <string name="import_error_nothing_added">Rien n’a été ajouté à votre bibliothèque.</string>
    <string name="import_error_ok">Compris</string>

    <!-- ===================== Détails du livre (1.07 – 1.08) ===================== -->
    <string name="details_title">Détails du livre</string>
    <string name="details_field_title">Titre</string>
    <string name="details_field_author">Auteur</string>
    <string name="details_autosave_hint">Les modifications sont enregistrées automatiquement.</string>
    <string name="details_imported_on">Importé le</string>
    <string name="details_size">Taille</string>
    <string name="details_format">Format</string>
    <string name="details_original_file">Fichier d’origine</string>
    <string name="details_format_epub" translatable="false">EPUB</string>
    <!-- « 1,2 Mo » ; %1$s = nombre formaté avec virgule décimale (NumberFormat Locale.FRENCH) -->
    <string name="details_size_mb">%1$s Mo</string>
    <string name="details_size_kb">%1$s ko</string>
    <string name="details_delete_book">Supprimer le livre</string>

    <!-- Confirmation de suppression (1.08) -->
    <string name="details_delete_dialog_title">Supprimer « %1$s » ?</string>
    <string name="details_delete_dialog_body">Le livre et votre position de lecture seront effacés de Verso. Le fichier d’origine, sur votre téléphone ou votre Drive, n’est pas touché.</string>
    <string name="details_delete_dialog_cancel">Annuler</string>
    <string name="details_delete_dialog_confirm">Supprimer</string>

    <!-- ===================== Paramètres (1.09) ===================== -->
    <string name="settings_title">Paramètres</string>
    <string name="settings_section_startup">Au démarrage</string>
    <string name="settings_reopen_last_book">Rouvrir le dernier livre</string>
    <string name="settings_reopen_last_book_summary">Si vous l’avez lu il y a moins de 24 heures</string>
    <string name="settings_section_display">Affichage</string>
    <string name="settings_display_body">Verso suit le thème clair ou sombre de votre téléphone et garde l’écran allumé pendant la lecture.</string>
    <string name="settings_section_privacy">Confidentialité</string>
    <string name="settings_privacy_body">Verso n’utilise pas Internet. Vos livres, vos positions, votre journal et vos réglages restent sur ce téléphone.</string>
    <string name="settings_clear_journal">Effacer le journal de lecture</string>
    <string name="settings_clear_journal_summary">Vos positions de lecture sont conservées.</string>
    <string name="settings_section_about">À propos</string>
    <string name="settings_version">Version</string>
    <string name="settings_source_code">Code source</string>
    <!-- À remplacer quand le dépôt existe (spec, Questions ouvertes) -->
    <string name="settings_source_code_url_label" translatable="false">github.com/[votre-compte]/verso</string>
    <string name="settings_source_code_url" translatable="false">https://github.com/[votre-compte]/verso</string>
    <string name="settings_licenses">Licences open source</string>

    <!-- PROPOSÉ : confirmation « Effacer le journal de lecture » (spec : « avec confirmation », non dessinée) -->
    <string name="settings_clear_journal_dialog_title">Effacer le journal de lecture ?</string>
    <string name="settings_clear_journal_dialog_body">Toutes vos sessions de lecture seront effacées. Vos positions de lecture sont conservées.</string>
    <string name="settings_clear_journal_dialog_cancel">Annuler</string>
    <string name="settings_clear_journal_dialog_confirm">Effacer</string>

    <!-- ===================== Lecture (1.10 – 1.11) ===================== -->
    <string name="reader_back_to_library">Retour à la bibliothèque</string>
    <string name="reader_toc">Sommaire</string>
    <string name="reader_journal">Journal</string>

    <!-- ===================== Sommaire (1.12) ===================== -->
    <string name="toc_title">Sommaire</string>
    <string name="toc_chapters_group">Chapitres</string>
    <!-- « Madame Bovary · 3 parties, 35 chapitres » -->
    <string name="toc_subtitle">%1$s · %2$s</string>
    <string name="toc_subtitle_counts">%1$s, %2$s</string>
    <plurals name="toc_part_count">
        <item quantity="one">%1$d partie</item>
        <item quantity="many">%1$d parties</item>
        <item quantity="other">%1$d parties</item>
    </plurals>
    <plurals name="toc_chapter_count">
        <item quantity="one">%1$d chapitre</item>
        <item quantity="many">%1$d chapitres</item>
        <item quantity="other">%1$d chapitres</item>
    </plurals>
    <string name="toc_chapter_read">Lu</string>
    <!-- « En cours · 31 % » -->
    <string name="toc_chapter_current">En cours · %1$d %%</string>

    <!-- ===================== Journal de lecture (1.13) ===================== -->
    <string name="journal_title">Journal de lecture</string>
    <string name="journal_subtitle">Touchez une session pour reprendre là où elle s’est arrêtée.</string>
    <string name="journal_today">Aujourd’hui</string>
    <string name="journal_yesterday">Hier</string>
    <!-- En-têtes de jour au-delà d’hier : « Lundi 21 septembre » (Locale.FRENCH, 1re lettre en capitale) -->
    <string name="journal_day_pattern" translatable="false">EEEE d MMMM</string>
    <!-- Jour d’une autre année : « Lundi 21 septembre 2025 » -->
    <string name="journal_day_pattern_other_year" translatable="false">EEEE d MMMM yyyy</string>
    <!-- « 21 h 05 – 21 h 36 » ; %1$s et %2$s = common_clock_time -->
    <string name="journal_time_range">%1$s – %2$s</string>
    <!-- Session en cours : « Depuis 22 h 10 » -->
    <string name="journal_since">Depuis %1$s</string>
    <!-- « Partie I, chap. VIII → Partie II, chap. I » ou « Partie I, chap. VI → VIII » -->
    <string name="journal_passage">%1$s → %2$s</string>
    <!-- « 18 → 25 % » -->
    <string name="journal_percent_range">%1$d → %2$d %%</string>
    <string name="journal_in_progress">En cours</string>
    <string name="journal_resume_here">Reprendre ici</string>
    <!-- PROPOSÉ : journal vide -->
    <string name="journal_empty">Aucune session pour l’instant. Vos sessions apparaîtront ici dès que vous lirez.</string>

    <!-- ===================== Carte de retour (1.14) ===================== -->
    <string name="return_card_title">Vous avez quitté votre lecture</string>
    <!-- « Votre lecture : Partie II, chap. I · 31 % » -->
    <string name="return_card_position">Votre lecture : %1$s · %2$d %%</string>
    <string name="return_card_stay">Rester ici</string>
    <string name="return_card_go_back">Revenir</string>

</resources>
```

Formatage des valeurs (à mettre dans une classe `Formats.kt` testée unitairement) :

| Valeur | Règle | Exemple |
| --- | --- | --- |
| Durée ≥ 1 h avec minutes | `common_duration_hours_minutes`, minutes sur 2 chiffres | « 5 h 30 », « 1 h 05 » |
| Durée en heures pleines | `common_duration_hours` | « 5 h » |
| Durée < 1 h | `common_duration_minutes` | « 45 min », « 31 min » |
| Heure d’horloge | `common_clock_time`, 24 h, heure sans zéro initial | « 7 h 42 », « 22 h 10 » |
| Date d’import | `DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)` | « 12 septembre 2026 » |
| En-tête de jour du journal | « Aujourd’hui », « Hier », sinon `EEEE d MMMM` (+ `yyyy` si autre année), première lettre en capitale | « Lundi 21 septembre » |
| Taille de fichier | < 1 Mo → ko entier, sinon Mo à 1 décimale, virgule décimale | « 1,2 Mo », « 850 ko » |
| Pourcentage | entier arrondi vers le bas (jamais « 100 % » tant que non terminé : proposé) | « 31 % » |

---

## 2. Écrans

Structure commune des écrans « bibliothèque » (1.01–1.06, 1.02b) : `Scaffold` fond `background`, barre du haut de la bibliothèque (§3.1), puis contenu défilant. Tous les chiffres ci-dessous sont en dp / sp.

### 1.01 Premier lancement (bibliothèque vide)

Arbre :

```
Column(fillMaxSize, bg = background)
├─ LibraryTopBar (§3.1) — SANS le bouton « Importer » (spec : masqué tant que la bibliothèque est vide)
│    └─ seul le bouton icône Paramètres (48×48, icône settings 24, couleur text)
└─ Column(weight 1, verticalArrangement = Center, padding start/end 32, bottom 88, spacedBy 20)
   ├─ Icône book-open 56×56, trait 1,5 (⚠ spec : trait 2), couleur textSecondary, alignée à gauche
   ├─ Titre « Votre bibliothèque est vide » : 28 sp, 700, lh 1,25 (35 sp), text
   ├─ Paragraphe library_empty_body : 18 sp, 400, lh 1,55 (27,9 sp), textSecondary
   ├─ PrimaryButton large (§3.8) : fillMaxWidth, hauteur 56, rayon 28 (pilule), fond accent,
   │    texte onAccent 16 sp 700, icône plus 20 à gauche, gap 8, padding start 18 / end 22
   │    « Importer un EPUB »  → ouvre le SAF (ACTION_OPEN_DOCUMENT, application/epub+zip)
   └─ Paragraphe library_empty_open_with_hint : 16 sp, 400, lh 1,55 (24,8 sp), textSecondary
```

- Accent : uniquement le bouton « Importer un EPUB ».
- Le bloc central est centré verticalement dans l’espace sous la barre, avec 88 dp de marge basse (le centre optique est donc décalé vers le haut). Rendre le Column défilable (`verticalScroll`) pour le texte à 200 %.
- Insets : barre du haut sous la barre d’état ; marge basse + `navigationBars`.

### 1.02 Bibliothèque — liste (clair)

Arbre (le tout dans un `LazyColumn` sous la barre du haut fixe ; tous les éléments défilent) :

```
LibraryTopBar (§3.1) avec [Paramètres] [Importer]
LazyColumn
├─ item: ResumeCard complète (§3.3), conteneur padding top 8, start/end 16
├─ item: LibrarySectionHeader
│    Row(padding top 20, end 16, bottom 4, start 24; SpaceBetween; gap 8; centerVertically)
│    ├─ Row(alignByBaseline, gap 10)
│    │   ├─ « Bibliothèque » 22 sp 700 text
│    │   └─ « 7 livres » (plurals library_book_count) 16 sp 400 textSecondary
│    └─ ViewModeToggle (§3.6) [Liste ✓sélectionné | Grille]
├─ item: SortSegmentedButton (§3.5), conteneur padding start/end 16, bottom 8
│    [✓ Récents | Titre | Auteur], hauteur 48, pleine largeur
└─ items: BookRow (§3.4) × n, sans séparateurs
```

- Exemple de contenu : 5 livres visibles (64 %, 47 %, 12 %, « Terminé », 8 %).
- Accent : étiquette « Reprendre », barre 6 dp et bouton « Reprendre » de la carte. Tout le reste à l’encre (barres 4 dp en `text`).
- Insets : `contentPadding(bottom = navigationBars + 16)` du LazyColumn (proposé ; la maquette coupe la liste au bord).

### 1.02 Bibliothèque — sombre

Structure identique à 1.02 ; seuls changent les jetons (thème `dark`). Relevé des différences effectives :

| Élément | Clair | Sombre |
| --- | --- | --- |
| Fond / texte | background / text | background / text (jetons sombres) |
| Contour du bouton « Importer », des segmentés | outline | outline |
| Carte Reprendre | surface | surface |
| Étiquette « Reprendre », barre 6 dp, bouton | accent / onAccent | accent `#E8C48E` / onAccent `#1B1510` |
| Segment sélectionné | selection / onSelection | selection / onSelection |
| **Remplissage des barres 4 dp** | **text** `#1F1B16` | **textSecondary** `#CFC7BB` ⚠ (pas `text`) |
| Piste des barres | progressTrack | progressTrack |
| Bouton ⋮, auteur, % | textSecondary | textSecondary |
| Vignettes générées | palette claire, monogramme `background` | palette sombre, monogramme `text` (voir §3.13) |

### 1.02b Menu d’un livre (⋮)

- Même écran que 1.02, plus un `DropdownMenu` (§3.9) ancré au bouton ⋮ du livre, aligné à droite (bord droit à 16 dp du bord de l’écran), largeur 232.
- Pas de voile (scrim) derrière le menu.
- Items : « Détails du livre » (icône info 20, couleur text) → écran 1.07 ; « Supprimer » (icône trash 20 **et** texte en `danger`) → ouvre la confirmation 1.08 (même dialogue que dans la fiche ; la spec exige une confirmation pour toute suppression).
- Le bouton ⋮ a pour intitulé `library_book_options` (« Options pour « Titre » »).

### 1.03 Bibliothèque — grille

```
LibraryTopBar (§3.1)
LazyVerticalGrid(columns = 2) — ou LazyColumn avec lignes de 2 cellules
├─ item(span 2): ResumeCard **compacte** (§3.3 variante B) ⚠ voir Écarts : la spec demande la carte complète
├─ item(span 2): LibrarySectionHeader (idem 1.02), toggle [Liste | ✓Grille]
├─ item(span 2): SortSegmentedButton (idem 1.02)
└─ items: BookGridCell (§3.4b)
     contentPadding: top 4, start/end 20 ; horizontalArrangement spacedBy 16 ; verticalArrangement spacedBy 20
     → largeur de cellule sur 390 dp : (390 − 40 − 16) / 2 = 167 dp ; couverture 167 × 250,5 (ratio 2:3)
```

- Toujours 2 colonnes (spec), quelle que soit la largeur : `GridCells.Fixed(2)`.
- Livre terminé en grille : la ligne d’état affiche la coche + « Terminé » au lieu de la barre (même composant que la liste).

### 1.04 Import réussi (snackbar)

- Écran 1.02 inchangé + `VersoSnackbar` (§3.10) : « « Le Rouge et le Noir » a été ajouté. » avec l’action « Commencer » (ouvre le livre au début).
- Position : 16 dp des bords gauche/droit, 24 dp du bas (+ inset `navigationBars`). Largeur 358 sur 390.
- Le message peut passer sur 2 lignes (titre long) : l’action reste centrée verticalement à droite.
- Durée : `SnackbarDuration.Long` proposé (non précisé). Doit respecter « Supprimer les animations ».

### 1.05 Livre déjà importé (dialogue)

`VersoDialog` (§3.11) par-dessus 1.02 :

```
Scrim text à 42 % (rgba(31,27,22,0.42))
Dialog (surface, rayon 28, marges latérales 24 → largeur 342, centré verticalement)
├─ Titre « Ce livre est déjà dans votre bibliothèque » 22 sp 700 lh 1,3, aligné à gauche
├─ Corps (Column gap 10, 16 sp lh 1,5, textSecondary)
│   ├─ « « germinal.epub » est identique à **Germinal**, importé le 3 septembre 2026. »
│   │     (titre du livre existant en 700, couleur héritée textSecondary)
│   └─ « Votre position de lecture est conservée dans les deux cas. »
└─ Boutons (FlowRow, aligné à droite, gap 8, marge haute 4)
    ├─ TextButton « Remplacer » (text, 700, padding horizontal 14)
    └─ PrimaryButton « Ignorer » (accent / onAccent, 700, padding horizontal 24)
```

- `role = alertdialog`, non annulable par tap extérieur (proposé : le tap extérieur = « Ignorer », comportement sûr).

### 1.06 Fichier refusé (dialogue)

Comme 1.05, avec une icône et un titre centrés :

```
Dialog
├─ Icône triangle d’alerte 28×28, trait 2, textSecondary, centrée
├─ Titre « Impossible d’importer ce fichier » 22 sp 700 lh 1,3, **centré**
├─ Corps (gap 10, 16 sp lh 1,5, textSecondary, aligné à gauche)
│   ├─ Cas : import_error_not_epub | import_error_drm (spec) | import_error_unreadable (proposé)
│   └─ « Rien n’a été ajouté à votre bibliothèque. »
└─ PrimaryButton « Compris » seul, aligné à droite
```

- La variante DRM (spec) utilise exactement la même mise en page ; seul le premier paragraphe change.

### 1.07 Détails du livre

```
DetailTopBar (§3.2) : [← Retour] « Détails du livre »
Column(verticalScroll, padding top 8, start/end 24, bottom 24, spacedBy 22)
├─ Row(gap 20, verticalAlignment = Bottom)
│   ├─ Couverture 96×144, rayon 4 (image EPUB ou vignette générée, monogramme 22 sp 600)
│   └─ Column(weight 1, gap 12)
│       ├─ Texte 16 sp lh 1,45 textSecondary, 2 lignes : « 31 % lu » \n « Environ 5 h 30 restantes »
│       └─ PrimaryButton pleine largeur, hauteur 48 : icône book-open 20 + « Reprendre »
│            (« Commencer » si 0 %)
├─ Champ « Titre » (§3.12) : label 14 sp 700 textSecondary, gap 6, champ 56 de haut
├─ Champ « Auteur » (§3.12) + aide « Les modifications sont enregistrées automatiquement. »
│     14 sp lh 1,4 textSecondary, sous le champ (gap 6)
├─ Liste de métadonnées (§3.14), 4 lignes séparées par un trait divider 1 dp en bas de chaque ligne :
│     Importé le · 12 septembre 2026 | Taille · 1,2 Mo | Format · EPUB | Fichier d’origine · madame-bovary.epub
└─ OutlinedDangerButton pleine largeur, hauteur 48 : icône trash 20 + « Supprimer le livre »
      bordure 1 danger, texte/icône danger, 16 sp 600
```

- Enregistrement automatique : sauvegarder au changement avec un debounce (≈ 500 ms) et à la perte de focus. Titre vide : proposé de revenir à la valeur précédente.
- Accent : bouton « Reprendre » et bordure du champ actif (spec : « au champ actif »).
- Le nom de fichier long doit passer à la ligne (texte à 200 %) ; la valeur est alignée à droite, poids flexible.
- Insets : barre sous la barre d’état ; bas du défilement + `navigationBars` ; `imePadding()` pour les champs.

### 1.08 Supprimer un livre (dialogue)

- Par-dessus 1.07 (ou 1.02 depuis le menu ⋮).
- Titre (gauche) : « Supprimer « Madame Bovary » ? » (espace fine avant `?`).
- Corps : details_delete_dialog_body.
- Boutons : TextButton « Annuler » (text) + DangerButton « Supprimer » (fond `danger`, texte `#FFFFFF` ⚠ = onAccent clair ; aucun jeton `onDanger`, voir Écarts), padding horizontal 24, 700.
- Après suppression : retour à la bibliothèque (proposé : snackbar non prévue par la spec, ne pas en ajouter).

### 1.09 Paramètres

```
DetailTopBar (§3.2) : [← Retour] « Paramètres »
Column(verticalScroll)
├─ Section « Au démarrage » (§3.15, séparateur bas)
│   └─ SwitchRow : « Rouvrir le dernier livre » / « Si vous l’avez lu il y a moins de 24 heures », interrupteur activé
├─ Section « Affichage » (séparateur bas)
│   └─ Paragraphe settings_display_body (padding 4 24 12, 16 sp lh 1,5 textSecondary) — non cliquable
├─ Section « Confidentialité » (séparateur bas)
│   ├─ Paragraphe settings_privacy_body
│   └─ ActionRow : « Effacer le journal de lecture » / « Vos positions de lecture sont conservées. »,
│        icône trash 20 en danger à droite → dialogue de confirmation (PROPOSÉ, §3.11 variante danger)
└─ Section « À propos » (pas de séparateur)
    ├─ ValueRow : « Version » … « 1.0.0 » (BuildConfig.VERSION_NAME), 16 sp textSecondary, + 12 dp d’espace à droite
    ├─ ActionRow : « Code source » / « github.com/[votre-compte]/verso », icône external-link 20 textSecondary
    │     → Intent ACTION_VIEW (le navigateur, pas l’app : aucune permission réseau)
    └─ ActionRow : « Licences open source », icône chevron-right 20 textSecondary → écran des licences (non dessiné)
```

- Accent : uniquement l’interrupteur (piste accent, coche accent sur pastille onAccent).

### 1.10 Lecture (clair)

Le texte est rendu par **Readium** (navigateur EPUB en Fragment), pas par Compose. Réglages à imposer via `EpubPreferences` :

| Réglage | Valeur | Remarque |
| --- | --- | --- |
| Police | Atkinson Hyperlegible Next (TTF variable embarqué, déclaré comme police Readium) | |
| Taille | 19 sp | respecter l’échelle de police système |
| Interligne | 1,6 | |
| Espacement de paragraphe | 0,5 × interligne (maquette : 15 dp de marge basse pour une ligne de 30,4) | |
| Marges latérales | 24 dp | |
| Alignement | début (gauche), `textAlign = START` | |
| Césure | désactivée (`hyphens = false`) | |
| Justification | désactivée (imposée par-dessus le CSS de l’éditeur, `publisherStyles = false`) | |
| Défilement | scroll vertical continu (`scroll = true`) | |

Rendu de l’en-tête de chapitre tel que dessiné (dépend du CSS de l’EPUB ; à approcher par une feuille de style injectée si nécessaire) :

```
Column(padding top 56 (⚠ inclut la zone de barre d’état), start/end 24)
├─ En-tête centré, gap 4, marge basse 32
│   ├─ « Deuxième partie » 14 sp 400 textSecondary
│   └─ « I » 28 sp **400** lh 1,2 text
└─ Paragraphes 19 sp lh 1,6, marge basse 15, alignés à gauche, sans césure
```

- Aucune barre visible (barre de lecture masquée). Tap au centre : affiche/masque la barre (1.11).
- Fond `background`, texte `text`. Écran maintenu allumé (`FLAG_KEEP_SCREEN_ON`), luminosité jamais modifiée.
- Insets : le texte ne doit pas passer sous la barre d’état ni la barre de gestes (padding haut = inset `statusBars`, bas = inset `navigationBars`). La spec ne demande pas de mode immersif.

### 1.10 Lecture — sombre

Identique, avec les jetons sombres, et pour les paragraphes : **graisse 380** (au lieu de 400) et **espacement des lettres +0,01 em** (`tokens.typography.darkThemeAdjust`). L’en-tête de chapitre n’est pas allégé dans la maquette (seuls les `<p>` le sont). Readium : `fontWeight = 0.95` (380/400) et `letterSpacing` ≈ 0,01 — vérifier l’unité exacte de `letterSpacing` dans Readium 3.4.

### 1.11 Lecture, barre affichée (clair)

Deux barres superposées au texte (le texte ne bouge pas) :

```
Barre du haut (ReaderTopBar) — alignée en haut, pleine largeur
  fond surface, trait bas 1 dp divider, hauteur 72 (+ inset statusBars au-dessus), padding start 4 / end 8, gap 4
  ├─ IconButton 48 « Retour à la bibliothèque » (arrow-left 24, text)
  └─ Column(weight 1, gap 1)
      ├─ Titre du livre 18 sp 700 lh 1,25, 1 ligne, ellipse
      └─ Chapitre courant (forme longue) 14 sp lh 1,3 textSecondary, 1 ligne, ellipse

Barre du bas (ReaderBottomBar) — alignée en bas, pleine largeur
  fond surface, trait haut 1 dp divider, padding top 16, start/end 20, bottom 24 (+ inset navigationBars), Column gap 12
  ├─ Row(SpaceBetween, alignByBaseline, gap 12)
  │   ├─ « 31 % lu » 16 sp 700 text
  │   └─ « Environ 5 h 30 restantes » 14 sp textSecondary
  ├─ ProgressBar 6 dp accent (§3.7)
  └─ Row de 2 boutons égaux (weight 1), gap 12, marge haute 4
      ├─ OutlinedButton « Sommaire » (icône list-bullets 20) → feuille 1.12
      └─ OutlinedButton « Journal » (icône history 20) → feuille 1.13
```

- Hauteur totale de la barre du bas sur la maquette : 16 + ~21 + 12 + 6 + 12 + 4 + 48 + 24 ≈ 143 dp (+ inset).
- Accent : uniquement la barre de progression 6 dp.
- Animation d’apparition : fondu/glissement court, désactivé si « Supprimer les animations ».

### 1.11 Barre affichée — sombre

Jetons sombres ; aucune autre différence (fond des barres surface `#22201D`, trait divider `#3A3530`, barre accent `#E8C48E`, contours outline `#8E857A`). Le texte de lecture garde l’allègement 380 / +0,01 em.

### 1.12 Sommaire (clair)

`VersoBottomSheet` (§3.16) par-dessus la lecture :

```
Scrim : text à 42 % (clair) / noir à 58 % (sombre)
Sheet : haut à 88 dp du haut de l’écran (hauteur 756 sur 844), surface, rayons haut 28
├─ Poignée 32×4, rayon 2, outline, marge 6 haut / 4 bas
├─ SheetHeader (padding top 4, end 12, bottom 8, start 24)
│   ├─ Column(padding top 10) : « Sommaire » 22 sp 700 lh 1,3 ;
│   │     sous-titre « Madame Bovary · 3 parties, 35 chapitres » 14 sp lh 1,4 textSecondary, marge haute 2
│   └─ IconButton 48 « Fermer » (icône x 24, text)
└─ LazyColumn (contentPadding bottom 8 ; padding bas de la feuille 24 + navigationBars)
    ├─ En-tête de partie : « Première partie » 14 sp 700 textSecondary, padding 18 24 6
    ├─ Ligne chapitre lu : min 52 de haut, padding horizontal 24, SpaceBetween, gap 12
    │     « Chapitre VII » 16 sp 400 text | [coche 18 (trait 2,4) + « Lu » 14 sp textSecondary, gap 6]
    ├─ Ligne chapitre courant : conteneur padding horizontal 12 ; ligne padding horizontal 12, rayon 12,
    │     fond selection, texte onSelection, **700** ; à droite « En cours · 31 % » 14 sp (700 hérité)
    └─ Ligne chapitre non lu : comme « lu » sans badge
```

- À l’ouverture, la liste défile pour montrer le chapitre en cours (dans la maquette, il est précédé de 3 chapitres et d’en-têtes).
- Tap sur un chapitre = saut direct (navigation : ne touche pas la position de lecture, fait apparaître la carte de retour si > 1 écran).
- Chapitres sans partie : pas d’en-tête. Chapitres imbriqués sur plus de 2 niveaux : non dessiné (proposé : indentation de 16 dp par niveau).
- Sémantique : liste `Chapitres` ; chapitre courant `selected = true` / état « En cours ».

### 1.12 Sommaire — sombre

Scrim `rgba(0,0,0,0.58)` ⚠ ; feuille surface `#22201D` ; poignée outline `#8E857A` ; chapitre courant selection `#463727` / onSelection `#F2E3D0` ; libellés secondaires textSecondary `#CFC7BB`.

### 1.13 Journal de lecture (clair)

Même `VersoBottomSheet` que 1.12 :

```
SheetHeader : « Journal de lecture » / « Touchez une session pour reprendre là où elle s’est arrêtée. » + [Fermer]
LazyColumn(contentPadding horizontal 12, bottom 8)
├─ En-tête de jour : « Aujourd’hui » 14 sp 700 textSecondary, padding top 14, horizontal 12, bottom 4
├─ Session en cours (non cliquable, aria-current) : fond **background**, rayon 12, padding 12, Column gap 4
│   ├─ Row(SpaceBetween, gap 12) 16 sp 700 : « Depuis 22 h 10 » | « 31 min »
│   ├─ « Partie I, chap. VIII → Partie II, chap. I » 16 sp 400 lh 1,4
│   └─ Row(SpaceBetween, center, gap 12) 14 sp textSecondary : « 25 → 31 % » | « En cours » 14 sp 700
├─ En-tête « Hier »
├─ Session terminée (bouton, rayon 12, padding 12, fond transparent, ripple rayon 12)
│   ├─ « 21 h 05 – 21 h 36 » | « 31 min » (16 sp 700)
│   ├─ « Partie I, chap. VI → VIII » (16 sp lh 1,4)
│   └─ « 18 → 25 % » | [« Reprendre ici » 14 sp 700 + chevron-right 18, gap 2] (textSecondary)
├─ En-tête « Lundi 21 septembre » : 2 sessions
└─ En-tête « Samedi 19 septembre » : 1 session
```

- Même partie au début et à la fin : « Partie I, chap. VI → VIII » (la partie n’est pas répétée).
- Tap sur une session terminée = saut à la fin de la session (navigation explicite).
- Aucune ligne de séparation entre sessions ; l’espacement vient des paddings.
- Accent : aucun.

### 1.13 Journal — sombre

Scrim noir 58 % ; feuille surface `#22201D` ; carte de la session en cours en **background** `#171513` (plus sombre que la feuille) ; secondaires en `#CFC7BB`.

### 1.14 Retour à votre lecture (carte « Revenir »)

La lecture (ici au chapitre II) avec la `ReturnCard` (§3.17) en bas :

```
ReturnCard : 16 dp des bords, 24 dp du bas (+ navigationBars), fond inverse, texte onInverse, rayon 20
├─ Row(top, gap 12, padding end 8)
│   ├─ Icône bookmark 22, inverseAccent, padding top 2
│   └─ Column(gap 2)
│       ├─ « Vous avez quitté votre lecture » 18 sp 700 lh 1,3
│       └─ « Votre lecture : Partie II, chap. I · 31 % » 14 sp lh 1,4
└─ Row(End, gap 8)
    ├─ TextButton « Rester ici » : 48, padding horizontal 16, 16 sp 700, onInverse
    └─ Bouton plein « Revenir » : 48, padding start 16 / end 20, gap 8, fond inverseAccent,
         texte `inverse` (couleur du fond de la carte), icône undo 20, 16 sp 700
```

- Pas de minuterie, pas de geste de fermeture (swipe) : la carte reste jusqu’au choix ou jusqu’à l’acceptation automatique (~25 s de lecture sur place).
- La barre de lecture peut être affichée en même temps : la carte se place alors au-dessus de la barre du bas (proposé ; non dessiné).
- `role = status` → `liveRegion = Polite` à l’apparition.
- Sombre (non dessiné, par jetons) : fond `#E8E2D8`, texte `#1F1B16`, icône et bouton « Revenir » `#7A3021`, texte du bouton `#E8E2D8`.

---

## 3. Composants partagés

### 3.1 LibraryTopBar

- Hauteur 72 (+ inset `statusBars` au-dessus), padding top 12, end 12, bottom 4, start 24 ; `Row(SpaceBetween, centerVertically)`.
- Gauche : « Verso » 28 sp 700, letterSpacing −0,01 em (−0,28 sp), text. Texte, pas le logo (spec).
- Droite : `Row(gap 4)` : IconButton Paramètres (48×48, forme cercle, icône settings 24, text, intitulé « Paramètres ») puis `OutlinedButton` « Importer » (§3.8) — ce dernier est absent si la bibliothèque est vide.
- Fixe (ne défile pas) ; fond background, pas d’ombre, pas de trait.

### 3.2 DetailTopBar (Détails, Paramètres)

- Hauteur 64 (+ `statusBars`), padding start 4, end 8, gap 4.
- IconButton 48 (arrow-left 24, text, intitulé « Retour ») + titre 22 sp 700 lh 1,25, 1 ligne, ellipse.
- Fond background, sans trait.

### 3.3 ResumeCard

**Variante A — complète (1.02 ; à utiliser partout selon la spec)**

- Carte = **un seul bouton** (clic → ouvrir le livre à la position de lecture). Fond surface, rayon 20, padding 16, Column gap 14.
- Ligne 1 : Row(gap 16, alignement haut)
  - Couverture 64×96 rayon 4 (monogramme 2 lettres 18 sp 600).
  - Column(weight 1, gap 2, padding top 2) : « Reprendre » 14 sp 700 **accent** ; titre 22 sp 700 lh 1,25 (marge haute 2 supplémentaire) ; auteur 16 sp lh 1,4 textSecondary ; chapitre (forme longue) 16 sp lh 1,4 textSecondary.
- Extrait : `common_quoted`, 16 sp lh 1,5 text, **2 lignes max**, ellipse.
- Barre 6 dp accent sur progressTrack (§3.7), pleine largeur.
- Pied : Row(SpaceBetween, center, gap 12)
  - Column(gap 2), 14 sp lh 1,35 : « 31 % lu » 700 text ; « Environ 5 h 30 restantes » textSecondary.
  - Pseudo-bouton pilule (non focalisable séparément) : hauteur 48, padding start 18 / end 22 ⚠ (la flèche est à droite : padding 18/22 hérité du bouton à icône gauche ; garder tel quel), fond accent, « Reprendre » 16 sp 700 onAccent + flèche arrow-right 18 (trait 2,2), gap 8.
- Hauteur sur la maquette : 276 dp.
- Sémantique : `Modifier.semantics(mergeDescendants = true)` + `role = Button` ; intitulé proposé `resume_card_content_description`.

**Variante B — compacte (1.03, mode grille)** ⚠ contredit la spec (voir Écarts), documentée au cas où elle serait retenue :

- Row(gap 16, center), fond surface, rayon 20, padding 16.
- Couverture 56×84 ; Column(weight 1, gap 2) : « Reprendre » 14 sp 700 accent ; titre 22 sp 700 lh 1,25 ; « Partie II, chap. I · 31 % » 14 sp lh 1,4 textSecondary ; barre 6 dp accent (marge haute 8).
- Bouton rond 48 (cercle) fond accent, flèche arrow-right 20 (trait 2,2) onAccent.

### 3.4 BookRow (liste)

- Row(centerVertically, padding end 8) contenant :
  - Zone cliquable principale (weight 1) : padding top/bottom 12, start 24, end 8 ; Row(gap 16, center)
    - Couverture 48×72 rayon 4 (monogramme 1 lettre 22 sp 600).
    - Column(weight 1, gap 2) : titre 18 sp 600 lh 1,3 (passe à la ligne ; proposé maxLines 3) ; auteur 16 sp lh 1,35 textSecondary, 1 ligne ; **StatusLine** (marge haute 6).
  - IconButton ⋮ 48×48 (icône more-vertical 24, textSecondary, intitulé `library_book_options`).
- StatusLine :
  - En cours : Row(center, gap 12) : barre 4 dp (§3.7, weight 1) + pourcentage largeur fixe 44, aligné à droite, 14 sp 600 textSecondary, chiffres tabulaires (`fontFeatureSettings = "tnum"`).
  - Terminé : Row(center, gap 6) : coche 18 (trait 2,4) + « Terminé » 14 sp 600 textSecondary.
  - Nouveau (spec, non dessiné, PROPOSÉ) : même ligne que « Terminé », sans icône, texte « Nouveau » 14 sp 600 textSecondary.
- Hauteur type : ≈ 98 dp (titre sur 1 ligne) ; aucune séparation entre lignes.

### 3.4b BookGridCell (grille)

- Column(gap 10) :
  - Zone cliquable : Column(gap 10)
    - Couverture pleine largeur, ratio 2:3, rayon 4, ombre 0/1/2 dp noir 12 % ⚠ + tranche intérieure gauche de 4 dp noir 7 % ⚠ (dessiner un rectangle 4 dp à gauche, `drawBehind`). Vignette générée « titre » : Column centrée, padding vertical 16 / horizontal 12, gap 12 : titre 600 lh 1,2 centré (**28 sp si titre court**, 22 sp sinon — maquette : « Bel-Ami », « Germinal » en 28 ; « Le Grand Meaulnes », « Vingt mille lieues sous les mers » en 22 ; seuil proposé : ≤ 10 caractères), filet 24×2, auteur 14 sp 500.
    - Column(gap 2) : titre 18 sp 600 lh 1,3, **2 lignes max** ; auteur 16 sp lh 1,35 textSecondary.
  - Row(center, gap 4), collée à la zone précédente (marge −10 qui annule le gap) : StatusLine (weight 1, marge haute 6) + IconButton ⋮ 48.
- La couverture générée en grille est décorative (`contentDescription = null`) ; le titre textuel en dessous porte l’information.

### 3.5 SortSegmentedButton (choix unique)

- Conteneur : bordure 1 outline, rayon 12, `clip`, pleine largeur, hauteur 48.
- 3 segments `weight 1`, séparés par un trait vertical 1 outline ; padding horizontal 6 ; contenu centré, gap 4 ; 16 sp.
- Sélectionné : fond selection, texte onSelection, **700**, coche 18 (trait 2,4) avant le libellé.
- Non sélectionné : fond transparent, texte text, 500, sans coche.
- Sémantique : `selectableGroup()` sur le conteneur (intitulé « Trier par »), `Modifier.selectable(selected, role = Role.RadioButton)` par segment. Pas d’accent (spec : sélection = selection + gras + coche).
- Équivalent M3 : `SingleChoiceSegmentedButtonRow` restylé (forme rayon 12 sur les extrémités, couleurs ci-dessus, `icon` = coche).

### 3.6 ViewModeToggle (Liste / Grille)

- Même conteneur que 3.5 (bordure 1 outline, rayon 12), 2 segments de **52×48**, séparateur 1 outline.
- Icônes seules 20 : list (3 traits) et grid (4 carrés). Sélectionné : fond selection, icône onSelection ; non sélectionné : icône text.
- Intitulés : « Liste », « Grille » ; groupe « Affichage » ; état exposé (`selected`).

### 3.7 ProgressBar

| Variante | Hauteur | Rayon | Piste | Remplissage | Usage |
| --- | --- | --- | --- | --- | --- |
| Livre en cours | 6 | 3 | progressTrack | **accent** | carte Reprendre, barre de lecture |
| Autres livres | 4 | 2 | progressTrack | clair : text ; sombre : textSecondary ⚠ | lignes et cellules de la bibliothèque |

- Remplissage arrondi aux deux extrémités (rayon identique), largeur = fraction. Utiliser un `Canvas`/`Box` plutôt que `LinearProgressIndicator` (qui ajoute un espace et un point d’arrêt en M3 récent), ou désactiver `gapSize` et `drawStopIndicator`.
- Sémantique : `progressSemantics(fraction)`.

### 3.8 Boutons

| Type | Hauteur | Padding horizontal | Forme | Fond / bordure | Texte | Icône |
| --- | --- | --- | --- | --- | --- | --- |
| Principal d’écran vide | 56 | 18 / 22 | pilule (28) | accent | onAccent 16 sp 700 | 20, à gauche, gap 8 |
| Principal | 48 | 18 / 22 (avec icône), 24 (sans icône, dialogues) | pilule | accent | onAccent 16 sp 700 | 20, gap 8 |
| Contour (« Importer », « Sommaire », « Journal ») | 48 | 18 / 22 | pilule | bordure 1 outline | text 16 sp **600** | 20, gap 8 |
| Contour danger (« Supprimer le livre ») | 48 | 18 / 22 | pilule | bordure 1 danger | danger 16 sp 600 | trash 20 |
| Plein danger (dialogue) | 48 | 24 | pilule | danger | `#FFFFFF` ⚠ (voir Écarts) 16 sp 700 | — |
| Texte (dialogue) | 48 | 14 | pilule | — | text 16 sp 700 | — |
| Texte sur inverse (snackbar, carte) | 48 | 16 | pilule | — | inverseAccent (snackbar) / onInverse (« Rester ici ») 16 sp 700 | — |
| Plein sur inverse (« Revenir ») | 48 | 16 / 20 | pilule | inverseAccent | inverse 16 sp 700 | undo 20, gap 8 |
| Icône | 48×48 | — | cercle | — | — | 24, text (⋮ : textSecondary) |

- Largeur minimale de toute cible : 48 dp (spec). Ripple borné à la forme.

### 3.9 DropdownMenu (menu ⋮)

- Largeur 232, padding vertical 8, fond surface, rayon 12, ombre 0/6/24 noir 22 % ⚠ (`shadowElevation ≈ 8.dp`, `tonalElevation = 0`).
- Item : hauteur 48, padding horizontal 16, Row(gap 14, center), icône 20 + libellé 16 sp 500.
- Couleurs : item normal text ; item destructif danger (icône et texte).

### 3.10 VersoSnackbar

- Hôte : `SnackbarHost` avec padding horizontal 16, bas 24 + `navigationBars`.
- Conteneur : min 56 de haut, padding top/bottom/end 4, start 20 ; Row(SpaceBetween, center, gap 12) ; fond inverse, texte onInverse 16 sp lh 1,4 ; rayon 12 ; ombre 0/4/16 noir 20 % ⚠.
- Action : TextButton 48, padding horizontal 16, 16 sp 700, **inverseAccent**, ne rétrécit pas.
- `liveRegion = Polite` (fourni par M3).

### 3.11 VersoDialog (alertdialog)

- Scrim : clair `text` à 42 % (= `#1F1B16` alpha 0,42) ; sombre noir à 58 % ⚠ (relevé sur les feuilles sombres, aucun dialogue sombre dessiné).
- Conteneur : largeur = écran − 2 × 24 (342 sur 390) ; centré verticalement ; padding top 24, horizontal 24, bottom 16 ; Column gap 16 ; fond surface ; rayon 28 ; ombre 0/8/28 noir 22 % ⚠.
- Icône optionnelle 28 textSecondary centrée ; si présente, le titre est centré, sinon aligné à gauche.
- Titre 22 sp 700 lh 1,3 text. Corps : Column(gap 10), 16 sp lh 1,5 textSecondary.
- Boutons : `FlowRow(horizontalArrangement = End, spacing 8)`, marge haute 4 → texte d’abord, principal ensuite (à droite).
- Variantes : principal accent (1.05, 1.06) ; destructif danger (1.08, confirmation d’effacement du journal).
- Implémentation : `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))` avec contenu custom, ou `BasicAlertDialog` restylé.

### 3.12 Champ de texte (fiche)

- Label au-dessus (pas de label flottant) : 14 sp 700 textSecondary, gap 6.
- Champ : hauteur 56, padding 14 / 16, bordure 1 outline, rayon 12, fond **background**, texte 16 sp lh 1,45 text, 1 ligne.
- Focus (spec, non dessiné) : bordure 2 **accent**. Curseur : accent.
- Aide sous le champ : 14 sp lh 1,4 textSecondary, reliée au champ pour TalkBack.
- `OutlinedTextField` M3 restylé ou `BasicTextField` + décoration.

### 3.13 Couverture et vignette générée

- Rayon 4 partout. Tailles : 48×72 (liste), 56×84 (carte compacte), 64×96 (carte Reprendre), 96×144 (fiche), pleine largeur 2:3 (grille).
- Image EPUB : `ContentScale.Crop`, fond progressTrack pendant le chargement (proposé).
- Vignette générée (règle de la spec : « couleur sourde choisie à partir du titre, monogramme ou titre en texte clair, contraste d’au moins 4,5:1 ») :
  - Couleur : `palette[Math.floorMod(hash, 6)]`, avec un hachage stable (pas `String.hashCode()` d’un objet mutable ; un CRC32 convient). La spec dit « choisie à partir du titre » ; proposé : hacher l’empreinte SHA-256 du fichier plutôt que le titre, pour que la couleur ne change pas quand on corrige le titre (à confirmer). Le monogramme, lui, suit le titre affiché.
  - Palette relevée dans les maquettes ⚠ (pas de jeton ; à ajouter à `tokens.json`) :

    | Teinte | Clair | Sombre | Grille (clair, pastel ⚠) |
    | --- | --- | --- | --- |
    | Ardoise | `#37515B` | `#2C3A40` | `#D5E0E0` |
    | Brun | `#624833` | `#3B3129` | `#EBDCCB` |
    | Gris chaud | `#4B4540` | `#33302C` | `#D9D4CE` |
    | Olive | `#4A5540` | `#2F3629` | `#DDE3D1` |
    | Indigo | `#4A4A63` | `#2E2E3A` | — |
    | Prune | `#6F434C` | `#3D2B30` | — |

  - Texte : clair → `background` (`#F5F1E8`) ; sombre → `text` (`#E8E2D8`). Monogramme 600.
  - Monogramme : 1 initiale pour 48 dp de large (« V », « B », « M » pour *Le Grand Meaulnes* : l’article est ignoré) ; 2 initiales à partir de 56 dp (« MB »). Ignorer les articles initiaux (le, la, les, l’, un, une, des). Taille : 22 sp (48 et 96 dp), 18 sp (56 et 64 dp).
  - Grille : titre + filet + auteur (§3.4b). ⚠ La maquette grille utilise un fond pastel et du texte `text` foncé, contraire à la spec : voir Écarts.

### 3.14 Ligne de métadonnée (fiche)

- Row(SpaceBetween, gap 16), padding vertical 12, trait bas 1 divider.
- Libellé 16 sp 400 textSecondary ; valeur 16 sp 600 text, alignée à droite, peut passer à la ligne.

### 3.15 Lignes de paramètres

- Section : Column, padding vertical 8, trait bas 1 divider (sauf la dernière). Titre de section 14 sp 700 textSecondary, padding top 12, horizontal 24, bottom 4 (encre, jamais accent).
- Ligne (SwitchRow / ActionRow / ValueRow) : min 64 de haut, padding top/bottom 8, start 24, end 12, Row(center, gap 12) ; Column(weight 1, gap 2) : titre 16 sp 600 lh 1,35 text ; résumé 14 sp lh 1,4 textSecondary.
  - SwitchRow : toute la ligne est `toggleable(role = Switch)` ; interrupteur à droite dans une zone 60×48.
  - ActionRow : icône 20 à droite avec padding end 12 (trash danger, external-link ou chevron-right textSecondary).
  - ValueRow : valeur 16 sp textSecondary + espace de 12 à droite ; non cliquable.
- Interrupteur (état activé dessiné) : piste 52×32 rayon 16 **accent** ; pastille 24 (cercle) onAccent, à 4 dp du bord droit ; coche 16 (trait 2,6) couleur accent dans la pastille. État désactivé (PROPOSÉ, non dessiné) : piste surfaceHigh avec bordure 2 outline, pastille 16 outline à gauche, sans icône. `Switch` M3 avec `thumbContent` = coche, couleurs ci-dessus.

### 3.16 VersoBottomSheet (Sommaire, Journal)

- `ModalBottomSheet` : bord haut à 88 dp du haut de l’écran (hauteur fixe = écran − 88, proposé : `skipPartiallyExpanded = true`), fond surface, forme rayons haut 28, ombre 0/−4/20 noir 14 % ⚠, scrim comme §3.11.
- Poignée : 32×4 rayon 2 outline, marges 6 / 4 (`dragHandle` personnalisé).
- En-tête : Row(SpaceBetween, alignement haut, gap 8), padding top 4, end 12, bottom 8, start 24 ; Column(padding top 10) titre 22 sp 700 lh 1,3 + sous-titre 14 sp lh 1,4 textSecondary (marge haute 2) ; IconButton « Fermer » (x 24).
- Contenu défilant ; padding bas 24 + `navigationBars`.

### 3.17 ReturnCard

Voir 1.14. Conteneur : padding top 16, end 12, bottom 10, start 20 ; Column gap 10 ; fond inverse ; rayon 20 ; ombre 0/6/20 noir 25 % ⚠ ; position 16 / 16 / 24 + `navigationBars`.

### 3.18 Icônes

Toutes les icônes des maquettes sont des tracés 24×24 au trait (`fill="none"`, `stroke-linecap="round"`, `stroke-linejoin="round"`), dessinés en `currentColor`. Ce sont des icônes **Lucide** (licence ISC). Les Material Symbols Rounded sont pleins et plus lourds : ils ne rendent pas le même style. Recommandation : convertir les tracés ci-dessous en `ImageVector` avec un utilitaire unique :

```kotlin
fun strokeIcon(name: String, vararg paths: String, strokeWidth: Float = 2f): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { d ->
            addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black), // teinté par Icon(tint = …)
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
```

Les éléments `<circle>` sont convertis en tracés ci-dessous. Les petits segments `h.01` dessinent un point grâce au bout arrondi.

| Nom | Où | Taille / trait | Lucide | Material Symbols Rounded | Tracé(s) `d` |
| --- | --- | --- | --- | --- | --- |
| settings | barre bibliothèque | 24 / 2 | `settings` | `settings` | `M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z` + `M12 9a3 3 0 1 1 0 6a3 3 0 1 1 0-6z` |
| plus | « Importer », « Importer un EPUB » | 20 / 2 | `plus` | `add` | `M12 5v14M5 12h14` |
| arrow-right | bouton « Reprendre » de la carte | 18 / 2,2 (20 / 2,2 variante compacte) | `arrow-right` | `arrow_forward` | `M5 12h14M13 6l6 6-6 6` |
| arrow-left | retour (fiche, paramètres, lecture) | 24 / 2 | `arrow-left` | `arrow_back` | `M19 12H5M11 18l-6-6 6-6` |
| list | bascule Liste | 20 / 2 | `menu` | `menu` | `M4 6h16M4 12h16M4 18h16` |
| grid | bascule Grille | 20 / 2 | `layout-grid` (proche) | `grid_view` | `M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z` |
| check | segment sélectionné, « Terminé », « Lu » | 18 / **2,4** ; interrupteur 16 / **2,6** | `check` (proche) | `check` | `M5 12.5l4.5 4.5L19 7.5` |
| more-vertical | ⋮ | 24, **plein** | `ellipsis-vertical` | `more_vert` | pleins, sans trait : `M12 3.9a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0-3.2z` `M12 10.4a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0-3.2z` `M12 16.9a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0-3.2z` |
| info | menu « Détails du livre » | 20 / 2 | `info` | `info` | `M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z` + `M12 16v-4.5M12 8h.01` |
| trash | menu « Supprimer », « Supprimer le livre », « Effacer le journal » | 20 / 2 | `trash-2` | `delete` | `M4 7h16M9 7V4.5h6V7M18 7l-.9 13H6.9L6 7M10 11v6M14 11v6` |
| alert-triangle | dialogue « Fichier refusé » | 28 / 2 | `triangle-alert` | `warning` | `M12 4 2.5 20h19z` + `M12 10v4.5M12 17.5h.01` |
| book-open | écran vide (56 / **1,5**), bouton « Reprendre » de la fiche (20 / 2) | voir colonne | `book-open` | `menu_book` | `M4 5.5A1.5 1.5 0 0 1 5.5 4H10a2 2 0 0 1 2 2v14a1.5 1.5 0 0 0-1.5-1.5h-5A1.5 1.5 0 0 1 4 17z` + `M20 5.5A1.5 1.5 0 0 0 18.5 4H14a2 2 0 0 0-2 2v14a1.5 1.5 0 0 1 1.5-1.5h5a1.5 1.5 0 0 0 1.5-1.5z` |
| external-link | « Code source » | 20 / 2 | `external-link` | `open_in_new` | `M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5` |
| chevron-right | « Licences open source » (20), « Reprendre ici » (18) | 20 ou 18 / 2 | `chevron-right` | `chevron_right` | `m9 6 6 6-6 6` |
| list-bullets | bouton « Sommaire » | 20 / 2 | `list` | `format_list_bulleted` | `M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01` |
| history | bouton « Journal » | 20 / 2 | `history` | `history` | `M3.5 12a8.5 8.5 0 1 0 2.6-6.1L3.5 8.5` + `M3.5 4v4.5H8` + `M12 7.5V12l3 2` |
| x | « Fermer » des feuilles | 24 / 2 | `x` | `close` | `M18 6 6 18M6 6l12 12` |
| bookmark | carte de retour | 22 / 2 | `bookmark` | `bookmark` | `M7 4h10v16l-5-3.5L7 20z` |
| undo | bouton « Revenir » | 20 / 2 | `undo-2` | `undo` | `M9 14 4 9l5-5` + `M4 9h11a5 5 0 0 1 0 10h-3` |

### 3.19 Typographie (récapitulatif)

| Rôle | Taille | Graisse | Interligne | Usages |
| --- | --- | --- | --- | --- |
| Logo texte | 28 | 700 | — (ls −0,01 em) | « Verso » |
| Grand titre | 28 | 700 | 1,25 (35) | titre de l’écran vide |
| Numéro de chapitre (lecture) | 28 | 400 | 1,2 | « I », « II » |
| Titre d’écran / feuille / dialogue / carte | 22 | 700 | 1,25 (écran, carte) ; 1,3 (feuille, dialogue) | |
| Titre de livre | 18 | 600 (liste, grille) ; 700 (barre de lecture, carte de retour) | 1,3 / 1,25 | |
| Texte d’écran vide | 18 | 400 | 1,55 | |
| Texte d’interface / boutons | 16 | 400 ; boutons 600 (contour) ou 700 (plein, texte) ; segments 500 / 700 | 1,35 – 1,5 | |
| Légende / méta | 14 | 400 ; 600 (%, « Terminé ») ; 700 (titres de section, étiquettes) | 1,3 – 1,4 | |
| Lecture | 19 | 400 (clair) / 380 (sombre, ls +0,01 em) | 1,6 | Readium |

---

## 4. Écarts

### 4.1 HTML ↔ spec (la spec prime)

| # | Sujet | Maquette | Spec | Décision pour l’implémentation |
| --- | --- | --- | --- | --- |
| 1 | Carte « Reprendre » en mode grille (1.03) | Variante compacte : ni auteur, ni extrait, ni « Environ… », bouton flèche sans libellé | La carte montre couverture, titre, auteur, chapitre, extrait de 2 lignes, barre 6 dp, « 31 % lu », « Environ 5 h 30 restantes », bouton « Reprendre » | Carte complète dans les deux modes. Variante compacte documentée (§3.3 B) si Maxime la confirme dans la spec. |
| 2 | Vignettes générées en grille (1.03) | Fond pastel clair + texte foncé (`text`) | « couleur sourde … texte clair » | Palette sourde de la liste + texte clair aussi en grille, avec la mise en page « titre + filet + auteur ». À confirmer. |
| 3 | Espaces dans les `aria-label` | « Options pour « Titre » » avec espaces normales | Espace insécable dans les guillemets | ` ` (déjà dans `library_book_options`). |
| 4 | État « Nouveau » | Absent de toutes les maquettes V1 | « état de lecture (…, « Terminé » avec une coche, ou « Nouveau ») » | Rendu proposé en §3.4 (texte 14 sp 600 textSecondary, sans icône). |
| 5 | Variante DRM du refus | Non dessinée (le README le dit) | « pas un EPUB, ou protégé par un DRM Adobe ou LCP » | Texte proposé `import_error_drm`, même mise en page que 1.06. |
| 6 | Confirmation « Effacer le journal de lecture » | Non dessinée | « avec confirmation » | Dialogue destructif proposé (§3.11). |
| 7 | Règle de sélection « fond + gras + coche » | Bascule Liste/Grille : fond seul (icônes) ; chapitre en cours : fond + gras, pas de coche (mais texte « En cours ») | « Fond + gras + coche, jamais la couleur seule » | Garder la maquette : la bascule expose `selected` pour TalkBack et le fond diffère fortement ; le chapitre courant porte un texte. À signaler dans la spec si on veut une coche. |
| 8 | Icônes hors plage et traits | book-open 56 dp trait 1,5 ; coches trait 2,4 / 2,6 ; flèche 2,2 ; tailles 16, 18, 22, 28, 56 | « Trait de 2, 20 à 24 dp » | Reproduire la maquette (ajustements optiques volontaires), sauf si Maxime préfère la règle stricte. |
| 9 | Fiche du livre : progression | Texte seul (« 31 % lu »), pas de barre | « couverture, progression, temps restant » | Conforme (la progression est affichée en texte). Pas de barre. |
| 10 | Menu ⋮ « Supprimer » | Pas de confirmation dessinée depuis le menu | Suppression avec confirmation | Ouvrir le dialogue 1.08 aussi depuis le menu. |
| 11 | Insets | Aucune barre système ; lecture avec 56 dp en haut ; barre de lecture de 72 dp collée au bord | Les barres, feuilles et la carte tiennent compte des insets | Ajouter les insets `statusBars` / `navigationBars` aux mesures de la maquette (voir chaque écran). |
| 12 | Titre de chapitre de lecture | Style fixé (14 sp / 28 sp 400, centré) | « Centrés : partie en 14 sp, numéro en 28 sp » | Dépend du balisage de l’EPUB : ne s’applique que si l’EPUB sépare partie et numéro ; sinon laisser Readium. |
| 13 | Forme courte « Partie II, chap. I » | Utilisée partout (carte compacte, carte de retour, journal) | Idem (spec, carte de retour) | Les sommaires EPUB ne donnent que des titres : dériver partie/numéro depuis la hiérarchie du sommaire ; à défaut, afficher le titre brut du chapitre. |

### 4.2 Jetons manquants ou incohérents

| # | Besoin | Valeur relevée | Proposition pour `tokens.json` |
| --- | --- | --- | --- |
| 1 | Texte sur `danger` (bouton « Supprimer » du dialogue) | `#FFFFFF` en clair ; rien en sombre (`danger` sombre = `#F2A99E`, le blanc serait illisible) | `onDanger` : clair `#FFFFFF`, sombre `#1B1510` (contraste ≈ 10:1, à vérifier par calcul) |
| 2 | Remplissage des barres 4 dp | clair `text`, sombre `textSecondary` | Jeton dérivé `progressInk` (clair = text, sombre = textSecondary), ou aligner sur `text` : à trancher |
| 3 | Palette des vignettes générées | 6 couleurs × 2 thèmes (+ 4 pastels grille) | `coverPalette.light[]`, `coverPalette.dark[]` (§3.13) |
| 4 | Voile des dialogues et feuilles | clair `rgba(31,27,22,0.42)` (= text à 42 %) ; sombre `rgba(0,0,0,0.58)` | `scrim` + `scrimAlpha` par thème |
| 5 | Ombres | menu 0/6/24 22 % ; dialogue 0/8/28 22 % ; snackbar 0/4/16 20 % ; carte de retour 0/6/20 25 % ; feuille 0/−4/20 14 % ; couverture grille 0/1/2 12 % + tranche 4 dp 7 % | Niveaux d’élévation nommés (`elevation.menu`, `.dialog`, …) |
| 6 | Interrupteur désactivé | Non dessiné | piste `surfaceHigh` + bordure `outline`, pastille `outline` |
| 7 | Champ actif | Non dessiné (spec : accent) | bordure 2 dp `accent` |
| 8 | Vignette générée : couleur du texte | clair `background`, sombre `text` | `onCover` par thème |
| 9 | Carte de retour, bouton « Revenir » : texte | `inverse` (`#2E2A25`) sur `inverseAccent` | `onInverseAccent` = `inverse` (explicite, pour le sombre : `#E8E2D8` sur `#7A3021`) |
| 10 | Hauteurs de barre | bibliothèque 72, fiche/paramètres 64, lecture 72 | `size.topBarDp` : `library 72`, `detail 64`, `reader 72` |

### 4.3 États non dessinés (à prévoir, sans libellé imposé)

- Import en cours (fichier volumineux, Drive) : aucun indicateur dessiné ; la spec vise < 5 s. Proposé : indicateur discret dans le bouton « Importer ».
- Journal vide (`journal_empty`, proposé), sommaire vide (EPUB sans table des matières), couverture en cours de chargement.
- Écran « Licences open source » (Readium BSD-3, polices OFL, AndroidX) : non dessiné ; réutiliser `DetailTopBar` + liste de `ActionRow`.
- Mode sombre des écrans 1.01, 1.03 – 1.09 et 1.14 : non dessinés, à produire uniquement par les jetons (la spec l’exige : « sans zone blanche éblouissante »).
