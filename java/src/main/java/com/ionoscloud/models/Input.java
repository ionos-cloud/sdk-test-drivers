package com.ionoscloud.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Set;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Input {

    @NotBlank(message = "'operation' cannot be empty")
    String operation;

    @NotNull(message = "'params' cannot be empty")
    List<Param> params;

    public static Input get() throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line = reader.readLine();
        while (line != null) {
            stringBuilder.append(line);
            line = reader.readLine();
        }
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(stringBuilder.toString(), Input.class);
    }

    public void validate() throws IllegalArgumentException {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();

        Set<ConstraintViolation<Input>> violations = validator.validate(this);

        if (violations.isEmpty()) {
            /* input is valid */
            return;
        }

        StringBuilder errorBuilder = new StringBuilder("invalid input: ");
        for (var violation: violations) {
            errorBuilder.append(violation.getMessage());
            errorBuilder.append("; ");
        }

        throw new IllegalArgumentException(errorBuilder.toString());
    }

}
