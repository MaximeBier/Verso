#!/usr/bin/env bash
# Garde-fou « Rien ne quitte le téléphone » : le manifeste fusionné de :app ne doit déclarer aucune permission hormis
# INTERNET (V3, pour la seule traduction de la sélection) et
# la permission interne DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION qu'AndroidX Core ajoute à toute application,
# et doit désactiver la sauvegarde Android (allowBackup="false" et dataExtractionRules).
# Usage : scripts/check-no-internet.sh [chemin/vers/AndroidManifest.xml]
# Codes : 0 = conforme, 1 = permission interdite ou sauvegarde active, 2 = manifeste introuvable.
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

allowed='^(android\.permission\.INTERNET|com\.maximebier\.verso(\.[A-Za-z0-9_]+)?\.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION)$'

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
  echo "Verso ne demande que INTERNET (traduction). Retirer la dépendance fautive ou ajouter <uses-permission android:name=\"…\" tools:node=\"remove\" /> au manifeste." >&2
  exit 1
fi

# Sauvegarde Android : sinon le système enverrait livres, base et réglages sur le compte Google (ou vers un autre
# appareil), sans que l'app ait besoin d'aucune permission.
application="$(tr '\n' ' ' < "$manifest" | grep -oE '<application[^>]*>' | head -n 1 || true)"
backup_errors=""
if [[ ! "$application" =~ android:allowBackup=\"false\" ]]; then
  backup_errors+="android:allowBackup=\"false\" manquant"$'\n'
fi
if [[ ! "$application" =~ android:dataExtractionRules=\"@xml/data_extraction_rules\" ]]; then
  backup_errors+="android:dataExtractionRules=\"@xml/data_extraction_rules\" manquant"$'\n'
fi
if [[ -n "$backup_errors" ]]; then
  echo "ERREUR : sauvegarde Android non désactivée dans $manifest :" >&2
  printf '%s' "$backup_errors" | sed 's/^/  - /' >&2
  exit 1
fi

echo "OK : aucune permission hormis INTERNET (traduction), sauvegarde Android désactivée ($manifest)."
