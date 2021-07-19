package com.ionoscloud.services;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ionoscloud.ApiClient;
import com.ionoscloud.ApiException;
import com.ionoscloud.ApiResponse;
import com.ionoscloud.Configuration;
import com.ionoscloud.auth.HttpBasicAuth;
import com.ionoscloud.models.*;
import com.ionoscloud.models.Error;
import com.thoughtworks.paranamer.AnnotationParanamer;
import com.thoughtworks.paranamer.BytecodeReadingParanamer;
import com.thoughtworks.paranamer.CachingParanamer;
import com.thoughtworks.paranamer.Paranamer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SdkService {

    protected ApiClient apiClient;

    private final Logger log = Logger.getLogger("sdk-service");

    private static final String BASE_PACKAGE_NAME = "com.ionoscloud.api";

    public SdkService() {

        var username = System.getenv("IONOS_USERNAME");

        if (username == null || username.trim().length() == 0) {
            throw new IllegalArgumentException("IONOS_USERNAME env var not set");
        }

        var password = System.getenv("IONOS_PASSWORD");

        if (password == null || password.trim().length() == 0) {
            throw new IllegalArgumentException("IONOS_PASSWORD env var not set");
        }

        this.apiClient = Configuration.getDefaultApiClient();

        // Configure HTTP basic authorization: Basic Authentication
        HttpBasicAuth basicAuthentication = (HttpBasicAuth) this.apiClient.getAuthentication("Basic Authentication");
        basicAuthentication.setUsername(username);
        basicAuthentication.setPassword(password);
    }

    public Response run(Input input)
            throws Throwable {
        var operation  = input.getOperation();
        var params = input.getParams();


        if (operation.equals("waitForRequest")) {
            return this.waitForRequest(input);
        } else {

            Set<Class<?>> classSet = getAccessibleMethods();

            for (Class<?> apiClass : classSet) {
                for (Method method : apiClass.getMethods()) {
                    if (method.getName().equals(operation + "WithHttpInfo")) {
                        /* get parameter list for api call */
                        Object[] prm = getParameterList(
                                method, params.stream()
                                        .collect(Collectors.toMap(
                                                Param::getName, Param::getValue
                                        ))
                        );

                        log.info("found method " + operation + "WithHttpInfo() in class " + apiClass.getName());

                        return performRequest(apiClient, apiClass, method, prm);
                    }
                }
            }
        }

        return Response
                .builder()
                .error(
                        Error
                                .builder()
                                .message("method " + operation + " not found")
                                .build()
                )
                .build();
    }

    public Response performRequest(ApiClient apiClient, Class<?> apiClass, Method method, Object[] prm)
            throws Throwable {

        ObjectMapper objectMapper = new ObjectMapper();

        log.info("call params: " + objectMapper.writeValueAsString(prm));

        Object result;

        try {
            result = method.invoke(
                    apiClass
                            .getDeclaredConstructor(ApiClient.class)
                            .newInstance(apiClient),
                    prm
            );

            if (!(result instanceof ApiResponse)) {
                throw new IllegalAccessException("method did not return an ApiResponse object");
            }

        } catch (InvocationTargetException e) {
            var target = e.getTargetException();
            if (target instanceof ApiException) {
                var apiEx = (ApiException)target;
                var httpResponse = HttpResponse
                        .builder()
                        .body(apiEx.getResponseBody())
                        .headers(apiEx.getResponseHeaders())
                        .statusCode(apiEx.getCode())
                        .build();
                return Response
                        .builder()
                        .result(apiEx.getResponseBody())
                        .error(
                                Error
                                        .builder()
                                        .message(apiEx.getMessage())
                                        .apiResponse(httpResponse)
                                        .build()
                        )
                        .httpResponse(httpResponse)
                        .build();
            } else {
                throw target;
            }
        }


        ApiResponse<?> apiResponse = (ApiResponse<?>) result;

        Map<String, List<String>> headers = apiResponse.getHeaders();
        Map<String, List<String>> headersObject = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            headersObject.put(
                    WordUtils
                            .capitalize(entry.getKey().replace('-', ' '))
                            .replace(' ', '-'),
                    entry.getValue()
            );
        }


        return Response
                .builder()
                .result(apiResponse.getData())
                .httpResponse(
                        HttpResponse
                                .builder()
                                .body(objectMapper.writeValueAsString(apiResponse.getData()))
                                .headers(headersObject)
                                .statusCode(apiResponse.getStatusCode())
                                .build()
                )
                .build();
    }


    protected Response waitForRequest(Input input) throws ApiException {
        Param requestParam = input.getParams().stream()
                .filter(n -> n.getName().equals("request"))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("'request' parameter is missing"));

        String requestId = getRequestIdFromUrl((String)requestParam.getValue());

        log.info("waitForRequest: " + requestId);

        ApiClient.waitForRequest(requestId, 80000, 4000, 2000);

        return Response.builder().build();
    }

    public static Object[] getParameterList(Method method, Map<String, Object> testParams) {
        Paranamer info = new CachingParanamer(new AnnotationParanamer(new BytecodeReadingParanamer()));
        String[] methodParameterNames = info.lookupParameterNames(method);
        Class<?>[] methodParameterTypes = method.getParameterTypes();
        Map<String, Class<?>> methodParameterTypesMap = new HashMap<>();

        int i = 0;
        for (String pn : methodParameterNames) {
            methodParameterTypesMap.put(pn, methodParameterTypes[i]);
            i++;
        }

        List<Object> paramList = new ArrayList<>();

        var mapper = new ObjectMapper();
        mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.NONE)
                .withGetterVisibility(JsonAutoDetect.Visibility.ANY)
                .withSetterVisibility(JsonAutoDetect.Visibility.ANY)
                .withCreatorVisibility(JsonAutoDetect.Visibility.ANY));

        for (String parameterName : methodParameterNames) {
            if (testParams.containsKey(parameterName) || testParams.containsKey(StringUtils.capitalize(parameterName))) {
                Object testParameter = testParams.get(parameterName) == null ?
                        testParams.get(StringUtils.capitalize(parameterName)) : testParams.get(parameterName);
                Class<?> parameterType = methodParameterTypesMap.get(parameterName) == null ?
                        methodParameterTypesMap.get(StringUtils.capitalize(parameterName)) :
                        methodParameterTypesMap.get(parameterName);


                if (testParameter instanceof Map || testParameter instanceof List) {
                    paramList.add(mapper.convertValue(testParameter, parameterType));
                } else {
                    paramList.add(testParameter);
                }

            } else {
                paramList.add(null);
            }
        }

        return paramList.toArray();
    }


    protected Set<Class<?>> getAccessibleMethods() {
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setScanners(new SubTypesScanner(false /* don't exclude Object.class */), new ResourcesScanner())
                .setUrls(ClasspathHelper.forPackage(BASE_PACKAGE_NAME))
                .filterInputsBy(new FilterBuilder().include(FilterBuilder.prefix(BASE_PACKAGE_NAME))));

        return reflections.getSubTypesOf(Object.class);
    }

    protected String getRequestIdFromUrl(String url) {

        /* we assume a request url is of the form https://api.host/REQUESTID/status */
        var parts = url.split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("invalid request URL: " + url);
        }
        return parts[parts.length - 2];
    }

}
