package com.bancoias.pse.handlers;

import com.bancoias.pse.dtos.CollectionOrderResponse;
import com.bancoias.pse.services.definitions.CollectionOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class CollectionOrderHandler {

    private final CollectionOrderService collectionOrderService;

    public Mono<ServerResponse> getAll(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(collectionOrderService.findAll(), CollectionOrderResponse.class);
    }
}
