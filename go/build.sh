#!/usr/bin/env bash

function usage() {
  if [ -n "$1" ]; then
    echo "$1" >&2
  fi

  echo "Usage: $0 [--verbose | -v] [--shared <path>] <major> <version> <core_lib_path> [<github_lib_path>]" >&2
  echo ""
  echo "Example: $0 v1 v1.3.11 /home/runner/work/sdk-go [github.com/ionos-cloud/sdk-go/v5]" >&2
  echo ""
  echo "Options:"
  echo "  --shared <path>  Set the path for the shared SDK package."
  echo "  -v, --verbose    Verbose output."
  echo ""
  exit 1
}

# Args and flags
DEBUG=false
SHARED=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --shared)
      if [ -z "$2" ]; then
        usage "No path specified for --shared."
      fi
      SHARED=$2
      shift 2
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

# MacOS sed command
if [[ "$OSTYPE" == "darwin"* ]]; then
  SED_CMD="sed -i ''"
else
  SED_CMD="sed -i"
fi

# Update go.mod with github_lib_path and version
$SED_CMD "s%^\(.*\)github.com\/ionos-cloud\/sdk-go\(.*\)$%\1${github_lib_path} ${version}%g" go.mod || exit 1

# Modify run.go based on the shared SDK path
if [ -n "$SHARED" ]; then
  # If --shared is set, use its value for _SDK_SHARED_
  $SED_CMD "s%_SDK_SHARED_%github.com/ionos-cloud/sdk-go-bundle/shared%g" run.go || exit 1
  $SED_CMD "s%_SDK_MAIN_%${github_lib_path}%g" run.go || exit 1
else
  # If --shared is not set, merge _SDK_SHARED_ into _SDK_MAIN_
  $SED_CMD "s%_SDK_MAIN_%${github_lib_path}%g" run.go || exit 1
  $SED_CMD "/_SDK_SHARED_/d" run.go || exit 1
  $SED_CMD "s%sdk2.%sdk1.%g" run.go || exit 1
fi

# Remove backup file created by sed (for macOS)
rm -f go.mod.bak

# Update go.mod with the replace directive
if ! grep -q "replace ${github_lib_path} => ${core_lib_path}" go.mod; then
  echo "replace ${github_lib_path} => ${core_lib_path}" >> go.mod || exit 1
fi

# if --shared is set, add replace directive for shared SDK
if [ -n "$SHARED" ]; then
  if ! grep -q "replace github.com/ionos-cloud/sdk-go-bundle/shared => ${SHARED}" go.mod; then
    echo "replace github.com/ionos-cloud/sdk-go-bundle/shared => ${SHARED}" >> go.mod || exit 1
  fi
fi

go mod tidy
go mod vendor
go build . || exit 1

if [ "$DEBUG" = true ]; then
  echo "Build completed with the following configuration:"
  echo "Major version: $major"
  echo "Version: $version"
  echo "Core library path: $core_lib_path"
  echo "GitHub library path: $github_lib_path"
  echo "Shared SDK path: $SHARED"
fi
