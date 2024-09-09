#!/usr/bin/env bash
#
# {App} - used to replace com.ionoscloud with the provided package name(eg. com.ionoscloud.dbaaspostgres)
#

function usage() {
	echo "error: $1"
	echo
	echo "usage: build.sh <core_lib_path>"
	echo "example: build.sh com.ionoscloud.dbaaspostgres"
	exit 1
}


core_lib_path="${1}"
if [ "${core_lib_path}" = "" ]; then
	usage "path to core lib not specified"
fi

# replace core lib version
# NOTE: THIS RUNS ON LINUX ONLY - DOESN'T WORK ON OSX - on OSX the -i flag requires an extension name to save backups to
git reset --hard
find . -name "*.java" -exec sed -i "s/com.ionoscloud/${core_lib_path}/g" {} \; || exit 1
