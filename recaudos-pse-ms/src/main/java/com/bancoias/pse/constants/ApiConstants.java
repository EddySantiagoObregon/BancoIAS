package com.bancoias.pse.constants;

public final class ApiConstants {

    public static final String API_BASE = "/api/v1";
    public static final String PAYMENTS = API_BASE + "/payments";
    public static final String PAYMENT_BY_REFERENCE = PAYMENTS + "/{paymentReference}";
    public static final String COLLECTION_ORDERS = API_BASE + "/collection-orders";

    public static final String PATH_PAYMENT_REFERENCE = "paymentReference";
    public static final String QUERY_LIMIT = "limit";
    public static final String QUERY_COLLECTION_ORDER = "collectionOrderId";

    public static final String HEADER_IDEMPOTENT_REPLAY = "X-Idempotent-Replay";

    private ApiConstants() {
    }
}
