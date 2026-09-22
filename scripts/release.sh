#!/usr/bin/env bash
# Build, verify and publish a signed release of the current main.
#
#   scripts/release.sh --notes <file.md> [--dry-run] [--yes]
#
# First bump versionCode and versionName in app/build.gradle.kts in one commit.
#   --dry-run  run every check and build the APK, publish nothing
#              (a dirty tree or another branch is only a warning here)
#   --yes      publish without the confirmation prompt
# Extra Gradle flags go in GRADLE_FLAGS, e.g. GRADLE_FLAGS=--offline.
set -euo pipefail
source "$(dirname "$0")/env.sh"
cd "$ROOT"

# SHA-256 of the release signing certificate (every release since v0.2.0).
# An APK signed with another key can't upgrade existing installs.
RELEASE_CERT_SHA256=b50b74b2032f8bf1f643815dacb248c97960f468f8c65ae1e7c24691bc18c0b2
GRADLE="./gradlew ${GRADLE_FLAGS:-}"

dry=0; yes=0; notes=""
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) dry=1 ;;
    --yes) yes=1 ;;
    --notes) [ $# -ge 2 ] || die "--notes needs a file"; notes="$2"; shift ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) die "unknown argument: $1 (see --help)" ;;
  esac
  shift
done
if [ -n "$notes" ]; then
  [ -s "$notes" ] || die "notes file is missing or empty: $notes"
elif [ $dry -eq 0 ]; then
  die "--notes <file.md> is required to publish"
fi

# A problem that blocks a real release but only warns in a dry run.
blocker() { if [ $dry -eq 1 ]; then echo "warning: $* (ignored in dry run)" >&2; else die "$*"; fi; }

# --- version -----------------------------------------------------------------
name="$(sed -n 's/^ *versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)"
code="$(sed -n 's/^ *versionCode = \([0-9][0-9]*\).*/\1/p' app/build.gradle.kts)"
[ -n "$name" ] && [ -n "$code" ] || die "can't read versionName/versionCode from app/build.gradle.kts"
tag="v$name"
note "version $name (versionCode $code)"

# --- repository state --------------------------------------------------------
[ -z "$(git status --porcelain)" ] || blocker "the working tree has uncommitted changes"
branch="$(git rev-parse --abbrev-ref HEAD)"
[ "$branch" = main ] || blocker "releases are made from main, not $branch"

git fetch --quiet --tags origin || die "git fetch from origin failed"
if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
  die "tag $tag already exists: bump versionName and versionCode first"
fi
if gh release view "$tag" >/dev/null 2>&1; then
  die "GitHub release $tag already exists"
fi
if [ $dry -eq 0 ] && git rev-parse -q --verify refs/remotes/origin/main >/dev/null; then
  git merge-base --is-ancestor origin/main HEAD || die "main is behind origin/main: pull first"
fi

prev="$(git describe --tags --abbrev=0 --match 'v*' 2>/dev/null || true)"
if [ -n "$prev" ]; then
  prev_code="$(git show "$prev:app/build.gradle.kts" | sed -n 's/^ *versionCode = \([0-9][0-9]*\).*/\1/p')"
  [ "$code" -gt "$prev_code" ] ||
    die "versionCode $code must be greater than $prev_code (the one in $prev)"
  [ "$code" -eq $((prev_code + 1)) ] ||
    echo "warning: versionCode jumps from $prev_code ($prev) to $code" >&2
fi

[ -f keystore.properties ] || die "keystore.properties is missing: the release can't be signed"

# --- check, build, verify ----------------------------------------------------
note "check: unit tests + lint"
$GRADLE -q testDebugUnitTest lintDebug || die "the check failed: nothing was built"

note "build: assembleRelease"
apk=app/build/outputs/apk/release/app-release.apk
rm -f "$apk"
$GRADLE -q assembleRelease || die "assembleRelease failed"
[ -f "$apk" ] || die "no APK at $apk"

certs="$("$BUILD_TOOLS/apksigner" verify --print-certs "$apk")" || die "apksigner rejected $apk"
# Here-strings and sed (not echo | grep -q, head): with pipefail, a reader that
# exits early turns SIGPIPE into a false failure.
grep -q "SHA-256 digest: $RELEASE_CERT_SHA256\$" <<<"$certs" ||
  die "the APK isn't signed with the release certificate:"$'\n'"$certs"
badging="$("$BUILD_TOOLS/aapt2" dump badging "$apk" | sed -n 1p)"
grep -q "versionCode='$code' versionName='$name'" <<<"$badging" ||
  die "APK version doesn't match build.gradle.kts: $badging"

# Upload under the usual asset name without leaving a copy in the repo root.
stage_dir="$(mktemp -d)"
trap 'rm -rf "$stage_dir"' EXIT
stage="$stage_dir/obsidian-widget-$tag.apk"
cp "$apk" "$stage"

# --- summary -----------------------------------------------------------------
echo
echo "Release  $tag  (versionCode $code)"
echo "APK      $stage  ($(du -h "$stage" | cut -f1 | tr -d ' '), release certificate OK)"
echo "Commit   $(git log -1 --format='%h %s')"
if [ -n "$prev" ]; then
  echo "Since $prev:"
  git log --format='  %h %s' "$prev..HEAD"
fi
if [ -n "$notes" ]; then
  echo "Notes ($notes):"
  sed 's/^/  /' "$notes"
fi
echo

if [ $dry -eq 1 ]; then
  note "dry run: nothing pushed, no tag, no release"
  exit 0
fi

# --- publish -----------------------------------------------------------------
if [ $yes -eq 0 ]; then
  [ -t 0 ] || die "no terminal for the confirmation prompt: pass --yes to publish"
  read -r -p "Push main and publish $tag on GitHub? [y/N] " answer
  case "$answer" in y|Y|yes) ;; *) die "cancelled: nothing published" ;; esac
fi

note "push main"
git push origin main
note "create GitHub release $tag"
gh release create "$tag" "$stage" --target "$(git rev-parse HEAD)" \
  --title "$tag" --notes-file "$notes"
git fetch --quiet --tags origin
note "done: $(gh release view "$tag" --json url -q .url)"
