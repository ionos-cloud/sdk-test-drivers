#!/usr/bin/env ruby

$LOAD_PATH << '.'

require 'json'

{
  'ionoscloud' => 'Ionoscloud',
  'ionoscloud-autoscaling' => 'IonoscloudAutoscaling',
  'ionoscloud-dbaas-postgres' => 'IonoscloudDbaasPostgres',
}.each do |module_name, namespace|
  begin
    require module_name
    Ionoscloud = Object.const_get(namespace)
    break
  rescue LoadError
  end
end

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

begin
  if operation == 'waitForRequest'
    request_id = testing_data['params'][0]['value'].scan(%r{/requests/(\b[0-9a-f]{8}\b-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-\b[0-9a-f]{12}\b)}).last.first
    api_client.wait_for_completion(request_id)
    puts JSON[{}]
  else
    method_name = "#{underscore_string(operation)}_with_http_info"

    cls = get_class method_name

    special_params_names = %w[pretty depth XContractNumber contractNumber offset limit start end filters orderBy maxResults]

    normal_params, special_params = (testing_data['params'].nil? ? [] : testing_data['params']).partition do |el|
      special_params_names.none? { |special_param_name| special_param_name == el['name'] }
    end

    filters_index = special_params.index { |param| param['name'] == 'filters' }
    max_results_index = special_params.index { |param| param['name'] == 'maxResults' }

    query_params = {
      'name' => 'query_params',
      'value' => {},
    }
    unless filters_index.nil?
      special_params[filters_index]['value'].keys.each { |key| query_params['value'][('filter.' + key).to_sym] = special_params[filters_index]['value'][key] }
      special_params.delete_at(filters_index)
    end

    ['orderBy', 'maxResults'].each do |query_param_name|
      index = special_params.index { |param| param['name'] == query_param_name }
      unless index.nil?
        query_params['value'][query_param_name.to_sym] = special_params[index]['value']
        special_params.delete_at(index)
      end
    end

    special_params.push(query_params)

    normal_params = normal_params.map { |el| el['value'] }

    special_params = special_params.each_with_object({}) do |current, total|
      total[current['name'].to_sym] = current['value']
    end

    response, status_code, headers = cls.new(api_client).public_send(
      method_name.to_sym,
      *normal_params,
      special_params,
    )

    begin
      response = response.to_hash
    rescue NoMethodError
    end

    headers = headers.transform_values do |value|
      value.split(',', -1)
    end

    puts JSON[{
      'result' => response,
      'httpResponse' => {
        'statusCode' => status_code,
        'headers' => headers.transform_keys(&:downcase),
        'body' => response,
      }
    }]
  end
rescue Ionoscloud::ApiError => e
  puts JSON[{
    'result' => 'ApiException occured',
    'httpResponse' => {
      'statusCode' => e.code,
      'headers' => e.response_headers,
      'body' => JSON.parse(e.response_body),
    },
    'error' => JSON.parse(e.response_body),
  }]
rescue StandardError => e
  puts JSON[{
    'result' => 'GeneralException occured',
    'httpResponse' => {
      'statusCode' => nil,
      'headers' => nil,
      'body' => nil,
    },
    'error' => e.inspect,
  }]
end
