#!/usr/bin/env ruby

$LOAD_PATH << '.'

require 'json'
require 'ionoscloud'

config = Ionoscloud::Configuration.new

config.username = ENV['IONOS_USERNAME']
config.password = ENV['IONOS_PASSWORD']

api_client = Ionoscloud::ApiClient.new(config)

def underscore_string(str)
  str.gsub(/::/, '/')
     .gsub(/([A-Z]+)([A-Z][a-z])/, '\1_\2')
     .gsub(/([a-z\d])([A-Z])/, '\1_\2')
     .tr('-', '_')
     .downcase
end

def get_class(operation)
  Ionoscloud.constants.select do |c|
    Ionoscloud.const_get(c).is_a? Class and Ionoscloud.const_get(c).name.end_with? 'Api'
  end.each do |class_symbol|
    class_name = class_symbol.to_s
    methods = Ionoscloud.const_get(class_name).new.methods - Object.methods
    return Ionoscloud.const_get(class_name) if methods.map(&:to_s).include? operation
  end
  nil
end

input = gets.chomp
testing_data = JSON.parse(input.gsub('\"', '"'))

operation = testing_data['operation']

if operation == 'waitForRequest'
  request_id = testing_data['params'][0]['value'].scan(%r{/requests/(\b[0-9a-f]{8}\b-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-\b[0-9a-f]{12}\b)}).last.first

  api_client.wait_for_completion(request_id)
  puts JSON[{}]
  exit

else
  method_name = underscore_string(operation) + '_with_http_info'

  cls = get_class method_name

  special_params_names = %w[pretty depth contractNumber]

  normal_params, special_params = testing_data['params'].partition do |el|
    special_params_names.none? do |special_param_name|
      special_param_name == el['name']
    end
  end

  normal_params = normal_params.map { |el| el['value'] }

  special_params = special_params.each_with_object({}) do |current, total|
    total[current['name']] = current['value']
  end

  special_params[:debug_auth_names] = ['Basic Authentication']

  output = ''
  begin
    response, status_code, headers = cls.new(api_client).public_send(
      method_name.to_sym,
      *normal_params,
      special_params
    )

    response = response.to_hash unless response.nil?

    headers = headers.transform_values do |value|
      value.split(',', -1)
    end

    output = JSON[{
      'result' => response,
      'httpResponse' => {
        'statusCode' => status_code,
        'headers' => headers,
        'body' => response
      }
    }]
  rescue Ionoscloud::ApiError => e
    exception_body = e.response_body
    exception_status = e.code
    exception_headers = e.response_headers

    output = JSON[{
      'result' => 'ApiException occured',
      'httpResponse' => {
        'statusCode' => exception_status,
        'headers' => exception_headers,
        'body' => JSON.parse(exception_body)
      },
      'error' => JSON.parse(exception_body)
    }]
  rescue StandardError => e
    output = 'General Exception occured: ' + e.inspect
  end

  puts output
end
