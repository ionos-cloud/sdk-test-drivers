package com.ionos.sdk;
// Import classes:
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.ionossdk.ApiClient;
import com.ionossdk.ApiException;
import com.ionossdk.ApiResponse;
import com.ionossdk.Configuration;
import com.ionossdk.auth.*;

import com.ionossdk.api.DataCenterApi;

import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    final static String BASE_PATH = "https://api.ionos.com/cloudapi/v5";

    public static void main(String[] args) throws IOException, ClassCastException {

        /**
         * Initialize ApiClient
         */
        ApiClient apiClient = getApiClient();

        /**
         * GET Input Data
         */
        Map<String, String> map = getInputData();

        /**
         * SET operation and params
         */
        String operation = map.get("operation");
        Object paramObject = map.get("params");
        List<Map<String, Object>> params = (List<Map<String, Object>>) paramObject;

        if (operation.equals("waitForRequest")) {
            String requestId = getRequestIdFromUrl((String) params.get(0).get("value"));
            try {
                apiClient.waitForRequest(requestId, 20000, 4000, 2000);
                System.out.print("{\"error\": null}");
            } catch (ApiException e) {
                System.out.printf("{\"error\": \"%s\"}", e.getMessage());
            }
        } else {
            Set<Class<? extends Object>> classSet = getAccessibleMethods("com.ionossdk.api");

            for (Class apiClass : classSet) {
                for (Method method : apiClass.getMethods()) {
                    if (method.getName().equals(operation + "WithHttpInfo")) {
                        /**
                         * GET parameter list for api call
                         */
                        Object[] prm = getParapeterList(method, params);

                        /**
                         * Perform api call
                         */
                        performRequest(apiClient, apiClass, method, prm);
                    }
                }
            }
        }
    }

    public static void performRequest(ApiClient apiClient, Class apiClass, Method method, Object[] prm) throws IOException {
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        try {
            ApiResponse<Object> apiResponse =
                    (ApiResponse<Object>) method.invoke(
                            apiClass.getDeclaredConstructor(
                                    new Class[]{ApiClient.class}
                            ).newInstance(apiClient),
                            prm
                    );

            /**
             * set Location instead of location
             */
            Map<String, List<String>> headers = apiResponse.getHeaders();
            List<String> requestUrl = headers.get("location");
            headers.remove("location");
            headers.put("Location", requestUrl);

            String json = ow.writeValueAsString(
                new HashMap<String, Object>() {{
                    put("httpResponse", new HashMap<String, Object>() {{
                        put("statusCode", apiResponse.getStatusCode());
                        put("headers", headers);
                    }});
                    put("result", castEnumTypeToLowercaseString(apiResponse.getData()));
                    put("error", null);
                }}
            );
            System.out.print(json);
        } catch (Exception e) {
            Map httpResponse = new ObjectMapper().convertValue(e.getCause(), Map.class);
            if (httpResponse.containsKey("code")) {
                httpResponse.put("statusCode", httpResponse.get("code"));
            }
            String json = ow.writeValueAsString(
                new HashMap<String, Object>() {{
                    put("error", true);
                    put("httpResponse", httpResponse);
                    put("message", e.getCause().getMessage());
                }}
            );
            System.out.print(json);
        }
    }

    public static Object castEnumTypeToLowercaseString(Object body) {
        ObjectMapper oMapper = new ObjectMapper();
        Map<String, Object> bodyAsMap = oMapper.convertValue(body, Map.class);
        if (bodyAsMap == null) {
            return body;
        }
        for (Object key : bodyAsMap.keySet()) {
            if (key.equals("type")) {
                bodyAsMap.put("type", bodyAsMap.get("type").toString().toLowerCase());
            }
        }
        return bodyAsMap;
    }

    public static String getRequestIdFromUrl(String url) {
        Pattern p = Pattern.compile("/([-A-Fa-f0-9]+)/");
        Matcher m = p.matcher(url);
        while(m.find()) {
            return m.group().split("/")[1];
        }
        return null;
    }

    public static Object[] getParapeterList(Method method, List<Map<String, Object>> params) {

        Class[] parameterTypes = method.getParameterTypes();
        List<Object> paramList = new ArrayList<>();

        List mandatoryDefaultParameters = new ArrayList<Object>(){{
            add(true); // pretty
            add(1); // depth
            add(1); // contract number
            add(0); // offset
            add(100); // limit
        }};


        ObjectMapper om = new ObjectMapper();

        for (int i=0; i<parameterTypes.length; i++) {
            /**
             * add parameters from test suite
             * then default mandatory parameters
             */
            if (params.size() > i) {
                Map<String, Object> param = params.get(i);
                paramList.add(om.convertValue(param.get("value"), parameterTypes[i]));
            } else {
                paramList.add(mandatoryDefaultParameters.get(0));
                mandatoryDefaultParameters.remove(0);
            }
        }

        return paramList.toArray();
    }

    public static ApiClient getApiClient() {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath(BASE_PATH);

        // Configure HTTP basic authorization: Basic Authentication
        HttpBasicAuth basicAuthentication = (HttpBasicAuth) defaultClient.getAuthentication("Basic Authentication");
        basicAuthentication.setUsername(System.getenv("IONOS_USERNAME"));
        basicAuthentication.setPassword(System.getenv("IONOS_PASSWORD"));

        return defaultClient;
    }

    public static Map<String, String> getInputData() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String stdin = reader.readLine();
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(stdin, Map.class);
    }

    public static Set<Class<? extends Object>> getAccessibleMethods(String basePackageName) {
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setScanners(new SubTypesScanner(false /* don't exclude Object.class */), new ResourcesScanner())
                .setUrls(ClasspathHelper.forPackage(basePackageName))
                .filterInputsBy(new FilterBuilder().include(FilterBuilder.prefix(basePackageName))));

        return reflections.getSubTypesOf(Object.class);
    }
}
