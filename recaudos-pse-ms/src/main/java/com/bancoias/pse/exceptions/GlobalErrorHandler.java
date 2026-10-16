package com.bancoias.pse.exceptions;

import com.bancoias.pse.dtos.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Las router functions no pasan por @ControllerAdvice, así que los errores se traducen aquí a un
 * único formato JSON. Se registra antes que el DefaultErrorWebExceptionHandler de Spring Boot.
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }
        ErrorResponse body = toErrorResponse(ex, exchange.getRequest().getPath().value());
        response.setStatusCode(HttpStatusCode.valueOf(body.status()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.fromCallable(() -> toBuffer(response, body)));
    }

    private ErrorResponse toErrorResponse(Throwable ex, String path) {
        OffsetDateTime now = OffsetDateTime.now(clock);

        if (ex instanceof RequestValidationException validation) {
            return new ErrorResponse(now, validation.getStatus().value(), validation.getCode(),
                    validation.getMessage(), path, validation.getDetails());
        }
        if (ex instanceof ApiException api) {
            return new ErrorResponse(now, api.getStatus().value(), api.getCode(), api.getMessage(), path, List.of());
        }
        if (ex instanceof ServerWebInputException) {
            return new ErrorResponse(now, HttpStatus.BAD_REQUEST.value(), "MALFORMED_REQUEST",
                    "El cuerpo de la solicitud no es un JSON válido o tiene tipos incorrectos", path, List.of());
        }
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
            String code = status != null ? status.name() : "HTTP_ERROR";
            return new ErrorResponse(now, rse.getStatusCode().value(), code,
                    rse.getReason() != null ? rse.getReason() : code, path, List.of());
        }

        log.error("Error no controlado en {}", path, ex);
        return new ErrorResponse(now, HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR",
                "Ocurrió un error inesperado procesando la solicitud", path, List.of());
    }

    private DataBuffer toBuffer(ServerHttpResponse response, ErrorResponse body) throws JsonProcessingException {
        return response.bufferFactory().wrap(objectMapper.writeValueAsBytes(body));
    }
}
