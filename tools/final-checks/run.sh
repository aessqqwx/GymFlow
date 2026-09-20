#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
cd "$ROOT"

python3 - <<'PY'
import re
import xml.etree.ElementTree as ET
from pathlib import Path

for p in Path('app/src/main/res').rglob('*.xml'):
    ET.parse(p)
ET.parse('app/src/main/AndroidManifest.xml')

ru = ET.parse('app/src/main/res/values/strings.xml').getroot()
en = ET.parse('app/src/main/res/values-en/strings.xml').getroot()
for kind in ('string', 'string-array'):
    a = {e.attrib['name'] for e in ru.findall(kind)}
    b = {e.attrib['name'] for e in en.findall(kind)}
    assert a == b, f'locale resource mismatch for {kind}: {sorted(a-b)} / {sorted(b-a)}'

text = '\n'.join(p.read_text(errors='ignore') for p in Path('app/src/main/java').rglob('*.kt'))
strings = {e.attrib['name'] for e in ru.findall('string')}
arrays = {e.attrib['name'] for e in ru.findall('string-array')}
missing_s = set(re.findall(r'R\.string\.([A-Za-z0-9_]+)', text)) - strings
missing_a = set(re.findall(r'R\.array\.([A-Za-z0-9_]+)', text)) - arrays
assert not missing_s, f'missing string resources: {sorted(missing_s)}'
assert not missing_a, f'missing array resources: {sorted(missing_a)}'
print('XML_RESOURCE_OK')
PY

if find app/src/main -type f \( -iname '*.mp3' -o -iname '*.mp4' -o -iname '*.gif' -o -iname '*.webm' -o -iname '*.flac' -o -iname '*.m4a' -o -iname '*.ogg' \) | grep -q .; then
  echo 'OLD_EXERCISE_MEDIA_FOUND' >&2
  exit 1
fi
if grep -RIEq 'ExerciseVisual|VideoView|R\.raw\.ex_|animationLoading|preload.*exercise|CircularProgressIndicator' app/src/main/java app/src/main/res; then
  echo 'REMOVED_OR_FORBIDDEN_UI_REFERENCE_FOUND' >&2
  exit 1
fi
if grep -RIEq 'TODO|FIXME|Coming soon|Not implemented|placeholder button|fake implementation' app/src/main tools/pure-tests; then
  echo 'PLACEHOLDER_FOUND' >&2
  exit 1
fi
grep -q 'versionName = "1.0"' app/build.gradle.kts
grep -q 'put("schemaVersion", 5)' app/src/main/java/com/aess/gymflow/GymFlowStore.kt
grep -q 'schema in 1..5' app/src/main/java/com/aess/gymflow/GymFlowStore.kt
grep -q 'putNullable("birthDate"' app/src/main/java/com/aess/gymflow/GymFlowStore.kt
grep -q 'birthDateFromLegacyMillis(o.optNullableLong("birthDateMillis"))' app/src/main/java/com/aess/gymflow/GymFlowStore.kt
if grep -q 'birthDateMillis:' app/src/main/java/com/aess/gymflow/Models.kt; then
  echo 'LEGACY_BIRTHDATE_MODEL_FIELD_FOUND' >&2
  exit 1
fi
[ "$(grep -RIl 'ExoPlayer.Builder' app/src/main/java | wc -l | tr -d ' ')" = "1" ]
grep -q 'ExoPlayer.Builder' app/src/main/java/com/aess/gymflow/MusicService.kt
grep -q 'MediaSessionService' app/src/main/AndroidManifest.xml
grep -q 'foregroundServiceType="mediaPlayback"' app/src/main/AndroidManifest.xml
grep -q 'OpenMultipleDocuments' app/src/main/java/com/aess/gymflow/PlayerScreen.kt
grep -q 'takePersistableUriPermission' app/src/main/java/com/aess/gymflow/PlayerScreen.kt
if grep -RIEq 'contentResolver\.delete|DocumentsContract\.delete' app/src/main/java; then
  echo 'PHYSICAL_MEDIA_DELETE_CALL_FOUND' >&2
  exit 1
fi
printf '%s\n' 'STATIC_CHECK_OK'
