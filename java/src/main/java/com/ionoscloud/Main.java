package com.ionoscloud;

import com.ionoscloud.models.Error;
import com.ionoscloud.models.HttpResponse;
import com.ionoscloud.models.Input;
import com.ionoscloud.models.Response;
import com.ionoscloud.services.SdkService;

import java.util.Arrays;
import java.util.stream.Collectors;


public class Main {


    public static void main(String[] args) {

        /* Initialize ApiClient */
        SdkService sdkService = new SdkService();

        try {

            /* read and validate input */
            Input input = Input.get();
            input.validate();

            /* run sdk method and print result */
            System.out.println(sdkService.run(input).toString());

        } catch (Throwable e) {

            Error.ErrorBuilder errorBuilder =
                    Error
                    .builder()
                    .message(e.getMessage())
                    .stackTrace(Arrays.stream(e.getStackTrace()).map(
                            element -> element.getClassName() + ":" + element.getMethodName() + "():" + element.getLineNumber()
                    ).collect(Collectors.toList()));

            if (e instanceof ApiException) {
                ApiException apiEx = (ApiException) e;
                errorBuilder.apiResponse(
                        HttpResponse
                                .builder()
                                .statusCode(apiEx.getCode())
                                .headers(apiEx.getResponseHeaders())
                                .body(apiEx.getResponseBody())
                                .build()
                );
            }
            System.out.println(
                    Response
                            .builder()
                            .error(errorBuilder.build())
                            .build()
                            .toString()
            );
        }


    }

}
