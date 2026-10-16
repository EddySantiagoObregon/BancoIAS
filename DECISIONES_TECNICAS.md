# Decisiones técnicas

Registro de las decisiones que más pesan en la solución. Cada una tiene el problema, las opciones que
evalué, lo que elegí y cómo lo comprobé.

---

## 1. Persistencia: PostgreSQL + R2DBC

**Problema.** Los reportes deben persistirse y el backend es WebFlux. Además, RF04 y RF05 exigen garantías
de consistencia (no sobregirar, no aplicar dos veces) que tienen que sostenerse aunque haya varias instancias.

**Alternativas.**
- Almacenamiento en memoria: descartado; no persiste y no sirve con más de una instancia.
- MongoDB reactivo: tiene buen soporte en WebFlux, pero el problema central es descontar un saldo y
  registrar el resultado de forma atómica, y eso en una base relacional con transacciones y restricciones es
  más simple y más fácil de defender.
- JPA/JDBC: bloqueante; usarlo dentro de WebFlux obliga a mover todo a otro scheduler y pierde el sentido.
- **PostgreSQL con Spring Data R2DBC**: driver no bloqueante, transacciones reactivas, `UNIQUE`, `CHECK` y
  `UPDATE ... RETURNING`.

**Decisión.** PostgreSQL 16 + R2DBC. Dinero en `NUMERIC(19,2)` y `BigDecimal` (nunca `double`).

Tablas:
- `collection_orders`: `id`, `merchant_id`, `status` (`OPEN`/`BLOCKED`), `pending_balance`, `updated_at`.
  `CHECK (pending_balance >= 0)` como última barrera.
- `payment_reports`: datos del reporte, `status`, `reason_code`, `reason_message`, `balance_after` (saldo justo
  después de aplicar, para trazabilidad) y `processed_at` asignado por el backend. `UNIQUE (payment_reference)`
  e índice por `processed_at DESC` para la consulta de recientes.

`payment_reports.collection_order_id` no tiene llave foránea a propósito: un reporte para una orden
inexistente también debe guardarse (rechazado con `ORDER_NOT_FOUND`).

El paquete `collections` contiene las entidades `@Table` de R2DBC (los modelos persistidos).

**Datos semilla.** `schema.sql` usa `CREATE TABLE IF NOT EXISTS` y `data.sql` usa `INSERT ... ON CONFLICT (id)
DO NOTHING`, ejecutados por `spring.sql.init` en cada arranque. Así las órdenes no se duplican y un reinicio no
restablece los saldos.

**Trade-off.** No uso Flyway/Liquibase. Para el alcance de la prueba alcanza, pero no hay versionado de
esquema.

**Validación.** Se reinició el backend contra el PostgreSQL de Docker Compose y seguían existiendo 3 órdenes
con el saldo que había quedado antes del reinicio. Con `\d payment_reports` se comprobó que existieran la
restricción `UNIQUE` y los `CHECK`.

---

## 2. Concurrencia sobre el saldo (RF04)

**Problema.** Saldo 1.000.000; llegan a la vez A = 700.000 y B = 600.000. Si ambas leen el saldo, ambas ven
1.000.000 y ambas descuentan.

**Alternativas.**
- `synchronized` / locks en Java: solo protege dentro de un proceso; con dos instancias no sirve. En código
  reactivo además bloquearía hilos del event loop.
- Leer, validar en Java y luego guardar el nuevo saldo: es exactamente la condición de carrera que hay que
  evitar.
- Bloqueo optimista con `@Version` + reintentos: funciona, pero bajo contención genera reintentos y más código.
- `SELECT ... FOR UPDATE`: funciona, pero son dos sentencias y mantiene el lock más tiempo.
- **`UPDATE` condicional atómico**: una sola sentencia que valida y descuenta.

**Decisión.**

```sql
UPDATE collection_orders
   SET pending_balance = pending_balance - :amount, updated_at = now()
 WHERE id = :orderId AND merchant_id = :merchantId AND status = 'OPEN'
   AND pending_balance >= :amount
RETURNING pending_balance
```

(`CollectionOrderRepository.debitIfAvailable`). PostgreSQL toma el lock de la fila; si otra transacción la
está modificando, espera a que termine y vuelve a evaluar el `WHERE` con el valor confirmado (READ COMMITTED).
Si ya no alcanza, no se actualiza ninguna fila, el `Mono` termina vacío y el servicio lo convierte en un
rechazo `INSUFFICIENT_BALANCE`.

El descuento y el `INSERT` del reporte corren en la misma transacción (`TransactionalOperator`): si el insert
falla, el descuento se revierte.

La validación de saldo que hace `PaymentRulesValidator` antes del `UPDATE` es solo para dar un motivo claro
rápido; la garantía real es el `UPDATE`.

**Trade-off.** Todas las solicitudes sobre una misma orden se serializan en esa fila. Para órdenes de recaudo
es razonable; si una sola orden recibiera miles de pagos por segundo habría que pensar otra cosa.

**Validación.**
- `PaymentConcurrencyIntegrationTest.twoSimultaneousPaymentsCannotOverdrawTheOrder` (repetida 5 veces): siempre
  exactamente 1 `APPLIED` y 1 `REJECTED`. En los logs se ve que el ganador cambia entre A y B según la
  ejecución, así que la carrera es real.
- `manySimultaneousPartialPaymentsApplyExactlyWhatTheBalanceAllows`: 15 × 150.000 en paralelo → 6 aplicados,
  saldo 100.000.
- `CollectionOrderRepositoryIntegrationTest.concurrentDebitsNeverOverdraw`: llama el `UPDATE` directamente, sin
  el validador, 20 × 70.000 en paralelo → 14 aplicados, saldo 20.000.
- Contra la app en ejecución, 10 POST HTTP en paralelo de 300.000 sobre `ORD-PSE-2001` (2.000.000): 6
  aplicados, 4 rechazados.

Las pruebas corren contra PostgreSQL real (Testcontainers), no contra mocks. Cada llamada usa su propia conexión
del pool R2DBC, así que compiten de verdad en la base de datos.

---

## 3. Referencias repetidas / idempotencia (RF05)

**Problema.** Una misma `paymentReference` puede llegar varias veces, incluso al mismo tiempo. No puede
generar una segunda aplicación y se debe conservar el resultado original.

**Alternativas.**
- Verificar solo en Java si la referencia existe: dos solicitudes simultáneas pasan ambas la verificación.
- Cache/Redis de idempotencia: añade infraestructura y otra fuente de verdad que puede desincronizarse.
- `INSERT ... ON CONFLICT DO NOTHING` primero y luego procesar: posible, pero complica guardar el resultado
  final en el mismo registro.
- **`UNIQUE` en base de datos + consulta previa + resolución del conflicto**.

**Decisión.**
1. Dentro de la transacción se busca la referencia. Si existe:
   - mismos datos (orden, comercio y monto, comparando el `BigDecimal` por valor) → se devuelve el reporte
     original, HTTP 200 con header `X-Idempotent-Replay: true`. No se descuenta ni se inserta nada.
   - datos distintos → `PaymentReferenceConflictException`, HTTP 409. El original no se modifica.
2. Si no existe, se procesa normalmente.
3. **Carrera de inserción:** si dos solicitudes con la misma referencia pasan el paso 1 a la vez, ambas llegan
   al `INSERT`. La segunda choca con el `UNIQUE` (`DataIntegrityViolationException`), su transacción hace
   rollback (incluido el descuento de saldo que ya había hecho) y, **fuera de la transacción**, el
   `onErrorResume` vuelve a leer el reporte ganador y aplica la misma regla del paso 1.

Esto aplica igual a reportes rechazados: si `REF-X` se rechazó por orden bloqueada, reenviarla devuelve el
mismo rechazo; no se reprocesa.

**Trade-off.** Una referencia que se rechazó (por ejemplo por monto 0) queda "usada": si el comercio la
reenvía corregida recibe 409. Lo considero correcto, porque el enunciado pide preservar el resultado original
de cada referencia, pero es un comportamiento que hay que comunicar a los canales.

**Validación.**
- `repeatedReferenceWithSameDataReturnsOriginalWithoutDebitingAgain`, `repeatedRejectedReferenceKeepsTheOriginalRejection`,
  `repeatedReferenceWithDifferentDataIsAConflictAndPreservesOriginal`.
- `simultaneousRequestsWithSameReferenceApplyOnlyOnce` (4 solicitudes iguales en paralelo, repetida 5 veces):
  1 fila, saldo descontado una vez, 1 resultado nuevo y 3 `replayed`. En los logs aparece el mensaje "Carrera de
  inserción", lo que confirma que el camino del `UNIQUE` sí se ejercita.
- Contra la app en ejecución: 6 POST simultáneos con la misma referencia → un 201 y cinco 200, una sola fila.

---

## 4. Errores de formato frente a rechazos de negocio

**Problema.** RF03 pide guardar todo reporte procesado, pero no toda solicitud inválida es un "reporte".

**Decisión.**
- **400, no se persiste:** JSON mal formado, campos obligatorios vacíos, monto no numérico o con más de 2
  decimales, referencia con caracteres no permitidos o demasiado larga. No hay un reporte identificable o no se
  puede guardar con integridad.
- **201 con `REJECTED`, sí se persiste:** monto ≤ 0, orden inexistente, comercio que no corresponde, orden no
  habilitada, saldo insuficiente. Son las reglas de RF02 y forman parte del resultado de negocio.

Por eso `PaymentReportRequest` valida `@NotNull` y `@Digits` en el monto, pero **no** `@Positive`: el signo
lo evalúa la regla de negocio.

Elegí 201 también para los rechazos porque el recurso "reporte" sí se creó; el resultado va en `status`. Usar
422 habría mezclado "el reporte quedó registrado como rechazado" con "la solicitud no se pudo procesar".

---

## 5. Programación reactiva: dónde y por qué

- **Router functions + handlers** (`PaymentRouter`, `PaymentHandler`): el handler solo traduce HTTP ↔ servicio.
  Como no existe `@Valid` en endpoints funcionales, `RequestValidator` invoca Bean Validation y devuelve
  `Mono.error(...)` para integrarse al pipeline.
- **`Mono.error` + `onErrorResume` para las reglas**: cada regla de `PaymentRulesValidator` devuelve el valor o
  un `Mono.error(PaymentRejectedException)`. Al final de `applyOrReject`, un `onErrorResume` convierte ese error
  en un reporte `REJECTED` que se guarda igual. Así el flujo feliz es una cadena lineal de `flatMap` y no un
  árbol de `if`. `PaymentRejectedException` no genera stack trace porque es una señal de control, no una falla.
- **`switchIfEmpty`**: orden inexistente (`findById` vacío) y `UPDATE` que no afectó filas (saldo consumido por
  otra transacción) se modelan como `Mono` vacío y se traducen en el motivo correspondiente.
- **`Mono.defer`** en `switchIfEmpty(Mono.defer(() -> processNewReport(...)))`: evita armar el pipeline de
  procesamiento cuando la referencia ya existe.
- **`TransactionalOperator`** con `.as(transactionalOperator::transactional)`: la transacción se abre al
  suscribirse y se confirma o revierte según cómo termine el `Mono`. El `onErrorResume` de duplicados va
  *después* a propósito, para que el rollback ocurra primero.
- **`Flux` con backpressure** en `GET /payments` y `GET /collection-orders`: se pasa el `Flux` directamente a
  `ServerResponse.body(...)`.
- **Pruebas:** `StepVerifier` para servicios y validadores; `subscribeOn(Schedulers.parallel())` + `Flux.merge`
  para lanzar solicitudes realmente concurrentes. No hay `Thread.sleep`.
- Sin `block()` ni `subscribe()` manual en código de producción (solo en preparación de datos de las pruebas).

Herramientas que evalué y **no** usé porque no resolvían nada del enunciado: `retry`/`repeat` (reintentar
un pago puede duplicar efectos; la idempotencia ya cubre el reenvío del cliente), Redis, WebClient (no hay
servicios externos), Spring Security/JWT (el enunciado excluye autenticación), Kafka/Spring Cloud Stream (ver
RabbitMQ), y Reactor Context (no había un dato transversal que propagar).

---

## 6. Estructura del backend

Organicé el backend por responsabilidad técnica (`configs`, `constants`, `dtos`, `enums`, `exceptions`, `handlers`,
`mappers`, `records`, `repositories`, `routes`, `services/definitions`, `services/impls`, `validators`,
`collections`).

- Tres servicios con responsabilidades separadas: `PaymentBusinessService` (procesar), `PaymentQueryService`
  (consultas) y `CollectionOrderService` (acceso a órdenes y descuento). No agregué un `PaymentCrudService`
  porque habría duplicado lo que ya hace el repositorio.
- `dtos` son los contratos HTTP; `records` son objetos internos (`PaymentCommand`, `PaymentProcessingResult`).
- No creé `clientes` ni `streams`: no hay integraciones externas ni mensajería, y dejar paquetes vacíos o con
  código ficticio no aporta.
- `GlobalErrorHandler` implementa `ErrorWebExceptionHandler` con `@Order(-2)`, porque `@ControllerAdvice` no
  aplica a router functions.
- Fechas en UTC (`Clock` inyectable). PostgreSQL devuelve `timestamptz` con el offset de la sesión, así que los
  mappers normalizan a UTC; sin eso, la respuesta del POST y la de un GET posterior del mismo reporte diferían
  en el offset. El monto también se normaliza a escala 2 por la misma razón.
- `GET /collection-orders` no lo pide el enunciado; lo agregué en solo lectura para que la interfaz muestre cómo
  cambia el saldo. No es un módulo de administración.

---

## 7. Frontend

- Angular 18.2 standalone, sin router (es una sola pantalla), con Reactive Forms y `HttpClient`.
- Servicios separados de los componentes (`PaymentApiService`, `CollectionOrderApiService`). Los errores HTTP se
  normalizan a un `ApiError` en un solo lugar (`toApiError`).
- `PaymentEventsService` (un `Subject`) avisa que se procesó un pago; el historial y el panel de saldos se
  recargan con `merge(refresh$, processed$).pipe(switchMap(...))`. `switchMap` descarta respuestas viejas si se
  pide otra recarga antes de que llegue la anterior.
- El POST usa `observe: 'response'` para distinguir 201 (nuevo) de 200 (referencia repetida) y mostrar el aviso
  correspondiente.
- El formulario no bloquea montos ≤ 0 ni órdenes desconocidas: esas reglas son del core, y la interfaz debe
  permitir ver el rechazo y su motivo.
- Proxy de `ng serve` hacia el backend, y CORS en el backend para `localhost:4200`.

---

## 8. RabbitMQ (opcional) — no implementado

Decidí no implementarlo. El enunciado lo marca como opcional y estima 2 horas para el alcance obligatorio.
Hacerlo bien no es solo publicar un mensaje: si publico después del commit y el proceso cae entre el commit y el
envío, el evento se pierde; si publico dentro de la transacción y luego hay rollback, publico un pago que no
existe.

Si lo implementara, lo haría así:
- **Transactional Outbox:** en la misma transacción del pago aplicado, insertar una fila en `payment_events_outbox`
  (`event_id`, `payment_reference`, `payload`, `created_at`, `published_at`).
- Un publicador reactivo lee pendientes, publica en un exchange `pse.payments` con routing key
  `payment.applied` y marca `published_at`. Entrega *at-least-once*.
- Evento `PaymentApplied` con `eventId`, `paymentReference`, `collectionOrderId`, `merchantId`, `amount`,
  `balanceAfter`, `processedAt`. Solo para `APPLIED`, nunca para rechazados.
- Los consumidores deduplican por `eventId` o `paymentReference`.
- El código iría en el paquete `streams`.

---

## 9. Entorno

- **Java 17 en lugar de 21:** es el JDK disponible en mi equipo. Spring Boot 3.3 soporta ambos y el código no
  depende de características de 21.
- **Puertos:** PostgreSQL en 5433 y backend en 8090, porque 5432 y 8080 ya estaban ocupados en mi máquina. Ambos
  se pueden cambiar por variables de entorno.
- **Node:** Angular 18 requiere Node 18.19+/20.11+; usé Node 20.13.
