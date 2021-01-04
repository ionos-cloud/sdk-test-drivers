#!/usr/bin/env python3
#!/usr/bin/env python2

import sys
import json
from . import api as request_api_package
from .exceptions import ApiException
import ionoscloud
import re
import os

configuration = ionoscloud.Configuration(
    username=os.environ.get('IONOS_USERNAME'),
    password=os.environ.get('IONOS_PASSWORD')
)
api_client = ionoscloud.ApiClient(configuration)

input = sys.stdin.readlines()
testing_data = json.loads(input[0])

operation = testing_data['operation']
params = {re.sub(r'(?<!^)(?=[A-Z])', '_', p['name']).lower(): p['value'] for p in testing_data['params']}

def get_request_classes():
    classes = []
    for key in dir(request_api_package):
        structure = getattr(request_api_package, key)
        if isinstance(structure, type):
            classes.append(structure)
    return classes

def get_class_and_method(operation):
    classes = get_request_classes()
    for request_class in classes:
        method_list = {func:getattr(request_class, func) for func in dir(request_class) if callable(getattr(request_class, func))}
        for method_name, method in method_list.items():
            # camelcase to underscore for python standard
            if method_name == '%s_with_http_info' % re.sub(r'(?<!^)(?=[A-Z])', '_', operation).lower():
                return request_class, method
    return None, None

if operation == 'waitForRequest':
    request_id = re.search('/requests/([-A-Fa-f0-9]+)/', params['request']).group(1)
    api_client.wait_for_completion(request_id)
    sys.stdout.write(json.dumps({}))
    exit()

classApi, method = get_class_and_method(operation)
api_instance = classApi(api_client)
try:
    response = method(api_instance, response_type='object', **params)

    return_data = response[0]
    status_code = response[1]
    response_headers = dict(map(lambda x: (x[0], x[1].split(",")), dict(response[2]).items()))

    driver_output = {
      "result": return_data,
      "httpResponse": {
        "statusCode": status_code,
        "headers": response_headers,
        "body": return_data
      }
    }

    sys.stdout.write(json.dumps(driver_output))
except ApiException as e:
    exception_body = e.body
    exception_status = e.status
    exception_headers = e.headers
    sys.stdout.write(
        json.dumps(
            {
                "result": 'ApiException occured',
                "httpResponse": {
                    "statusCode": exception_status,
                    "headers": dict(exception_headers),
                    "body": exception_body
                },
                "error": exception_body
            }
        )
    )
except Exception as e:
    sys.stdout.write("General Exception occured: ")
    sys.stdout.write(str(e))
