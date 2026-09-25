#!/usr/bin/env bash
# Garde-fou « Rien ne quitte le téléphone » : le manifeste fusionné de :app ne doit déclarer aucune permission,
# hormis la permission interne DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION qu'AndroidX Core ajoute à toute application.
# Usage : scripts/check-no-internet.sh [chemin/vers/AndroidManifest.xml]
# Codes : 0 = conforme, 1 = permission interdite, 2 = manifeste introuvable.
set -euo pipefail

manifest="${1:-}"
if [[ -z "$manifest" ]]; then
  manifest="app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"
  if [[ ! -f "$manifest" ]]; then
    manifest="$(find app/build/intermediates -type f -name AndroidManifest.xml -path '*merged_manifest*' -path '*debug*' 2>/dev/null | head -n 1 || true)"
  fi
fi

if [[ -z "$manifest" || ! -f "$manifest" ]]; then
  echo "ERREUR : manifeste fusionné introuvable (${manifest:-aucun chemin}). Lancer d'abord ./gradlew :app:assembleDebug." >&2
  exit 2
fi

allowed='^com\.maximebier\.verso(\.[A-Za-z0-9_]+)?\.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION$'

# Aplatit le fichier pour lire les balises écrites sur plusieurs lignes.
permissions="$(tr '\n' ' ' < "$manifest" \
  | grep -oE '<uses-permission(-sdk-23)?[^>]*>' \
  | grep -oE 'android:name="[^"]+"' \
  | sed -E 's/android:name="([^"]+)"/\1/' \
  | sort -u || true)"

forbidden=""
while IFS= read -r permission; do
  [[ -z "$permission" ]] && continue
  if [[ ! "$permission" =~ $allowed ]]; then
    forbidden+="$permission"$'\n'
  fi
done <<< "$permissions"

if [[ -n "$forbidden" ]]; then
  echo "ERREUR : permissions interdites dans $manifest :" >&2
  printf '%s' "$forbidden" | sed 's/^/  - /' >&2
  echo "Verso ne demande aucune permission. Retirer la dépendance fautive ou ajouter <uses-permission android:name=\"…\" tools:node=\"remove\" /> au manifeste." >&2
  exit 1
fi

echo "OK : aucune permission demandée ($manifest)."
