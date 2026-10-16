# BancoIAS — Core de Recaudos PSE

Solución a la prueba técnica Full Stack (Java Spring WebFlux + Angular). El sistema recibe reportes de pago PSE
sobre órdenes de recaudo, los aplica o rechaza según las reglas de negocio, guarda el resultado de cada reporte
en PostgreSQL y permite consultarlos. Es una simulación: no mueve dinero ni se integra con PSE.

Lo más importante del ejercicio está en dos puntos:

- **Concurrencia (RF04):** el saldo se descuenta con un `UPDATE ... WHERE pending_balance >= :monto` atómico
  dentro de una transacción reactiva. Dos pagos simultáneos nunca pueden sobregirar la orden.
- **Referencias repetidas (RF05):** `payment_reference` tiene restricción `UNIQUE` en la base de datos. Un
  reenvío idéntico devuelve el resultado original (HTTP 200); uno con datos distintos devuelve HTTP 409.

El detalle de estas decisiones está en [DECISIONES_TECNICAS.md](DECISIONES_TECNICAS.md), y la trazabilidad de
requisitos en [MATRIZ_CUMPLIMIENTO.md](MATRIZ_CUMPLIMIENTO.md).

## Tecnologías

| Capa | Tecnología | Versión |
|---|---|---|
| Backend | Java | 17 |
| | Spring Boot (WebFlux, Data R2DBC, Validation) | 3.3.5 |
| | Project Reactor | la que trae Boot 3.3.5 |
| | r2dbc-postgresql | la que trae Boot 3.3.5 |
| | MapStruct / Lombok | 1.6.2 / la que trae Boot |
| | springdoc-openapi (WebFlux UI) | 2.6.0 |
| Pruebas | JUnit 5, Reactor Test (StepVerifier), WebTestClient, Testcontainers | las que trae Boot 3.3.5 |
| Base de datos | PostgreSQL | 16 (imagen `postgres:16-alpine`) |
| Frontend | Angular (standalone, Reactive Forms, HttpClient, RxJS) | 18.2 |
| | TypeScript / RxJS | 5.5 / 7.8 |

Uso Java 17 porque es el JDK instalado en mi equipo; el código no usa nada exclusivo de 17 ni de 21, así
que corre igual en Java 21.

## Requisitos previos

- JDK 17 o superior y Maven 3.9+
- Node.js 18.19+ o 20.11+ (Angular 18 no funciona con versiones anteriores)
- Docker (para PostgreSQL y para las pruebas de integración con Testcontainers)

## Estructura

```text
.
├── docker-compose.yml          PostgreSQL 16 (puerto local 5433)
├── recaudos-pse-ms/            microservicio Spring WebFlux
│   └── src/main/java/com/bancoias/pse/
│       ├── collections/        entidades persistidas (@Table de R2DBC)
│       ├── configs/            CORS, OpenAPI, Clock, propiedades de la app
│       ├── constants/          rutas y nombres de headers/parámetros
│       ├── dtos/               contratos HTTP de entrada/salida
│       ├── enums/              estados y motivos de resultado
│       ├── exceptions/         excepciones de negocio y manejador global de errores
│       ├── handlers/           handlers funcionales (ServerRequest -> ServerResponse)
│       ├── mappers/            MapStruct
│       ├── records/            objetos internos (comando de pago, resultado)
│       ├── repositories/       repositorios reactivos R2DBC
│       ├── routes/             RouterFunction + documentación OpenAPI
│       ├── services/
│       │   ├── definitions/    interfaces de servicio
│       │   └── impls/          implementaciones
│       └── validators/         Bean Validation de la solicitud y reglas de negocio
│   └── src/main/resources/db/  schema.sql y data.sql (semilla idempotente)
└── recaudos-pse-web/           aplicación Angular
    └── src/app/
        ├── core/               modelos, servicios HTTP, manejo de errores
        ├── features/           formulario, resultado, historial, saldos, consulta
        └── shared/             etiquetas de estados
```

`collections` contiene las entidades relacionales de R2DBC. No hay paquetes para clientes externos ni
mensajería porque esta versión no tiene integraciones externas (ver decisiones técnicas).

## Cómo ejecutar

### 1. PostgreSQL

```bash
docker compose up -d
```

Levanta `bancoias-pse-db` en `localhost:5433` (base `bancoias_pse`, usuario/clave `bancoias`). Uso el 5433
para no chocar con otra instancia en el 5432.

### 2. Backend

```bash
cd recaudos-pse-ms
mvn spring-boot:run
```

Arranca en `http://localhost:8090`. Al iniciar ejecuta `schema.sql` (`CREATE TABLE IF NOT EXISTS`) y
`data.sql` (`INSERT ... ON CONFLICT DO NOTHING`), así que las tres órdenes semilla se crean una sola vez y los
reinicios no las duplican ni restablecen los saldos.

Variables opcionales: `SERVER_PORT`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`.

Swagger UI: `http://localhost:8090/swagger-ui.html`

Para volver al estado inicial (saldos originales y sin reportes):

```bash
docker compose down -v && docker compose up -d
```

### 3. Frontend

```bash
cd recaudos-pse-web
npm install
npm start
```

Abre `http://localhost:4200`. `ng serve` usa `proxy.conf.json` para enviar `/api` al backend en el 8090. El
backend también tiene CORS habilitado para `http://localhost:4200`.

### 4. Pruebas

```bash
cd recaudos-pse-ms
mvn test
```

Necesita Docker en ejecución: las pruebas de integración levantan su propio PostgreSQL con Testcontainers y no
tocan la base de `docker-compose`. Resultado de la última ejecución: **46 pruebas, 0 fallos**.

| Clase | Tipo | Qué cubre |
|---|---|---|
| `PaymentRulesValidatorTest` | unitaria + StepVerifier | reglas de RF02 aisladas |
| `PaymentBusinessServiceIntegrationTest` | integración (PostgreSQL) | aplicación, parciales, rechazos persistidos, duplicados |
| `PaymentConcurrencyIntegrationTest` | integración (PostgreSQL) | pagos simultáneos y misma referencia simultánea |
| `CollectionOrderRepositoryIntegrationTest` | integración (PostgreSQL) | el `UPDATE` condicional por sí solo, en paralelo |
| `PaymentRouterIntegrationTest` | integración HTTP (WebTestClient) | códigos HTTP, errores, consultas |

El frontend no tiene pruebas automatizadas; el flujo completo se verificó con un navegador headless contra el
backend y PostgreSQL reales (ver `USO_IA.md`).

## Endpoints

Base: `http://localhost:8090/api/v1`

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/payments` | Procesa un reporte de pago |
| GET | `/payments?limit=20` | Reportes recientes, el más nuevo primero (`limit` 1–100, por defecto 20) |
| GET | `/payments?collectionOrderId=ORD-PSE-1001` | Reportes recientes de una sola orden (se combina con `limit`) |
| GET | `/payments/{paymentReference}` | Consulta un reporte por referencia |
| GET | `/collection-orders` | Órdenes con su saldo pendiente actual (solo lectura) |

Respuestas de `POST /payments`:

| Código | Cuándo | ¿Se persiste? |
|---|---|---|
| 201 | Reporte nuevo, sea `APPLIED` o `REJECTED` (el body trae `status` y `reasonCode`) | Sí |
| 200 + header `X-Idempotent-Replay: true` | La referencia ya existía con los mismos datos; se devuelve el resultado original | No (ya existía) |
| 409 `PAYMENT_REFERENCE_CONFLICT` | La referencia ya existía con datos distintos | No; el original no cambia |
| 400 `INVALID_REQUEST` / `MALFORMED_REQUEST` | Faltan campos, formato inválido o JSON mal formado | No |

Un rechazo de negocio (monto ≤ 0, orden inexistente, comercio distinto, orden bloqueada, saldo insuficiente) no
es un error HTTP: es un resultado del procesamiento y queda guardado con su motivo.

Motivos (`reasonCode`): `PAYMENT_APPLIED`, `INVALID_AMOUNT`, `ORDER_NOT_FOUND`, `MERCHANT_MISMATCH`,
`ORDER_NOT_ENABLED`, `INSUFFICIENT_BALANCE`.

### Ejemplos

```bash
curl -i -X POST http://localhost:8090/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"paymentReference":"PSE-REF-001","collectionOrderId":"ORD-PSE-1001","merchantId":"COM-10","amount":600000}'
```

```http
HTTP/1.1 201 Created
Location: /api/v1/payments/PSE-REF-001

{"paymentReference":"PSE-REF-001","collectionOrderId":"ORD-PSE-1001","merchantId":"COM-10","amount":600000.00,
 "status":"APPLIED","reasonCode":"PAYMENT_APPLIED","reasonMessage":"Pago aplicado a la orden de recaudo",
 "balanceAfter":400000.00,"processedAt":"2026-10-16T16:16:24.532283Z"}
```

Rechazo por orden bloqueada:

```json
{"paymentReference":"PSE-REF-002","collectionOrderId":"ORD-PSE-1002","merchantId":"COM-10","amount":1000.00,
 "status":"REJECTED","reasonCode":"ORDER_NOT_ENABLED",
 "reasonMessage":"La orden de recaudo no se encuentra habilitada","processedAt":"..."}
```

```bash
curl http://localhost:8090/api/v1/payments/PSE-REF-001
curl "http://localhost:8090/api/v1/payments?limit=10"
curl http://localhost:8090/api/v1/collection-orders
```

En PowerShell use `curl.exe` (no el alias `curl`) o `Invoke-RestMethod`.

## Cómo verificar idempotencia y concurrencia

**Desde la interfaz:** registre un pago, presione "Procesar pago" otra vez sin cambiar nada (aparece el aviso
de resultado original y el saldo no cambia) y luego cambie solo el monto (aparece "Referencia en conflicto").

**Con curl:**

1. Envíe el ejemplo de `PSE-REF-001` dos veces: la primera responde 201 y la segunda 200 con
   `X-Idempotent-Replay: true`. El saldo de `ORD-PSE-1001` queda en 400.000, no en −200.000.
2. Envíe `PSE-REF-001` con `"amount":100`: responde 409 y `GET /payments/PSE-REF-001` sigue mostrando 600.000.

**Concurrencia:** está cubierta por `PaymentConcurrencyIntegrationTest` (700.000 y 600.000 simultáneos sobre
1.000.000; 15 pagos de 150.000 en paralelo; 4 solicitudes simultáneas con la misma referencia) y por
`CollectionOrderRepositoryIntegrationTest`. Para probarlo contra la aplicación en ejecución basta con lanzar
varias peticiones en paralelo, por ejemplo en bash:

```bash
for i in $(seq 1 10); do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8090/api/v1/payments \
    -H "Content-Type: application/json" \
    -d "{\"paymentReference\":\"PAR-$i\",\"collectionOrderId\":\"ORD-PSE-2001\",\"merchantId\":\"COM-20\",\"amount\":300000}" &
done; wait
curl http://localhost:8090/api/v1/collection-orders
```

Sobre los 2.000.000 de `ORD-PSE-2001` solo caben 6 pagos de 300.000: deben quedar 6 `APPLIED`, 4 `REJECTED`
(`INSUFFICIENT_BALANCE`) y saldo 200.000.

## Alcance y limitaciones

- RF01–RF07 están implementados y probados (ver matriz).
- El punto opcional de RabbitMQ **no** se implementó; en `DECISIONES_TECNICAS.md` explico por qué y cómo lo haría.
- El esquema se crea con `spring.sql.init` y no con una herramienta de migraciones (Flyway/Liquibase); para
  este alcance es suficiente, pero en un entorno real usaría migraciones versionadas.
- No hay autenticación ni administración de órdenes, tal como indica el enunciado.
- El frontend no tiene pruebas unitarias automatizadas.
