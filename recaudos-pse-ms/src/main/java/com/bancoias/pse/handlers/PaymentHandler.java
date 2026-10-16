package com.bancoias.pse.handlers;

import com.bancoias.pse.configs.AppProperties;
import com.bancoias.pse.constants.ApiConstants;
import com.bancoias.pse.dtos.PaymentReportRequest;
import com.bancoias.pse.dtos.PaymentReportResponse;
import com.bancoias.pse.exceptions.RequestValidationException;
import com.bancoias.pse.mappers.PaymentReportMapper;
import com.bancoias.pse.records.PaymentProcessingResult;
import com.bancoias.pse.services.definitions.PaymentBusinessService;
import com.bancoias.pse.services.definitions.PaymentQueryService;
import com.bancoias.pse.validators.RequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PaymentHandler {

    private final PaymentBusinessService paymentBusinessService;
    private final PaymentQueryService paymentQueryService;
    private final PaymentReportMapper paymentReportMapper;
    private final RequestValidator requestValidator;
    private final AppProperties appProperties;

    public Mono<ServerResponse> registerPayment(ServerRequest request) {
        return request.bodyToMono(PaymentReportRequest.class)
                .switchIfEmpty(Mono.error(() ->
                        new RequestValidationException("El cuerpo de la solicitud es obligatorio", List.of())))
                .flatMap(requestValidator::validate)
                .map(paymentReportMapper::toCommand)
                .flatMap(paymentBusinessService::process)
                .flatMap(this::toServerResponse);
    }

    public Mono<ServerResponse> getByReference(ServerRequest request) {
        String reference = request.pathVariable(ApiConstants.PATH_PAYMENT_REFERENCE);
        return paymentQueryService.findByReference(reference)
                .flatMap(report -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(report));
    }

    public Mono<ServerResponse> getRecent(ServerRequest request) {
        AppProperties.Payments config = appProperties.payments();
        return Mono.fromCallable(() -> requestValidator.parseLimit(
                        request.queryParam(ApiConstants.QUERY_LIMIT).orElse(null),
                        config.recentDefaultLimit(),
                        config.recentMaxLimit()))
                .flatMap(limit -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(paymentQueryService.findRecent(
                                request.queryParam(ApiConstants.QUERY_COLLECTION_ORDER).orElse(null), limit),
                                PaymentReportResponse.class));
    }

    private Mono<ServerResponse> toServerResponse(PaymentProcessingResult result) {
        PaymentReportResponse report = result.report();
        if (result.replayed()) {
            return ServerResponse.ok()
                    .header(ApiConstants.HEADER_IDEMPOTENT_REPLAY, "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(report);
        }
        URI location = UriComponentsBuilder.fromPath(ApiConstants.PAYMENT_BY_REFERENCE)
                .buildAndExpand(report.paymentReference())
                .toUri();
        return ServerResponse.created(location)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(report);
    }
}
