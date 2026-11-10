#!/usr/bin/env bash
#
# Publishes VolmLib to the local Maven repository under the coordinate this project
# resolves by default: com.github.VolmitSoftware:VolmLib:master-SNAPSHOT.
#
# VolmLib is normally picked up as a composite build straight from source, so this is
# only needed when you build with -PuseLocalVolmLib=false, or when another tool has to
# resolve VolmLib from ~/.m2 rather than from the included build. Gradle reads ~/.m2 only
# when -PuseMavenLocal=true is passed or the composite VolmLib is off (-PuseLocalVolmLib=false).
#
# The lookup mirrors settings.gradle: it walks up from this script looking for a
# VolmLib checkout sitting beside the project root. Set VOLMLIB_DIR to override.
# Any extra arguments are forwarded to gradle.

set -o pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

has_volmlib_settings() {
    [[ -f "$1/settings.gradle" || -f "$1/settings.gradle.kts" ]]
}

resolve_volmlib_directory() {
    if [[ -n "$VOLMLIB_DIR" ]] && has_volmlib_settings "$VOLMLIB_DIR"; then
        printf '%s' "$VOLMLIB_DIR"
        return 0
    fi

    local current="$SCRIPT_DIR"
    while [[ -n "$current" ]]; do
        if has_volmlib_settings "$current/VolmLib"; then
            printf '%s' "$current/VolmLib"
            return 0
        fi

        local parent
        parent="$(dirname -- "$current")"
        [[ "$parent" == "$current" ]] && break
        current="$parent"
    done

    return 1
}

if ! VOLMLIB_DIRECTORY="$(resolve_volmlib_directory)"; then
    printf 'Could not find a VolmLib checkout.\n' >&2
    printf 'Expected a VolmLib directory beside this project root, or VOLMLIB_DIR pointing at one.\n' >&2
    exit 1
fi

printf '==> Publishing VolmLib from %s\n' "$VOLMLIB_DIRECTORY"

cd "$VOLMLIB_DIRECTORY" || exit 1
./gradlew publishToMavenLocal \
    -Pgroup=com.github.VolmitSoftware \
    -Pversion=master-SNAPSHOT \
    -PvolmLibArtifactId=VolmLib \
    "$@"
