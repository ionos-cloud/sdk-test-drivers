package main

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"reflect"
	"strings"
	"time"

	sdk "github.com/ionos-cloud/api-gateway"
	"github.com/mitchellh/mapstructure"
)

type Options struct {
	Username *string `json:"username,omitempty"`
	Password *string `json:"password,omitempty"`
	Token    *string `json:"token,omitempty"`
	Timeout  *int64  `json:"timeout,omitempty"`
}

type InputParam struct {
	Name      string      `json:"name,omitempty"`
	Value     interface{} `json:"value,omitempty"`
	Processed bool        `json:"-"`
}

type Input struct {
	Operation string       `json:"operation,omitempty"`
	Params    []InputParam `json:"params,omitempty"`
	Options   *Options     `json:"options,omitempty"`
}

type HttpResponse struct {
	StatusCode int         `json:"statusCode"`
	Headers    http.Header `json:"headers"`
	Body       string      `json:"body"`
}

type Output struct {
	Error        *ErrorStruct `json:"error,omitempty"`
	HttpResponse HttpResponse `json:"httpResponse"`
	Result       interface{}  `json:"result"`
}

type ErrorStruct struct {
	Message     string        `json:"message,omitempty"`
	ApiResponse *HttpResponse `json:"apiResponse"`
}

const (
	WaitForRequestMethodName = "WaitForRequest"
)

func getDecoder(result interface{}) (*mapstructure.Decoder, error) {
	var decoderConfig = &mapstructure.DecoderConfig{
		ErrorUnused: true,
		Result:      result,
		DecodeHook: mapstructure.ComposeDecodeHookFunc(
			ToTimeHookFunc()),
	}
	return mapstructure.NewDecoder(decoderConfig)
}

// ToTimeHookFunc - decodes ionosTime before it is parsed
func ToTimeHookFunc() mapstructure.DecodeHookFuncType {
	return func(
		f reflect.Type,
		t reflect.Type,
		data interface{}) (interface{}, error) {
		if t != reflect.TypeOf(&sdk.IonosTime{}) && t != reflect.TypeOf(sdk.IonosTime{}) {
			return data, nil
		}

		switch f.Kind() {
		case reflect.String:
			valTime, _ := time.Parse(time.RFC3339, data.(string))
			return sdk.IonosTime{Time: valTime}, nil
		case reflect.Float64:
			valTime := time.Unix(0, int64(data.(float64))*int64(time.Millisecond))
			return sdk.IonosTime{Time: valTime}, nil
		case reflect.Int64:
			valTime := time.Unix(0, data.(int64)*int64(time.Millisecond))
			return sdk.IonosTime{Time: valTime}, nil
		default:
			return data, nil
		}
		// Convert it by parsing
	}
}

func getParamsMap(params []InputParam) map[string]InputParam {
	var ret = make(map[string]InputParam)
	for _, param := range params {
		ret[param.Name] = param
	}

	return ret
}

func convertParamToArg(param interface{}, arg reflect.Type) (reflect.Value, error) {
	if arg.Kind() == reflect.Ptr {
		arg = arg.Elem()
	}

	switch arg.Kind() {
	case reflect.Struct:
		return convertToStruct(param, arg)
	case reflect.Int32:
		return convertToInt32(param)
	case reflect.String:
		return reflect.ValueOf(param), nil
	default:
		return reflect.ValueOf(param), nil
	}
}

func convertToStruct(param interface{}, arg reflect.Type) (reflect.Value, error) {
	if arg == reflect.TypeOf(time.Time{}) {
		return convertToTime(param)
	}

	if reflect.TypeOf(param).Kind() != reflect.Map {
		return reflect.Value{}, fmt.Errorf("param expected to be a map for type %s", arg.Name())
	}

	tmpRes := reflect.New(arg).Interface()
	decoder, err := getDecoder(&tmpRes)
	if err != nil {
		return reflect.Value{}, err
	}
	if err := decoder.Decode(param); err != nil {
		return reflect.Value{}, err
	}
	return reflect.ValueOf(tmpRes), nil
}

func convertToTime(param interface{}) (reflect.Value, error) {
	var parsedTime time.Time
	var err error

	switch v := param.(type) {
	case string:
		parsedTime, err = time.Parse(time.RFC3339, v)
	case float64:
		parsedTime = time.Unix(0, int64(v)*int64(time.Millisecond))
	case int64:
		parsedTime = time.Unix(0, v*int64(time.Millisecond))
	default:
		return reflect.Value{}, fmt.Errorf("could not parse time parameter: %v", param)
	}

	if err != nil {
		return reflect.Value{}, fmt.Errorf("could not parse time parameter: %v", err)
	}
	return reflect.ValueOf(parsedTime), nil
}

func convertToInt32(param interface{}) (reflect.Value, error) {
	if reflect.TypeOf(param).Kind() != reflect.Float64 {
		return reflect.Value{}, fmt.Errorf("needed int32 arg but got %s", reflect.TypeOf(param).Kind())
	}
	return reflect.ValueOf(int32(param.(float64))), nil
}

func computeMethodArgs(methodFromVal reflect.Value, params []InputParam, output *Output) []reflect.Value {

	numArgs := methodFromVal.Type().NumIn()
	var args = make([]reflect.Value, numArgs)

	/* skipping first arg, we now it to always be context */
	args[0] = reflect.ValueOf(context.TODO())

	if len(params) < numArgs-1 {
		output.Error = &ErrorStruct{Message: fmt.Sprintf("too few params; found %d, expected %d", len(params), numArgs-1)}
		return args
	}

	/* initialize args */
	for j := 1; j < methodFromVal.Type().NumIn(); j++ {

		arg := methodFromVal.Type().In(j)
		args[j] = reflect.Zero(arg)

		inputArg := params[j-1].Value
		if inputArg == nil {
			continue
		}

		argReflectVal, err := convertParamToArg(inputArg, arg)
		if err != nil {
			output.Error = &ErrorStruct{Message: fmt.Sprintf("param #%d: %s", j-1, err.Error())}
			return args
		}

		args[j] = argReflectVal

		params[j-1].Processed = true
	}

	return args
}

func callMethod(name string, method reflect.Value, args []reflect.Value, params []InputParam, output *Output) {
	// Call the method to get an API object instance
	objectRes := method.Call(args)
	if len(objectRes) == 0 {
		output.Error = &ErrorStruct{Message: fmt.Sprintf("Method %s didn't return anything", name)}
		return
	}

	// Process unprocessed parameters
	for _, param := range params {
		if param.Processed {
			continue
		}

		if param.Name == "filters" {
			if err := processFilters(param, objectRes[0], output); err != nil {
				output.Error = &ErrorStruct{Message: err.Error()}
				return
			}
		} else {
			if err := processParam(param, objectRes[0], output); err != nil {
				output.Error = &ErrorStruct{Message: err.Error()}
				return
			}
		}
	}

	// Call the Execute method
	executeMethod := objectRes[0].MethodByName("Execute")
	if !executeMethod.IsValid() {
		output.Error = &ErrorStruct{Message: fmt.Sprintf("Execute() method not found when calling %s", name)}
		return
	}

	handleExecuteResponse(executeMethod.Call([]reflect.Value{}), output)
}

func processFilters(param InputParam, object reflect.Value, output *Output) error {
	builderMethod := object.MethodByName("Filter")
	if !builderMethod.IsValid() || builderMethod.Type().NumIn() != 2 {
		return fmt.Errorf("no valid builder method for filter query param")
	}

	kv := reflect.ValueOf(param.Value)
	if kv.Kind() != reflect.Map {
		return fmt.Errorf("no valid value param for filter query param")
	}

	for _, key := range kv.MapKeys() {
		v := kv.MapIndex(key)
		reflectArgFilter, err := convertParamToArg(key.String(), builderMethod.Type().In(0))
		if err != nil {
			return err
		}
		reflectArgValue, err := convertParamToArg(v.Interface().(string), builderMethod.Type().In(1))
		if err != nil {
			return err
		}
		object = builderMethod.Call([]reflect.Value{reflectArgFilter, reflectArgValue})[0]
	}
	param.Processed = true
	return nil
}

func processParam(param InputParam, object reflect.Value, output *Output) error {
	builderMethod := object.MethodByName(strings.Title(param.Name))
	if !builderMethod.IsValid() {
		return fmt.Errorf("operation %s: unknown parameter %s", param.Name, param.Name)
	}

	if param.Value != nil {
		reflectArg, err := convertParamToArg(param.Value, builderMethod.Type().In(0))
		if err != nil {
			return err
		}
		object = builderMethod.Call([]reflect.Value{reflectArg})[0]
	}
	param.Processed = true
	return nil
}

func handleExecuteResponse(reflectRes []reflect.Value, output *Output) {
	responseLength := len(reflectRes)
	var apiResponse *sdk.APIResponse

	if responseLength == 3 {
		output.Result = reflectRes[0].Interface()
		apiResponse = reflectRes[1].Interface().(*sdk.APIResponse)
		if callErr := reflectRes[2].Interface(); callErr != nil {
			output.Error = &ErrorStruct{Message: callErr.(error).Error()}
		}
	} else {
		apiResponse = reflectRes[0].Interface().(*sdk.APIResponse)
		if callErr := reflectRes[1].Interface(); callErr != nil {
			output.Error = &ErrorStruct{Message: callErr.(error).Error()}
		}
	}

	if apiResponse != nil && apiResponse.Response != nil {
		lowerCaseHeader := make(http.Header)
		for key, value := range apiResponse.Header {
			lowerCaseHeader[strings.ToLower(key)] = value
		}
		output.HttpResponse = HttpResponse{
			StatusCode: apiResponse.StatusCode,
			Headers:    lowerCaseHeader,
			Body:       string(apiResponse.Payload),
		}
		if output.Error != nil {
			output.Error.ApiResponse = &output.HttpResponse
		}
	}
}

func callWaitForRequest(method reflect.Value, args []reflect.Value, output *Output) {
	reflectRes := method.Call(args)
	/* assuming we always have result, *ApiResponse, error */
	apiResponseVar := reflectRes[0].Interface()
	var apiResponse *sdk.APIResponse
	if apiResponseVar != nil {
		apiResponse = apiResponseVar.(*sdk.APIResponse)
	}
	callErr := reflectRes[1].Interface()
	if callErr != nil {
		output.Error = &ErrorStruct{Message: callErr.(error).Error()}
	}
	if apiResponse != nil && apiResponse.Response != nil {
		output.HttpResponse = HttpResponse{
			StatusCode: apiResponse.StatusCode,
			Headers:    apiResponse.Header,
			Body:       string(apiResponse.Payload),
		}
	}
}

func searchAndCallMethod(val reflect.Value, name string, params []InputParam, output *Output) bool {
	found := false
	method := val.MethodByName(name)
	if method.IsValid() {
		found = true
		args := computeMethodArgs(method, params, output)
		if output.Error == nil {
			if name == WaitForRequestMethodName {
				callWaitForRequest(method, args, output)
			} else {
				callMethod(name, method, args, params, output)
			}
		} else {
			output.Error.Message = fmt.Sprintf("operation %s: %s", name, output.Error.Message)
		}
	}

	return found
}

func main() {

	output := Output{}
	var inputJson string

	scanner := bufio.NewScanner(os.Stdin)
	for scanner.Scan() {
		inputJson += scanner.Text()
	}

	if err := scanner.Err(); err != nil {
		output.Error = &ErrorStruct{Message: fmt.Sprintf("input error: %s", err.Error())}
		os.Exit(1)
	}

	input := Input{}
	if err := json.Unmarshal([]byte(inputJson), &input); err != nil {
		output.Error = &ErrorStruct{Message: fmt.Sprintf("{\"error\": \"%s\"}", err.Error())}
		outputJson, _ := json.Marshal(output)
		fmt.Println(string(outputJson))
		os.Exit(1)
	}

	client := sdk.NewAPIClient(sdk.NewConfigurationFromEnv())

	operation := strings.Title(strings.TrimSpace(input.Operation))
	if operation == "" {
		output.Error = &ErrorStruct{Message: "missing operation"}
		outputJson, _ := json.Marshal(output)
		fmt.Println(string(outputJson))
		os.Exit(1)
	}

	reflectVal := reflect.ValueOf(client).Elem()
	reflectType := reflectVal.Type()

	found := false

	if operation == WaitForRequestMethodName {
		/* special case, WaitForRequest is implemented directly on the client */
		found = searchAndCallMethod(reflect.ValueOf(client), operation, input.Params, &output)
	} else {
		for i := 0; i < reflectType.NumField(); i++ {
			fieldName := reflectType.Field(i).Name
			if !strings.HasSuffix(fieldName, "Api") {
				continue
			}

			/* every Api member is a pointer to something */
			field := reflectVal.Field(i)

			/* search for the method */
			if found = searchAndCallMethod(field, operation, input.Params, &output); found {
				break
			}
		}
	}

	if !found {
		output.Error = &ErrorStruct{Message: fmt.Sprintf("operation %s not found", input.Operation)}
	}

	outputJson, _ := json.Marshal(output)
	fmt.Println(string(outputJson))

}
