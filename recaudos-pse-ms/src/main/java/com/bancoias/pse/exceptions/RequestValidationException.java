package com.bancoias.pse.exceptions;

import com.bancoias.pse.dtos.ErrorResponse.FieldErrorDetail;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
public class RequestValidationException extends ApiException {

    private final List<FieldErrorDetail> details;

    public RequestValidationException(String message, List<FieldErrorDetail> details) {
        super(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
        this.details = List.copyOf(details);
    }
}
