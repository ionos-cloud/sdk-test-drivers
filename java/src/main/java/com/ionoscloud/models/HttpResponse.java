package com.ionoscloud.models;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class HttpResponse {
    Integer statusCode;
    Map<String, List<String>> headers;
    String body;
}
