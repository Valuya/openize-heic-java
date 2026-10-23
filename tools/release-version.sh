#!/usr/bin/env bash
# The next release of the library, from the Conventional Commits since the last release tag (vX.Y.Z), with git-cliff
# and cliff.toml. Only the commits that change what is published count: those that change src/main or pom.xml, other
# than build, chore, ci, docs, style and test commits, unless breaking. A dependency upgrade that users should get is a
# fix(deps).
#
# Usage, in a clone with the tags:
#
#   tools/release-version.sh          prints GitHub-output lines:
#                                       last=vX.Y.Z        the last release tag
#                                       needed=true|false  whether a commit since then changed what is published
#                                       version=X.Y.Z      the next version when needed, the last one otherwise
#   tools/release-version.sh --notes X.Y.Z
#                                     prints the release notes of version X.Y.Z, made of those commits
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
GIT_CLIFF="${GIT_CLIFF:-git-cliff}"
tag_pattern='^v[0-9]+\.[0-9]+\.[0-9]+$'

# The nearest tag, not the highest: the fork keeps upstream's tags, and v26.5.1 is Openize.HEIC's, not a release of
# this library
last=$(git describe --tags --abbrev=0 --match 'v[0-9]*' HEAD 2>/dev/null || true)
if [[ ! "$last" =~ $tag_pattern ]]; then
    echo "release-version.sh: no release tag vX.Y.Z before HEAD, found '$last'" >&2
    exit 1
fi

# The range first: --include-path takes several values and would read it as a path. Not --tag "$last": that marks the
# range as released, and git-cliff then never bumps.
cliff=("$GIT_CLIFF" "${last}..HEAD" --config cliff.toml --tag-pattern "$tag_pattern"
    --include-path 'src/main/**' --include-path pom.xml)

if [[ "${1:-}" == --notes ]]; then
    "${cliff[@]}" --tag "v$2" --strip all
    exit 0
fi

echo "last=$last"
changes=$(git log --no-merges --format=%s "${last}..HEAD" -- src/main pom.xml \
    | grep -vE '^(build|chore|ci|docs|style|test)(\([^)]*\))?: ' || true)
breaking=$(git log --no-merges --format=%b "${last}..HEAD" -- src/main pom.xml | grep -E '^BREAKING[ -]CHANGE:' || true)
if [[ -z "$changes" && -z "$breaking" ]]; then
    echo "needed=false"
    echo "version=${last#v}"
    exit 0
fi
next=$("${cliff[@]}" --bumped-version)
# git-cliff keeps the last version when no commit is conventional; a change to the library still needs a new one
if [[ "$next" == "$last" ]]; then
    next=$("${cliff[@]}" --bumped-version --bump patch)
fi
echo "needed=true"
echo "version=${next#v}"
