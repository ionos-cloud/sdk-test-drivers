package com.ionoscloud.services;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.Gson;
import com.ionoscloud.ApiClient;
import com.ionoscloud.ApiException;
import com.ionoscloud.ApiResponse;
import com.ionoscloud.Configuration;
import com.ionoscloud.auth.HttpBasicAuth;
import com.ionoscloud.auth.HttpBearerAuth;
import com.ionoscloud.auth.ApiKeyAuth;
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
import org.threeten.bp.OffsetDateTime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class SdkService {

    protected ApiClient apiClient;

    private final Logger log = Logger.getLogger("sdk-service");

    private static final String BASE_PACKAGE_NAME = "com.ionoscloud.api";
    private static final String CLOUDAPI_BASIC_AUTH = "Basic Authentication";
    private static final String CLOUDAPI_TOKEN_AUTH = "Token Authentication";
    private static final String DBAAS_BASIC_AUTH = "basicAuth";
    private static final String DBAAS_TOKEN_AUTH = "tokenAuth";
    private static final String WAIT_FOR_REQUEST = "waitForRequest";
    public SdkService() {

        String username = System.getenv(Configuration.IONOS_USERNAME_ENV_VAR);
        String password = System.getenv(Configuration.IONOS_PASSWORD_ENV_VAR);
        String token = System.getenv(Configuration.IONOS_TOKEN_ENV_VAR);

        if (
            (
                (username == null || username.trim().length() == 0) ||
                (password == null || password.trim().length() == 0)
            ) && (token == null || token.trim().length() == 0)
         ) {
            throw new IllegalArgumentException(
                Configuration.IONOS_USERNAME_ENV_VAR + "and" +
                Configuration.IONOS_PASSWORD_ENV_VAR + "or" +
                Configuration.IONOS_TOKEN_ENV_VAR + " env vars not set");
        }

        this.apiClient = Configuration.getDefaultApiClient();

        if (token != null && token.trim().length() != 0) {
          String authToken = getAuthToken(token);
        } else {
            // Configure HTTP basic authorization: Basic Authentication
            HttpBasicAuth basicAuthentication = (HttpBasicAuth) this.apiClient.getAuthentication(CLOUDAPI_BASIC_AUTH);
    
            //for DBaaS, we have 'basicAuth', not "Basic Authentication"
            if (basicAuthentication == null) {
               basicAuthentication = (HttpBasicAuth) this.apiClient.getAuthentication(DBAAS_BASIC_AUTH);
            }
            basicAuthentication.setUsername(username);
            basicAuthentication.setPassword(password);
        }
    }

public String getAuthToken(String token) {
    if (token != null && !token.trim().isEmpty()) {
        // Try CLOUDAPI_TOKEN_AUTH first, then fallback to DBAAS_TOKEN_AUTH
        String authToken = setAuthToken(token, CLOUDAPI_TOKEN_AUTH);
        if (authToken == null) {
            authToken = setAuthToken(token, DBAAS_TOKEN_AUTH);
        }
        return authToken;
    }
    return null; // Or handle as appropriate
}

private String setAuthToken(String token, String authType) {
    Object auth = this.apiClient.getAuthentication(authType);
    
    if (auth instanceof ApiKeyAuth) {
        ApiKeyAuth apiKeyAuth = (ApiKeyAuth) auth;
        apiKeyAuth.setApiKey(token);
        apiKeyAuth.setApiKeyPrefix("Bearer");
        return apiKeyAuth.getApiKey();
    }

    if (auth instanceof HttpBearerAuth) {
        HttpBearerAuth bearerAuth = (HttpBearerAuth) auth;
        bearerAuth.setBearerToken(token);
        return bearerAuth.getBearerToken();
    }

    return null;
}

    
    public Response run(Input input)
            throws Throwable {

        String operation  = input.getOperation();
        List<Param> params = input.getParams();

        if (operation.equals(WAIT_FOR_REQUEST)) {
            return this.waitForRequest(input);
        } else {

            Set<Class<?>> classSet = getAccessibleMethods();

            for (Class<?> apiClass : classSet) {
                for (Method method : apiClass.getMethods()) {
                    if (method.getName().equals(operation + "WithHttpInfo")) {
                        /* get parameter list for api call */
                        Object[] prm;

                        if (params != null) {
                            prm = getParameterList(
                                method, params.stream()
                                        .collect(Collectors.toMap(
                                                Param::getName, Param::getValue
                                        ))
                            );
                        } else {
                            prm = getParameterList(method, new HashMap<String, Object>());
                        }

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
        objectMapper.registerModule(new JavaTimeModule());

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
            Throwable target = e.getTargetException();
            if (target instanceof ApiException) {
                ApiException apiEx = (ApiException)target;
                HttpResponse httpResponse = HttpResponse
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


        Gson gson = new Gson();

        /* We're still using gson here to serialize the structure returned by the sdk because
         * the sdk is generated using gson and when serializing via jackson, the json adapter is not used
         * and enums are serialized in caps instead of using the lowercase values, which is not good.
         * However, this doesn't hurt since we were actually interested in using jackson to parse the input
         * payload that gets deserialized into sdk structures and use setters and getters there.
         */
        String apiResponseData = gson.toJsonTree(apiResponse.getData()).toString();
        Object resultObj;
        //we can also get an array here - "[]" for infosVersionsGet, or a map "{"data":{}}"
        if (apiResponseData.startsWith("[")) {
            resultObj = objectMapper.readValue(apiResponseData, List.class);
        } else {
            resultObj = objectMapper.readValue(apiResponseData, HashMap.class);
        }

        return Response
                .builder()
                .result(resultObj)
                // .result(apiResponse.getData()) - we could've done this if jackson was used by openapi generator
                .httpResponse(
                        HttpResponse
                                .builder()
                                .body(objectMapper.writeValueAsString(apiResponse.getData()))
                                .headers(apiResponse.getHeaders())
                                .statusCode(apiResponse.getStatusCode())
                                .build()
                )
                .build();
    }


    protected Response waitForRequest(Input input) throws Throwable {
        Param requestParam = input.getParams().stream()
                .filter(n -> n.getName().equals("request"))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("'request' parameter is missing"));

        String requestId = getRequestIdFromUrl((String)requestParam.getValue());

        log.info(WAIT_FOR_REQUEST + " requestId: " + requestId);
        //we need to do this because for DBaaS, waitForRequest does not exist
        for (Method method : ApiClient.class.getDeclaredMethods()) {
            if (method.getName().equals(WAIT_FOR_REQUEST)) {
                 try {
                    method.invoke(null, requestId, 80000, 4000, 2000);
                 }
                 catch (InvocationTargetException e) {
                    Throwable target = e.getTargetException();
                    if (target instanceof ApiException) {
                        ApiException apiEx = (ApiException)target;
                        HttpResponse httpResponse = HttpResponse
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
            }
        }


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

        Gson gson = new Gson();

        for (String parameterName : methodParameterNames) {
            if (testParams.containsKey(parameterName) || testParams.containsKey(StringUtils.capitalize(parameterName))) {
                Object testParameter = testParams.get(parameterName) == null ?
                        testParams.get(StringUtils.capitalize(parameterName)) : testParams.get(parameterName);
                Class<?> parameterType = methodParameterTypesMap.get(parameterName) == null ?
                        methodParameterTypesMap.get(StringUtils.capitalize(parameterName)) :
                        methodParameterTypesMap.get(parameterName);


                if (testParameter instanceof Map || testParameter instanceof List) {
                    paramList.add(gson.fromJson(gson.toJson(testParameter), parameterType));
                } else if (parameterType.getName().contentEquals(OffsetDateTime.class.getName()) || (parameterType.getName().contentEquals(UUID.class.getName()))) {
                    paramList.add(gson.fromJson(gson.toJson(testParameter), parameterType));
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
        String[] parts = url.split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("invalid request URL: " + url);
        }
        return parts[parts.length - 2];
    }

}
