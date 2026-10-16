package com.bancoias.pse.routes;

import com.bancoias.pse.constants.ApiConstants;
import com.bancoias.pse.dtos.CollectionOrderResponse;
import com.bancoias.pse.dtos.ErrorResponse;
import com.bancoias.pse.dtos.PaymentReportRequest;
import com.bancoias.pse.dtos.PaymentReportResponse;
import com.bancoias.pse.handlers.CollectionOrderHandler;
import com.bancoias.pse.handlers.PaymentHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class PaymentRouter {

    @Bean
    @RouterOperations({
            @RouterOperation(path = ApiConstants.PAYMENTS, method = RequestMethod.POST,
                    beanClass = PaymentHandler.class, beanMethod = "registerPayment",
                    operation = @Operation(operationId = "registerPayment", tags = "Pagos PSE",
                            summary = "Procesa un reporte de pago PSE",
                            requestBody = @RequestBody(required = true,
                                    content = @Content(schema = @Schema(implementation = PaymentReportRequest.class))),
                            responses = {
                                    @ApiResponse(responseCode = "201", description = "Reporte procesado (APPLIED o REJECTED)",
                                            content = @Content(schema = @Schema(implementation = PaymentReportResponse.class))),
                                    @ApiResponse(responseCode = "200", description = "Referencia ya procesada con los mismos datos; se devuelve el resultado original",
                                            content = @Content(schema = @Schema(implementation = PaymentReportResponse.class))),
                                    @ApiResponse(responseCode = "400", description = "Solicitud con formato inválido (no se persiste)",
                                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                                    @ApiResponse(responseCode = "409", description = "Referencia ya procesada con datos diferentes",
                                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
                            })),
            @RouterOperation(path = ApiConstants.PAYMENTS, method = RequestMethod.GET,
                    beanClass = PaymentHandler.class, beanMethod = "getRecent",
                    operation = @Operation(operationId = "getRecentPayments", tags = "Pagos PSE",
                            summary = "Reportes procesados recientemente (más reciente primero)",
                            parameters = {
                                    @Parameter(in = ParameterIn.QUERY, name = ApiConstants.QUERY_LIMIT,
                                            description = "Cantidad máxima de reportes (1-100, por defecto 20)"),
                                    @Parameter(in = ParameterIn.QUERY, name = ApiConstants.QUERY_COLLECTION_ORDER,
                                            description = "Opcional: solo los reportes de esta orden de recaudo")
                            },
                            responses = @ApiResponse(responseCode = "200",
                                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = PaymentReportResponse.class)))))),
            @RouterOperation(path = ApiConstants.PAYMENT_BY_REFERENCE, method = RequestMethod.GET,
                    beanClass = PaymentHandler.class, beanMethod = "getByReference",
                    operation = @Operation(operationId = "getPaymentByReference", tags = "Pagos PSE",
                            summary = "Consulta un reporte por su paymentReference",
                            parameters = @Parameter(in = ParameterIn.PATH, name = ApiConstants.PATH_PAYMENT_REFERENCE, required = true),
                            responses = {
                                    @ApiResponse(responseCode = "200",
                                            content = @Content(schema = @Schema(implementation = PaymentReportResponse.class))),
                                    @ApiResponse(responseCode = "404",
                                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
                            })),
            @RouterOperation(path = ApiConstants.COLLECTION_ORDERS, method = RequestMethod.GET,
                    beanClass = CollectionOrderHandler.class, beanMethod = "getAll",
                    operation = @Operation(operationId = "getCollectionOrders", tags = "Órdenes de recaudo",
                            summary = "Órdenes de recaudo con su saldo pendiente actual",
                            responses = @ApiResponse(responseCode = "200",
                                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CollectionOrderResponse.class))))))
    })
    public RouterFunction<ServerResponse> pseRoutes(PaymentHandler paymentHandler,
                                                    CollectionOrderHandler collectionOrderHandler) {
        return route()
                .POST(ApiConstants.PAYMENTS, paymentHandler::registerPayment)
                .GET(ApiConstants.PAYMENTS, accept(MediaType.APPLICATION_JSON), paymentHandler::getRecent)
                .GET(ApiConstants.PAYMENT_BY_REFERENCE, accept(MediaType.APPLICATION_JSON), paymentHandler::getByReference)
                .GET(ApiConstants.COLLECTION_ORDERS, accept(MediaType.APPLICATION_JSON), collectionOrderHandler::getAll)
                .build();
    }
}
