#!/usr/bin/env bash
#
# {App} - {purpose, description}
#


function usage() {
	echo "error: $1"
	echo
	echo "usage: build.sh <major> <version> <core_lib_path>"
	echo "example: build.sh v5 v5.1.3 /home/runner/work/sdk-go"
	exit 1
}

major="${1}"
if [ "${major}" = "" ]; then
	usage "major version not speficied"
fi

version="${2}"
if [ "${version}" = "" ]; then
	usage "version not specified"
fi

core_lib_path="${3}"
if [ "${core_lib_path}" = "" ]; then
	usage "path to core lib not specified"
fi

# replace core lib version
# NOTE: THIS RUNS ON LINUX ONLY - DOESN'T WORK ON OSX - on OSX the -i flag requires an extension name to save backups to
sed -i "s/^\(.*\)github.com\/ionos-cloud\/sdk-go\(.*\)$/\1github.com\/ionos-cloud\/sdk-go\/${major} ${version}/g" go.mod || exit 1

# remove backup file created by sed
rm -f go.mod.bak

# use locally installed core library
echo "replace github.com/ionos-cloud/sdk-go/${major} => ${core_lib_path}" >> go.mod || exit 1
go build . || exit 1
