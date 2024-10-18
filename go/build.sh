#!/usr/bin/env bash

function usage() {
  if [ -n "$1" ]; then
    echo "$1" >&2
  fi

  echo "Usage: $0 [--no-input | -n] [--debug | -d] [--bundled] <major> <version> <core_lib_path> [<github_lib_path>]" >&2
  echo ""
  echo "Example: $0 v1 v1.3.11 /home/runner/work/sdk-go [github.com/ionos-cloud/sdk-go/v5]" >&2
  echo ""
  echo "Options:"
  echo "  --bundled        Add the shared bundle require and replace statement."
  echo "  -v, --verbose    Verbose output."
  echo ""
  exit 1
}

# Args and flags
DEBUG=false
BUNDLED=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --bundled)
      BUNDLED=true
      shift
      ;;
    --verbose|-v)
      DEBUG=true
      shift
      ;;
    *)
      if [ -z "$major" ]; then
        major=$1
      elif [ -z "$version" ]; then
        version=$1
      elif [ -z "$core_lib_path" ]; then
        core_lib_path=$1
      elif [ -z "$github_lib_path" ]; then
        github_lib_path=$1
      else
        usage "Too many arguments. Unknown args: $@"
      fi
      shift
      ;;
  esac
done

if [ -z "$major" ]; then
  usage "major version not specified"
fi
if [ -z "$version" ]; then
  usage "version not specified"
fi
if [ -z "$core_lib_path" ]; then
  usage "path to core lib not specified"
fi

# Set default for github_lib_path if not provided
github_lib_path="${github_lib_path:-"github.com/ionos-cloud/sdk-go/${major}"}"

# MacOS really did and made a mess of sed
if [[ "$OSTYPE" == "darwin"* ]]; then
  SED_CMD="sed -i ''"
else
  SED_CMD="sed -i"
fi

$SED_CMD "s%^\(.*\)github.com\/ionos-cloud\/sdk-go\(.*\)$%\1${github_lib_path} ${version}%g" go.mod || exit 1
$SED_CMD "s%\"github.com\/ionos-cloud\/sdk-go\/\(.*\)\"$%\"${github_lib_path}\"%g" run.go || exit 1
# remove backup file created by sed (MacOS creates .bak files by default)
rm -f go.mod.bak

if ! grep -q "replace ${github_lib_path} => ${core_lib_path}" go.mod; then
  echo "replace ${github_lib_path} => ${core_lib_path}" >> go.mod || exit 1
fi

if [ "$BUNDLED" = true ]; then
  if ! grep -q 'github.com/ionos-cloud/sdk-go-bundle/shared' go.mod; then
    echo "require github.com/ionos-cloud/sdk-go-bundle/shared v0.1.1 // indirect" >> go.mod
  fi

  if ! grep -q 'replace github.com/ionos-cloud/sdk-go-bundle/shared' go.mod; then
    echo "replace github.com/ionos-cloud/sdk-go-bundle/shared => /home/avirtopeanu/go/src/sdk-resources/dist/sdk-go-bundle/shared" >> go.mod
  fi
fi

go mod tidy
go build . || exit 1

if [ "$DEBUG" = true ]; then
  echo "Build completed with the following configuration:"
  echo "Major version: $major"
  echo "Version: $version"
  echo "Core library path: $core_lib_path"
  echo "GitHub library path: $github_lib_path"
  echo "Bundled: $BUNDLED"
fi
