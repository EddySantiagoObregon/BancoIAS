package com.bancoias.pse.validators;

import com.bancoias.pse.dtos.ErrorResponse.FieldErrorDetail;
import com.bancoias.pse.exceptions.RequestValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Con router functions no existe @Valid, así que la validación de Bean Validation se invoca aquí
 * y se integra al pipeline como un Mono.
 */
@Component
@RequiredArgsConstructor
public class RequestValidator {

    private final Validator validator;

    public <T> Mono<T> validate(T body) {
        Set<ConstraintViolation<T>> violations = validator.validate(body);
        if (violations.isEmpty()) {
            return Mono.just(body);
        }
        List<FieldErrorDetail> details = violations.stream()
                .map(v -> new FieldErrorDetail(v.getPropertyPath().toString(), v.getMessage()))
                .sorted(Comparator.comparing(FieldErrorDetail::field))
                .toList();
        return Mono.error(new RequestValidationException("La solicitud tiene campos inválidos", details));
    }

    public int parseLimit(String rawLimit, int defaultLimit, int maxLimit) {
        if (rawLimit == null || rawLimit.isBlank()) {
            return defaultLimit;
        }
        try {
            int limit = Integer.parseInt(rawLimit.trim());
            if (limit < 1 || limit > maxLimit) {
                throw invalidLimit(maxLimit);
            }
            return limit;
        } catch (NumberFormatException e) {
            throw invalidLimit(maxLimit);
        }
    }

    private RequestValidationException invalidLimit(int maxLimit) {
        return new RequestValidationException("Parámetro inválido",
                List.of(new FieldErrorDetail("limit", "Debe ser un entero entre 1 y " + maxLimit)));
    }
}
