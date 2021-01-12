package com.ionos.sdk;
// Import classes:
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.ionoscloud.ApiClient;
import com.ionoscloud.ApiException;
import com.ionoscloud.ApiResponse;
import com.ionoscloud.Configuration;
import com.ionoscloud.auth.*;
import com.ionoscloud.model.Type;

import com.ionoscloud.model.Volume;
import com.thoughtworks.paranamer.AnnotationParanamer;
import com.thoughtworks.paranamer.BytecodeReadingParanamer;
import com.thoughtworks.paranamer.CachingParanamer;
import com.thoughtworks.paranamer.Paranamer;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.text.WordUtils;
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
import java.util.stream.Collectors;

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
        List<Map<String, Object>> paramList = (List<Map<String, Object>>) paramObject;

        Map<String, Object> params = paramList.stream()
                .collect(Collectors.toMap(s -> (String) s.get("name"), s -> s.get("value")));

        if (operation.equals("waitForRequest")) {
            String requestId = getRequestIdFromUrl((String) params.get("request"));
            try {
                apiClient.waitForRequest(requestId, 80000, 4000, 2000);
                System.out.print("{\"error\": null}");
            } catch (ApiException e) {
                System.out.printf("{\"error\": \"%s\"}", e.getMessage());
            }
        } else {
            Set<Class<? extends Object>> classSet = getAccessibleMethods("com.ionoscloud.api");

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

            Map<String, List<String>> headers = apiResponse.getHeaders();
            Map<String, List<String>> headersObject = new HashMap<>();
            for (String header : headers.keySet()) {
                headersObject.put(
                        WordUtils
                            .capitalize(header.replace('-', ' '))
                            .replace(' ', '-'),
                        headers.get(header)
                );
            }

            String json = ow.writeValueAsString(
                new HashMap<String, Object>() {{
                    put("httpResponse", new HashMap<String, Object>() {{
                        put("statusCode", apiResponse.getStatusCode());
                        put("headers", headersObject);
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
                bodyAsMap.put("type", Type.valueOf(bodyAsMap.get("type").toString()).getValue());
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

    public static Object[] getParapeterList(Method method, Map<String, Object> testParams) {
        Paranamer info = new CachingParanamer(new AnnotationParanamer(new BytecodeReadingParanamer()));
        String[] methodParameterNames = info.lookupParameterNames(method);
        Class[] methodParameterTypes = method.getParameterTypes();
        Map<String, Class> methodParameterTypesMap = new HashMap<>();

        int i = 0;
        for (String pn : methodParameterNames) {
            methodParameterTypesMap.put(pn, methodParameterTypes[i]);
            i++;
        }

        List<Object> paramList = new ArrayList<>();

        ObjectMapper om = new ObjectMapper();


        for (String parameterName : methodParameterNames) {
            if (testParams.containsKey(parameterName) || testParams.containsKey(StringUtils.capitalize(parameterName))) {
                paramList.add(
                    om.convertValue(
                        testParams.get(parameterName) == null ?
                            testParams.get(StringUtils.capitalize(parameterName)) : testParams.get(parameterName),
                        methodParameterTypesMap.get(parameterName) == null ?
                            methodParameterTypesMap.get(StringUtils.capitalize(parameterName)) : methodParameterTypesMap.get(parameterName)
                    )
                );
            } else {
                paramList.add(null);
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
