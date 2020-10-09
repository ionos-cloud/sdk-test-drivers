package main

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	sdk "github.com/ionos-cloud/ionos-cloud-sdk-go/v5"
	"github.com/mitchellh/mapstructure"
	"net/http"
	"os"
	"reflect"
	"strings"
)

type Options struct {
	Username 	*string				`json:"username,omitempty"`
	Password 	*string				`json:"password,omitempty"`
	Timeout 	*int64				`json:"timeout,omitempty"`
}

type Input struct {
	Operation 	string 				`json:"operation,omitempty"`
	Params 		[]interface{}		`json:"params,omitempty"`
	Options 	*Options			`json:"options,omitempty"`

}

type HttpResponse struct {
	StatusCode	int					`json:"statusCode"`
	Headers		http.Header			`json:"headers"`
	Body		string				`json:"body"`
}

type Output struct {
	Error 			*string			`json:"error,omitempty"`
	HttpResponse	HttpResponse	`json:"httpResponse"`
	Result			interface{}		`json:"result"`
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

func computeMethodArgs(methodFromVal reflect.Value, params []interface{}, output *Output) []reflect.Value {

	/* skipping first arg, we now it to always be context */
	numArgs := methodFromVal.Type().NumIn()
	var args = make([]reflect.Value, numArgs)
	args[0] = reflect.ValueOf(context.TODO())

	if len(params) > numArgs - 1 {
		errorMsg := fmt.Sprintf("too many params; found %d, expected %d", len(params), numArgs - 1)
		output.Error = &errorMsg
		return args
	}

	/* initialize args */
	for j := 1; j < methodFromVal.Type().NumIn(); j ++ {
		argType := methodFromVal.Type().In(j)
		args[j] = reflect.Zero(argType)
	}

	for j := 0; j < len(params); j ++ {

		inputArg := params[j]
		if inputArg == nil {
			continue
		}
		arg := methodFromVal.Type().In(j + 1)
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

			/* map the params map to struct */
			if reflect.TypeOf(inputArg).Kind() != reflect.Map {
				errorMsg := fmt.Sprintf("param %d expected to be a map for type %s", j + 1, argTypeName)
				output.Error = &errorMsg
				break
			}

			if arg.Kind() == reflect.Ptr {
				tmpRes := reflect.New(arg).Interface()
				decoder, errDecode = getDecoder(&tmpRes)
				if errDecode != nil {
					errorMsg := errDecode.Error()
					output.Error = &errorMsg
					break
				}
				errDecode = decoder.Decode(inputArg)
				args[j + 1] = reflect.ValueOf(tmpRes)

			} else {
				tmpRes := reflect.Zero(arg).Interface()
				decoder, errDecode = getDecoder(&tmpRes)
				if errDecode != nil {
					errorMsg := errDecode.Error()
					output.Error = &errorMsg
					break
				}
				errDecode = decoder.Decode(inputArg)
				args[j + 1] = reflect.ValueOf(tmpRes)

			}

			if errDecode != nil {
				errorMsg := errDecode.Error()
				output.Error = &errorMsg
				break
			}

		} else {

			args[j + 1] = reflect.ValueOf(inputArg)
		}
	}

	return args
}

func callMethod(method reflect.Value, args []reflect.Value, output *Output) {
	reflectRes := method.Call(args)
	/* assuming we always have result, *ApiResponse, error */
	output.Result = reflectRes[0].Interface()
	apiResponse := reflectRes[1].Interface().(*sdk.APIResponse)
	callErr := reflectRes[2].Interface()
	if callErr != nil {
		errMessage := callErr.(error).Error()
		output.Error = &errMessage
	}
	if apiResponse != nil && apiResponse.Response != nil {
		output.HttpResponse = HttpResponse{
			StatusCode: apiResponse.StatusCode,
			Headers:    apiResponse.Header,
			Body:       string(apiResponse.Payload),
		}
	}
}

func callWaitForRequest(method reflect.Value, args []reflect.Value, output *Output) {
	reflectRes := method.Call(args)
	/* assuming we always have result, *ApiResponse, error */
	apiResponse := reflectRes[0].Interface().(*sdk.APIResponse)
	callErr := reflectRes[1].Interface()
	if callErr != nil {
		errMessage := callErr.(error).Error()
		output.Error = &errMessage
	}
	if apiResponse != nil && apiResponse.Response != nil {
		output.HttpResponse = HttpResponse{
			StatusCode: apiResponse.StatusCode,
			Headers:    apiResponse.Header,
			Body:       string(apiResponse.Payload),
		}
	}
}

func searchAndCallMethod(val reflect.Value, name string, params []interface{}, output *Output) bool {
	found := false
	method := val.MethodByName(name)
	if method.IsValid() {
		found = true
		args := computeMethodArgs(method, params, output)
		if output.Error == nil {
			if name == WaitForRequestMethodName {
				callWaitForRequest(method, args, output)
			} else {
				callMethod(method, args, output)
			}
		}
	}

	return found
}

func main() {

	reader := bufio.NewReader(os.Stdin)
	inputJson, _ := reader.ReadString('\n')

	/* debug
	inputJson := `
	{"operation":"datacentersGet","params":["f7794b79-496a-4d02-a1a8-bd664e7af440"]}
`
	 */

	output := Output{}

	input := Input{}
	if err := json.Unmarshal([]byte(inputJson), &input); err != nil {
		errorMessage := fmt.Sprintf("{\"error\": \"%s\"}", err.Error())
		output.Error = &errorMessage
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
		errorMessage := "missing operation"
		output.Error = &errorMessage
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
		errorMessage := fmt.Sprintf("operation %s not found", input.Operation)
		output.Error = &errorMessage
	}

	outputJson, _ := json.Marshal(output)
	fmt.Println(string(outputJson))

}
