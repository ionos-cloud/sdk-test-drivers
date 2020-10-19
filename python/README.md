# Installation

Suppose we have the following paths:
- the **python sdk** is located at _/ionos-cloud-sdk-python_
- the **python test driver** is located at _/sdk-test-drivers/python_
- the **tests** leave under _/sdk-resources/tests_

1. Copy the driver to the sdk folder
```
$ cp /sdk-test-drivers/python/ionos-cloud-test-driver-sdk-python.py /ionos-cloud-sdk-python/ionossdk/test-driver.py
```

2. Create a python virtual env in your python sdk
```
$ cd /ionos-cloud-sdk-python
$ virtualenv testenv
```

3. Install requirements for both the sdk AND the test driver
```
$ cd /ionos-cloud-sdk-python
$ source testenv/bin/activate

# install sdk requirements
$ pip install -r ./requirements.txt

# install test-driver requirements
$ pip install -r /sdk-test-drivers/python/requirements.txt
```

# Running the tests

**NOTE**: When running the tests you'll need to activate the python environment in the same shell you're running the tests in.

Suppose you have the tests under _/sdk-resources/tests_ .

## Method 1: via config

1. Configure the driver in the test runner
Place the following configuration in your _$HOME/.config/csdk-test-runner/config.json_ in the `drivers` section:
```
{
	"name": "python",
	"command": "python",
	"cwd": "/ionos-cloud-sdk-python",
	"args": [ "-m", "ionossdk.test-driver" ]
}
```

2. Run the tests
```
$ cd /ionos-cloud-sdk-python
$ source testenv/bin/activate
$ cd /sdk-resources/tests
$ csdk-test-runner run -d go ./all-tests.json
```


## Method 2: directly from the command line

```
$ cd /ionos-cloud-sdk-python
$ source testenv/bin/activate
$ cd /sdk-resources/tests
$ csdk-test-runner run ./all-tests.json --driver-path python --driver-cwd "/ionos-cloud-sdk-python" --driver-arg "-m" --driver-arg "ionossdk.test-driver"
```

**NOTE**: the test spec file is passed first, before all other arguments, when using multiple `--driver-arg` arguments. Otherwise the tool will fail with an error
saying no test file was specified. This happens because of using the same flag multiple times, which makes it impossible to know where the flags end and the plain arguments start.

## Troubleshooting

1. Make sure you have the correct paths everywhere. Double check your config file, the current working directory and the csdk-test-runner command line
2. Make sure you pass the test spec as the **first argument** when using method 2.
