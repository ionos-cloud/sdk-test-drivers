#!/usr/bin/env bash
#
# {App} - {purpose, description}
#

package_name="${1:-"ionoscloud"}"
sed -i "s/ionoscloud/${package_name}/g" ionos-cloud-test-driver-sdk-python.py || exit 1
