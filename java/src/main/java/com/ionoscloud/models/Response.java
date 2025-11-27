package com.ionoscloud.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Response {
    HttpResponse httpResponse;
    Error error;
    Object result;

    public String toString() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {

            /* return the exception as an error */
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
