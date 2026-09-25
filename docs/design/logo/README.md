# Verso · logo et icône

Logo retenu : **E1, variante Papier**. Deux pages se croisent à la reliure et dessinent un V. La page de devant (à gauche) passe sur celle de derrière, avec un espace de 1,2 unité là où elles se croisent. Chaque page porte deux traits, comme la tranche d'un paquet de feuilles.

## Couleurs (tokens de la palette, voir SPEC.md)

| Élément | Token | Valeur |
|---|---|---|
| Fond de l'icône | Accent (jour) | `#7A3021` |
| Pages | Fond (jour) | `#F5F1E8` |
| Traits de pages | Séparateur (jour) | `#D3CABB` |
| V du logotype, fond clair | Accent (jour) | `#7A3021` |
| V du logotype, fond sombre | Accent (nuit) | `#E8C48E` |
| « erso », fond clair / sombre | Texte (jour) / Texte (nuit) | `#1F1B16` / `#E8E2D8` |

## Contenu

- `android/app/src/main/res/` : à copier tel quel dans le module `app`.
  - `drawable/ic_launcher_foreground.xml` : premier plan de l'icône adaptative (108 dp, tout le dessin tient dans la zone sûre de 66 dp).
  - `drawable/ic_launcher_monochrome.xml` : icône à thème d'Android 13+. Les traits de pages sont découpés dans la forme.
  - `values/ic_launcher_background.xml` : couleur de fond `ic_launcher_background`.
  - `mipmap-anydpi-v26/ic_launcher.xml` et `ic_launcher_round.xml` : icône adaptative (fond, premier plan, monochrome).
- `play-store/verso-play-store-512.png` : icône de la fiche Google Play (512 × 512, carré plein, Google arrondit les coins).
- `svg/` : sources vectorielles.
  - `verso-icone-108.svg` : icône complète sur le canevas 108 × 108 de l'icône adaptative.
  - `verso-icone-premier-plan-108.svg`, `verso-icone-monochrome-108.svg` : les deux calques, sans fond.
  - `verso-icone-ronde.svg` : icône avec masque rond, pour les présentations.
  - `verso-v.svg` : le V seul en `currentColor`, pour l'app (écran d'accueil, à propos).
  - `verso-logotype-clair.svg`, `verso-logotype-sombre.svg` : logotype « Verso » vectorisé (Atkinson Hyperlegible Next Bold), sans dépendance à la police.

## Intégration Android

1. Copier `android/app/src/main/res/` dans `app/src/main/res/`.
2. Supprimer les `ic_launcher*.webp` générés par le modèle Android Studio dans `mipmap-*dpi/` : avec un minSdk 26, seule l'icône adaptative sert.
3. Dans `AndroidManifest.xml`, sur `<application>` : `android:icon="@mipmap/ic_launcher"` et `android:roundIcon="@mipmap/ic_launcher_round"`.
4. L'élément `<monochrome>` demande un `compileSdk` 33 ou plus. Il est ignoré sur les versions plus anciennes d'Android.
5. Ne pas redessiner le logo : les fichiers XML sont générés à partir de la même géométrie que les SVG.
