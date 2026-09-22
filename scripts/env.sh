# Shared setup for the scripts in this folder: source it, don't run it.
# Sets ROOT, JAVA_HOME (when java isn't already usable), SDK, ADB, BUILD_TOOLS.
# Works with macOS's bash 3.2.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

die() { echo "error: $*" >&2; exit 1; }
note() { echo "==> $*"; }

# JDK 17+: keep JAVA_HOME if set, else java on PATH if it runs (macOS ships a
# stub that doesn't), else a Homebrew JDK. Gradle 8.9 runs on JDK 17–22, so the
# unversioned (newest) Homebrew openjdk is the last resort.
if [ -z "${JAVA_HOME:-}" ] && ! java -version >/dev/null 2>&1; then
  for c in /opt/homebrew/opt/openjdk@21 /opt/homebrew/opt/openjdk@17 \
           /usr/local/opt/openjdk@21 /usr/local/opt/openjdk@17 \
           /opt/homebrew/opt/openjdk /usr/local/opt/openjdk; do
    if [ -x "$c/bin/java" ]; then export JAVA_HOME="$c"; break; fi
  done
  [ -n "${JAVA_HOME:-}" ] || die "no JDK found: install JDK 17+ or set JAVA_HOME"
fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi

# Android SDK: local.properties first (what Gradle uses), then the env vars.
SDK="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" 2>/dev/null)"
SDK="${SDK:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}"
[ -n "$SDK" ] && [ -d "$SDK" ] ||
  die "Android SDK not found: set sdk.dir in local.properties or ANDROID_HOME"
ADB="$SDK/platform-tools/adb"
BUILD_TOOLS="$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
BUILD_TOOLS="${BUILD_TOOLS%/}"
