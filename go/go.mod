module github.com/ionos-cloud/ionos-cloud-test-driver-sdk-go

require (
	github.com/ionos-cloud/sdk-go/v6 v6.0.1
	github.com/mitchellh/mapstructure v1.3.3
)

go 1.13

replace github.com/ionos-cloud/sdk-go/v6 => /tmp/sdk-dummy
