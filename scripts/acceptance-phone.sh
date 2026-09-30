#!/usr/bin/env bash
# Passe d’acceptation V1 et V2 sur le téléphone (tâches 7.3 et 16.2).
# Autorisé : compiler, installer l'APK, lancer Verso, toucher l'écran DANS Verso, capturer l'écran.
# Interdit : modifier un réglage système (thème, taille du texte, animations, TalkBack).
# Ces critères-là sont vérifiés par Robolectric/Roborazzi et par Maxime (docs/acceptance-v1.md).
#
# Usage : scripts/acceptance-phone.sh             APK + permissions + installation + captures
#         scripts/acceptance-phone.sh --apk-only  APK + permissions seulement (sans téléphone)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# Poste de développement : JAVA_HOME système mal défini, le JDK 17 est dans LOCALAPPDATA (convention de la partie 01).
if [ -n "${LOCALAPPDATA:-}" ] && [ -d "$LOCALAPPDATA/Programs/jdk17" ]; then
  export JAVA_HOME="$LOCALAPPDATA/Programs/jdk17"
fi
PKG="com.maximebier.verso"
APK="app/build/outputs/apk/debug/app-debug.apk"
OUT="build/acceptance/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

sdk_dir() {
  if [ -f local.properties ] && grep -q '^sdk\.dir=' local.properties; then
    sed -n 's/^sdk\.dir=//p' local.properties | sed -e 's/\\:/:/g' -e 's/\\\\/\//g'
  else
    echo "${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  fi
}

find_aapt2() {
  local bt
  bt="$(ls -d "$(sdk_dir)"/build-tools/*/ 2>/dev/null | sort -V | tail -n 1)"
  if [ -x "${bt}aapt2" ]; then echo "${bt}aapt2"
  elif [ -x "${bt}aapt2.exe" ]; then echo "${bt}aapt2.exe"
  else return 1
  fi
}

echo "== 1. APK debug et permissions"
./gradlew --quiet :app:assembleDebug
AAPT2="$(find_aapt2)" || { echo "aapt2 introuvable dans $(sdk_dir)/build-tools"; exit 1; }
"$AAPT2" dump permissions "$APK" | tee "$OUT/permissions.txt"
UNEXPECTED="$(grep 'uses-permission' "$OUT/permissions.txt" | grep -v "name='$PKG.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'" || true)"
if [ -n "$UNEXPECTED" ]; then
  echo "ÉCHEC : permissions inattendues dans l'APK :"
  echo "$UNEXPECTED"
  exit 1
fi
echo "OK : aucune permission (hors permission de signature interne d'AndroidX)."
if [ "${1:-}" = "--apk-only" ]; then exit 0; fi

if [ -z "${ANDROID_SERIAL:-}" ]; then
  echo "ANDROID_SERIAL absent : export ANDROID_SERIAL=192.168.1.10:5555 (adb Wi-Fi ; si le téléphone ne répond pas : adb connect 192.168.1.10:5555)" >&2
  exit 2
fi
SERIAL="$ANDROID_SERIAL"
# Téléphone en cours d’utilisation (une autre app que Verso ou le lanceur au premier plan) : on n’y touche pas.
FRONT="$(adb -s "$SERIAL" shell dumpsys activity activities | grep -a -m1 topResumedActivity || true)"
if ! printf "%s" "$FRONT" | grep -qE "com.maximebier.verso|launcher"; then
  echo "Une autre application est au premier plan : téléphone en cours d’utilisation, passe annulée." >&2
  exit 3
fi

adb_() { adb -s "$SERIAL" "$@"; }
pause() { sleep "${1:-2}"; }
shot() { adb_ exec-out screencap -p > "$OUT/$1.png"; echo "  capture : $OUT/$1.png"; }
# Hiérarchie de l'écran vers la sortie standard (aucun fichier écrit sur le téléphone).
ui_dump() { adb_ exec-out uiautomator dump /dev/tty 2>/dev/null | tr '>' '\n' || true; }
# Chaîne here-string plutôt que tube : avec pipefail, « grep -q » dans un tube peut renvoyer 141 (SIGPIPE).
on_screen() { local d; d="$(ui_dump)"; grep -qF -e "text=\"$1\"" -e "content-desc=\"$1\"" <<< "$d"; }
tap_on() {
  local d node bounds x1 y1 x2 y2
  d="$(ui_dump)"
  node="$(grep -F -e "text=\"$1\"" -e "content-desc=\"$1\"" <<< "$d" | head -n 1 || true)"
  if [ -z "$node" ]; then echo "  (introuvable à l'écran : $1)"; return 1; fi
  bounds="$(printf '%s' "$node" | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/')"
  read -r x1 y1 x2 y2 <<< "$bounds"
  adb_ shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
  pause 2
}
screen_size() { adb_ shell wm size | sed -nE 's/.*: ([0-9]+)x([0-9]+).*/\1 \2/p' | tail -n 1; }
tap_center() { local w h; read -r w h <<< "$(screen_size)"; adb_ shell input tap $(( w / 2 )) $(( h / 2 )); pause 2; }
fling_up() { local w h; read -r w h <<< "$(screen_size)"; adb_ shell input swipe $(( w / 2 )) $(( h * 8 / 10 )) $(( w / 2 )) $(( h * 2 / 10 )) 60; }
back() { adb_ shell input keyevent KEYCODE_BACK; pause 2; }
# La carte « Revenir » n'apparaît qu'au repos, une fois l'élan des flings retombé : attendre jusqu'à $2 s.
wait_for() { local i; for i in $(seq 1 "${2:-10}"); do on_screen "$1" && return 0; sleep 1; done; echo "  (toujours absent : $1)"; return 1; }

echo "== 2. Installation et lancement"
./gradlew --quiet :app:installDebug
adb_ shell am force-stop "$PKG"
adb_ shell am start -W -n "$PKG/.MainActivity" > /dev/null
pause 3
shot "01-lancement"

echo "== 3. Lecture (réouverture automatique, sinon carte « Reprendre »)"
if on_screen "Paramètres"; then
  shot "02-bibliotheque"
  tap_on "Reprendre" || true
fi
if on_screen "Paramètres"; then
  echo "  Aucun livre commencé : étapes de lecture ignorées."
else
  shot "10-lecture"
  tap_center
  shot "11-barre-affichee"
  tap_on "Sommaire" || true
  shot "12-sommaire"
  back
  if ! on_screen "Journal"; then tap_center; fi
  tap_on "Journal" || true
  shot "13-journal"
  back
  if on_screen "Sommaire"; then tap_center; fi

  echo "== 4. Scroll accidentel (critère 7)"
  for _ in 1 2 3 4 5 6 7 8; do fling_up; sleep 0.3; done
  wait_for "Revenir" 10 || true
  shot "14-carte-revenir"
  tap_on "Revenir" || true
  shot "14b-apres-revenir"

  echo "== 5. Scroll accidentel puis fermeture sans toucher la carte (critère 8)"
  for _ in 1 2 3 4 5 6 7 8; do fling_up; sleep 0.3; done
  pause 2
  adb_ shell input keyevent KEYCODE_HOME
  pause 2
  adb_ shell am kill "$PKG"
  pause 1
  adb_ shell am start -W -n "$PKG/.MainActivity" > /dev/null
  pause 3
  shot "15-reouverture-apres-scroll-accidentel"
  echo "  Comparer 15 à 14b : même paragraphe attendu."
  echo "== 5b. V2 : barre, feuille « Aa », recherche (critères 6, 7, 17, 18)"
  tap_center
  shot "20-barre-v2"
  tap_on "Réglages" || true
  shot "21-feuille-aa"
  tap_on "Fermer" || back
  if ! on_screen "Rechercher"; then tap_center; fi
  tap_on "Rechercher" || true
  adb_ shell input text "riviere"
  pause 4
  adb_ shell input keyevent KEYCODE_ESCAPE
  shot "22-recherche"
  # Premier extrait (la ligne 1 est le champ de recherche, qui contient « riviere »).
  first_result="$(ui_dump | sed -nE 's/.*text="([^"]*[Rr]ivi[eè]re[^"]*)".*/\1/p' | sed -n 2p)"
  if [ -n "$first_result" ]; then
    tap_on "$first_result" || true
    pause 2
    shot "23-resultat-marque"
    wait_for "Revenir" 10 || true
    tap_on "Revenir" || true
    shot "24-apres-revenir"
  else
    echo "  (aucun résultat lisible dans la hiérarchie : étape 23 ignorée)"
    back
  fi

  tap_center
  tap_on "Retour à la bibliothèque" || back
fi

echo "== 6. Bibliothèque, Paramètres, Licences"
if ! on_screen "Paramètres"; then back; fi
shot "02-bibliotheque"
tap_on "Paramètres" || true
shot "09-parametres"
tap_on "Licences open source" || true
shot "09b-licences"
back
back

echo "== 7. Permissions vues par le téléphone (lecture seule)"
adb_ shell dumpsys package "$PKG" | sed -n '/requested permissions:/,/install permissions:/p' | tee "$OUT/permissions-telephone.txt"
echo "Captures et journaux dans $OUT"
