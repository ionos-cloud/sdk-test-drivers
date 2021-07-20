package com.ionoscloud.models;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class Error {
    String message;
    HttpResponse apiResponse;
    List<String> stackTrace;
}
