package main

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	sdk "github.com/ionos-cloud/sdk-go/v6"
	"github.com/mitchellh/mapstructure"
	"net/http"
	"os"
	"reflect"
	"strings"
	"time"
)

type Options struct {
	Username 	*string				`json:"username,omitempty"`
	Password 	*string				`json:"password,omitempty"`
	Timeout 	*int64				`json:"timeout,omitempty"`
}

type InputParam struct {
	Name string						`json:"name,omitempty"`
	Value interface{}				`json:"value,omitempty"`
	Processed bool					`json:"-"`
}

type Input struct {
	Operation 	string 				`json:"operation,omitempty"`
	Params 		[]InputParam		`json:"params,omitempty"`
	Options 	*Options			`json:"options,omitempty"`

}

type HttpResponse struct {
	StatusCode	int					`json:"statusCode"`
	Headers		http.Header			`json:"headers"`
	Body		string				`json:"body"`
}

type Output struct {
	Error 			*ErrorStruct	`json:"error,omitempty"`
	HttpResponse	HttpResponse	`json:"httpResponse"`
	Result			interface{}		`json:"result"`
}

type ErrorStruct struct {
	Message string					`json:"message,omitempty"`
	ApiResponse *HttpResponse		`json:"apiResponse"`
}

const (
	WaitForRequestMethodName = "WaitForRequest"
)

func getDecoder(result interface{}) (*mapstructure.Decoder, error) {
	var decoderConfig = &mapstructure.DecoderConfig{
		ErrorUnused: true,
		Result: result,
	}
	return mapstructure.NewDecoder(decoderConfig)
}

func getParamsMap(params []InputParam) map[string]InputParam {
	var ret = make(map[string]InputParam)
	for _, param := range params {
		ret[param.Name] = param
	}

	return ret
}

func convertParamToArg(param interface{}, arg reflect.Type) (reflect.Value, error) {

	var ret reflect.Value

	argTypeName := ""
	var argKind reflect.Kind
	if arg.Kind() == reflect.Ptr {
		argKind = arg.Elem().Kind()
		argTypeName = arg.Elem().Name()
	} else {
		argKind = arg.Kind()
		argTypeName = arg.Name()
	}

	if argKind == reflect.Struct {

		var errDecode error
		var decoder *mapstructure.Decoder

		if argKind == reflect.TypeOf(time.Time{}).Kind() {
			var parsedTime time.Time
			parsedTime, errDecode = time.Parse(time.RFC3339, param.(string))
			if errDecode != nil {
				return reflect.ValueOf(nil), errors.New(fmt.Sprintf("could not parse time parameter %s: %s", argTypeName, errDecode))
			}
			ret = reflect.ValueOf(parsedTime)
			return ret, nil
		}
		if reflect.TypeOf(param).Kind() != reflect.Map {
			return reflect.ValueOf(nil), errors.New(fmt.Sprintf("param expected to be a map for type %s", argTypeName))
		}

		if arg.Kind() == reflect.Ptr {
			tmpRes := reflect.New(arg).Interface()
			decoder, errDecode = getDecoder(&tmpRes)
			if errDecode != nil {
				return ret, errDecode
			}
			errDecode = decoder.Decode(param)
			ret = reflect.ValueOf(tmpRes)

		} else {
			tmpRes := reflect.Zero(arg).Interface()
			decoder, errDecode = getDecoder(&tmpRes)
			if errDecode != nil {
				return ret, errDecode
			}
			errDecode = decoder.Decode(param)
			ret = reflect.ValueOf(tmpRes)

		}

		if errDecode != nil {
			return ret, errDecode
		}

	} else {
		paramKind := reflect.TypeOf(param).Kind()
		if paramKind == reflect.Float64 && argKind == reflect.Int32 {
			/* test runner sends a float64,  method signature is generated with int32
			 * honestly, this is as bad as it can get - I mean, really!? let's cramp 64 bits in, maybe they'll fit ... */
			ret = reflect.ValueOf(int32(param.(float64)))
		} else {
			if paramKind != argKind {
				return ret, errors.New(fmt.Sprintf("Needed %s arg but got %s", argKind, paramKind))
			}
			ret = reflect.ValueOf(param)
		}
	}

	return ret, nil
}

func computeMethodArgs(methodFromVal reflect.Value, params []InputParam, output *Output) []reflect.Value {

	numArgs := methodFromVal.Type().NumIn()
	var args = make([]reflect.Value, numArgs)

	/* skipping first arg, we now it to always be context */
	args[0] = reflect.ValueOf(context.TODO())

	if len(params) < numArgs - 1 {
		output.Error = &ErrorStruct{Message: fmt.Sprintf("too few params; found %d, expected %d", len(params), numArgs - 1)}
		return args
	}

	/* initialize args */
	for j := 1; j < methodFromVal.Type().NumIn(); j ++ {

		arg := methodFromVal.Type().In(j)
		args[j] = reflect.Zero(arg)

		inputArg := params[j-1].Value
		if inputArg == nil {
			continue
		}

		argReflectVal, err := convertParamToArg(inputArg, arg)
		if err != nil {
			output.Error = &ErrorStruct{Message: fmt.Sprintf("param #%d: %s", j - 1, err.Error())}
			return args
		}

		args[j] = argReflectVal

		params[j - 1].Processed = true
	}

	return args
}

func callMethod(name string, method reflect.Value, args []reflect.Value, params []InputParam, output *Output) {

	/* first call gets us an api object instance */
	objectRes := method.Call(args)

	if len(objectRes) == 0 {
		output.Error = &ErrorStruct{Message: fmt.Sprintf("Method %s didn't return anything", name)}
		return
	}

	/* we set any unprocessed params by calling the appropriate builder methods */
	for _, param := range params {
		if param.Processed {
			continue
		}

		/* find the method associated with this param */
		builderMethod := objectRes[0].MethodByName(strings.Title(param.Name))
		if builderMethod.IsValid() {
			reflectArg, err := convertParamToArg(param.Value, builderMethod.Type().In(0))
			if err != nil {
				output.Error = &ErrorStruct{Message: err.Error()}
				return
			}
			objectRes = builderMethod.Call([]reflect.Value{reflectArg})
			param.Processed = true
		}

		if !param.Processed {
			output.Error = &ErrorStruct{Message: fmt.Sprintf("operation %s: unknown parameter %s", name, param.Name)}
			return
		}
	}

	executeMethod := objectRes[0].MethodByName("Execute")
	if !executeMethod.IsValid() {
		output.Error = &ErrorStruct{Message: fmt.Sprintf("Execute() method not found when calling %s", name)}
		return
	}

	reflectRes := executeMethod.Call([]reflect.Value{})

	responseLength := len(reflectRes)
	var apiResponse *sdk.APIResponse
	if responseLength == 3 {
		output.Result = reflectRes[0].Interface()
		apiResponseVar := reflectRes[1].Interface()
		if apiResponseVar != nil {
			apiResponse = apiResponseVar.(*sdk.APIResponse)
		}
		callErr := reflectRes[2].Interface()
		if callErr != nil {
			output.Error = &ErrorStruct{Message: callErr.(error).Error()}
		}
	} else {
		apiResponseVar := reflectRes[0].Interface()
		if apiResponseVar != nil {
			apiResponse = apiResponseVar.(*sdk.APIResponse)
		}
		callErr := reflectRes[1].Interface()
		if callErr != nil {
			output.Error = &ErrorStruct{Message: callErr.(error).Error()}
		}
	}
	if apiResponse != nil && apiResponse.Response != nil {
		output.HttpResponse = HttpResponse{
			StatusCode: apiResponse.StatusCode,
			Headers:    apiResponse.Header,
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

	var username, password string

	if input.Options != nil && input.Options.Username != nil {
		username = *input.Options.Username
	} else {
		username = os.Getenv("IONOS_USERNAME")
	}

	if input.Options != nil && input.Options.Password != nil {
		password = *input.Options.Password
	} else {
		password = os.Getenv("IONOS_PASSWORD")
	}

	client := sdk.NewAPIClient(sdk.NewConfiguration(username, password, ""))

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
