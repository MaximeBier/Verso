#!/usr/bin/env bash
# Tests du garde-fou : lance check-no-internet.sh sur des manifestes fabriqués.
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
check="$here/check-no-internet.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
failures=0

manifest() {
  local file="$work/$1.xml"
  shift
  local application="${APPLICATION:-<application android:name=\"com.maximebier.verso.VersoApplication\" android:allowBackup=\"false\" android:dataExtractionRules=\"@xml/data_extraction_rules\" />}"
  {
    echo '<?xml version="1.0" encoding="utf-8"?>'
    echo '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.maximebier.verso">'
    for permission in "$@"; do
      echo "    <uses-permission android:name=\"$permission\" />"
    done
    echo "    $application"
    echo '</manifest>'
  } > "$file"
  echo "$file"
}

expect() {
  local expected="$1" label="$2" file="$3"
  bash "$check" "$file" > /dev/null 2>&1
  local actual=$?
  if [[ "$actual" -eq "$expected" ]]; then
    echo "OK   $label"
  else
    echo "ÉCHEC $label : code $actual, attendu $expected"
    failures=$((failures + 1))
  fi
}

expect 0 "aucune permission" "$(manifest clean)"
expect 0 "permission interne AndroidX" "$(manifest androidx com.maximebier.verso.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION)"
expect 0 "permission interne AndroidX (suffixe .spike)" "$(manifest spike com.maximebier.verso.spike.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION)"
expect 1 "INTERNET" "$(manifest internet android.permission.INTERNET)"
expect 1 "ACCESS_NETWORK_STATE" "$(manifest network android.permission.ACCESS_NETWORK_STATE)"
expect 1 "permission interne d'une autre app" "$(manifest other org.example.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION)"
expect 1 "sauvegarde Android non désactivée (allowBackup absent)" "$(APPLICATION='<application android:name="com.maximebier.verso.VersoApplication" android:dataExtractionRules="@xml/data_extraction_rules" />' manifest nobackupattr)"
expect 1 "allowBackup=true" "$(APPLICATION='<application android:allowBackup="true" android:dataExtractionRules="@xml/data_extraction_rules" />' manifest backuptrue)"
expect 1 "dataExtractionRules absent" "$(APPLICATION='<application
        android:allowBackup="false" />' manifest norules)"
expect 0 "attributs sur plusieurs lignes" "$(APPLICATION='<application
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules" />' manifest multiline)"
expect 2 "manifeste absent" "$work/absent.xml"

if [[ "$failures" -gt 0 ]]; then
  echo "$failures test(s) en échec"
  exit 1
fi
echo "Tous les tests du garde-fou passent"
