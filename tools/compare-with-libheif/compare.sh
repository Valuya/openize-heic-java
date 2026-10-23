#!/usr/bin/env bash
# Decodes every .heic in a directory with Openize.HEIC and with libheif, then compares them.
# Usage: tools/compare-with-libheif/compare.sh <heic-dir> [output-dir]
# Needs a JDK, Maven (for the jar and its dependencies) and uv (for pillow-heif).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "${here}/../.." && pwd)"
corpus="$(cd "$1" && pwd)"
out="${2:-$(mktemp -d)}"
mkdir -p "${out}" "${out}/classes"

(cd "${root}" && mvn -q -DskipTests package dependency:build-classpath -Dmdep.outputFile="${out}/classpath.txt")
cp="$(ls "${root}"/target/openize-heic-*.jar | head -1):$(cat "${out}/classpath.txt")"
javac -d "${out}/classes" -cp "${cp}" "${here}/Decode.java"

args=()
for f in "${corpus}"/*.heic "${corpus}"/*.HEIC; do
  [ -e "${f}" ] || continue
  args+=("${f}" "${out}/$(basename "${f%.*}").png")
done
java -Xmx4g -cp "${cp}:${out}/classes" Decode "${args[@]}"

uv run -q --no-project --with pillow-heif --with numpy python -I "${here}/compare.py" "${corpus}" "${out}"
