#!/bin/bash
set -e

echo "Starting $0 at $(date)"

cd "$(dirname $0)"

CHECKOUT_DIR="$(cd ../../.. && pwd)"
OUT_DIR="$CHECKOUT_DIR/out"
if [ "$DIST_DIR" == "" ]; then
  DIST_DIR="$OUT_DIR/dist"
fi
# move OUT_DIR and DIST_DIR into subdirectories so that if diagnose-build-failure deletes them, it doesn't interfere with any files generated by buildbot code
export OUT_DIR="$OUT_DIR/incremental"
mkdir -p "$OUT_DIR"
export DIST_DIR="$DIST_DIR/incremental"
mkdir -p "$DIST_DIR"

if echo "$BUILD_NUMBER" | grep "P" >/dev/null; then
  PRESUBMIT=true
else
  PRESUBMIT=false
fi

function hashOutDir() {
  hashFile=out.hashes
  echo "hashing out dir and saving into $DIST_DIR/$hashFile"
  # We hash files in parallel for more performance (-P <number>)
  # We limit the number of files hashed by any one process (-n <number>) to lower the risk of one
  # process having to do much more work than the others.
  # We do allow each process to hash multiple files (also -n <number>) to avoid spawning too many processes
  # It would be nice to copy all files, but that takes a while
  time (cd $OUT_DIR && find -type f | grep -v "$hashFile" | xargs --no-run-if-empty -P 32 -n 64 sha1sum > $DIST_DIR/$hashFile)
  echo "done hashing out dir"
}
hashOutDir

# diagnostics to hopefully help us figure out b/188565660
function zipKotlinMetadata() {
  zipFile=kotlinMetadata.zip
  echo "zipping kotlin metadata"
  rm -f "$DIST_DIR/$zipFile"
  (cd $OUT_DIR && find -name "*kotlin_module" | xargs zip -q -u "$DIST_DIR/$zipFile")
  echo done zipping kotlin metadata
}

# If we encounter a failure in postsubmit, we try a few things to determine if the failure is
# reproducible
DIAGNOSE_ARG=""
if [ "$PRESUBMIT" == "false" ]; then
  DIAGNOSE_ARG="--diagnose"
fi

# Run Gradle
EXIT_VALUE=0
if impl/build.sh $DIAGNOSE_ARG buildOnServer checkExternalLicenses listTaskOutputs validateAllProperties \
    --profile "$@"; then
  echo build succeeded
  EXIT_VALUE=0
else
  zipKotlinMetadata
  echo build failed
  EXIT_VALUE=1
fi

# Parse performance profile reports (generated with the --profile option above) and re-export the metrics in an easily machine-readable format for tracking
impl/parse_profile_htmls.sh

echo "Completing $0 at $(date)"

exit "$EXIT_VALUE"