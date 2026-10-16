# Matriz de cumplimiento

Estado al último `mvn test`: 46 pruebas ejecutadas, 0 fallos. Rutas de clases relativas a
`recaudos-pse-ms/src/main/java/com/bancoias/pse/` y de pruebas a `recaudos-pse-ms/src/test/java/com/bancoias/pse/`.

## Requisitos funcionales

| ID | Descripción | Implementación | Pruebas | Estado | Observaciones |
|---|---|---|---|---|---|
| RF01 | Procesar un reporte de pago y registrar fecha/hora de procesamiento | `routes/PaymentRouter`, `handlers/PaymentHandler.registerPayment`, `services/impls/PaymentBusinessServiceImpl.process`, `configs/ClockConfig` | `PaymentBusinessServiceIntegrationTest.appliesValidPaymentAndDebitsBalance`, `PaymentRouterIntegrationTest.postAppliedPaymentReturns201WithLocation` | Cumplido | `processedAt` lo asigna el backend en UTC; el cliente no lo envía |
| RF02 | Validar monto > 0, orden existente, comercio, orden habilitada, saldo suficiente | `validators/PaymentRulesValidator`, `PaymentBusinessServiceImpl.applyOrReject`, `repositories/CollectionOrderRepository.debitIfAvailable` | `PaymentRulesValidatorTest` (8), `PaymentBusinessServiceIntegrationTest.rejects*`, `allowsPartialPaymentsUntilBalanceIsExhausted` | Cumplido | Se permiten pagos parciales; pagar exactamente el saldo es válido |
| RF03 | Conservar resultado (APPLIED/REJECTED, motivo, fecha); un rechazo no reduce saldo | `collections/PaymentReport`, `enums/PaymentResultReason`, `db/schema.sql` | `rejectedReportIsQueryableAndKeepsItsReason`, `assertRejectedWithoutDebit` (5 casos), `PaymentRouterIntegrationTest.postRejectedPaymentReturns201WithReason` | Cumplido | Errores de formato → 400 y no se persisten; rechazos de negocio → 201 y sí se persisten |
| RF04 | Solicitudes simultáneas sin exceder el saldo | `CollectionOrderRepository.debitIfAvailable` (UPDATE condicional atómico), `TransactionalOperator` en `PaymentBusinessServiceImpl`, `CHECK (pending_balance >= 0)` | `PaymentConcurrencyIntegrationTest.twoSimultaneousPaymentsCannotOverdrawTheOrder` (×5), `manySimultaneousPartialPaymentsApplyExactlyWhatTheBalanceAllows`, `CollectionOrderRepositoryIntegrationTest.concurrentDebitsNeverOverdraw` | Cumplido | Contra PostgreSQL real; también comprobado con 10 POST HTTP en paralelo |
| RF05 | Referencias repetidas: sin doble aplicación, conservar original, manejar datos distintos | `UNIQUE (payment_reference)`, `PaymentBusinessServiceImpl.resolveExisting` / `resolveInsertRace`, `PaymentRulesValidator.isSameReport`, `exceptions/PaymentReferenceConflictException` | `repeatedReferenceWithSameData...`, `repeatedRejectedReference...`, `repeatedReferenceWithDifferentData...`, `simultaneousRequestsWithSameReferenceApplyOnlyOnce` (×5), `PaymentRouterIntegrationTest.repeatedReference*` | Cumplido | Mismos datos → 200 + `X-Idempotent-Replay`; datos distintos → 409 |
| RF06 | Consultar por referencia y recientes | `services/impls/PaymentQueryServiceImpl`, `PaymentReportRepository.findRecent`, `PaymentHandler.getByReference/getRecent` | `PaymentRouterIntegrationTest.getByReferenceReturnsStoredReport`, `getUnknownReferenceReturns404`, `recentReportsAreReturnedNewestFirstAndRespectLimit`, `recentReportsCanBeFilteredByCollectionOrder`, `invalidLimitReturns400` | Cumplido | `limit` 1–100, por defecto 20; sin paginación avanzada. Extra: filtro opcional por orden de recaudo |
| RF07 | Interfaz Angular integrada al backend | `recaudos-pse-web/src/app/features/*`, `core/services/payment-api.service.ts`, `collection-order-api.service.ts` | Sin pruebas automatizadas | Cumplido | Verificado con navegador headless contra backend y PostgreSQL reales (ver `USO_IA.md`) |

## Escenarios de prueba mínimos

| # | Escenario | Prueba | Resultado |
|---|---|---|---|
| 1 | Aplicación correcta | `PaymentBusinessServiceIntegrationTest.appliesValidPaymentAndDebitsBalance` | Pasa |
| 2 | Pago parcial | `allowsPartialPaymentsUntilBalanceIsExhausted` | Pasa |
| 3 | Monto cero | `rejectsZeroAmountAndPersistsTheReport`, `PaymentRulesValidatorTest.rejectsZeroAmount` | Pasa |
| 4 | Monto negativo | `rejectsNegativeAmountAndPersistsTheReport`, `PaymentRulesValidatorTest.rejectsNegativeAmount` | Pasa |
| 5 | Orden inexistente | `rejectsUnknownOrder` | Pasa |
| 6 | Orden bloqueada | `rejectsBlockedOrder` (servicio y validador) | Pasa |
| 7 | Comercio incorrecto | `rejectsMerchantThatDoesNotOwnTheOrder` (servicio y validador) | Pasa |
| 8 | Monto superior al saldo | `rejectsAmountAboveBalance`, `PaymentRulesValidatorTest.rejectsAmountGreaterThanPendingBalance` | Pasa |
| 9 | Persistencia de rechazados | `rejectedReportIsQueryableAndKeepsItsReason` | Pasa |
| 10 | Referencia repetida, mismos datos | `repeatedReferenceWithSameDataReturnsOriginalWithoutDebitingAgain`, `PaymentRouterIntegrationTest.repeatedReferenceWithSameDataReturns200AndReplayHeader` | Pasa |
| 11 | Referencia repetida, datos distintos | `repeatedReferenceWithDifferentDataIsAConflictAndPreservesOriginal`, `PaymentRouterIntegrationTest.repeatedReferenceWithDifferentDataReturns409` | Pasa |
| 12 | Dos pagos simultáneos por el mismo saldo | `PaymentConcurrencyIntegrationTest.twoSimultaneousPaymentsCannotOverdrawTheOrder` | Pasa (5 repeticiones) |
| 13 | Misma referencia simultánea | `PaymentConcurrencyIntegrationTest.simultaneousRequestsWithSameReferenceApplyOnlyOnce` | Pasa (5 repeticiones) |
| 14 | Consulta por referencia | `PaymentRouterIntegrationTest.getByReferenceReturnsStoredReport` | Pasa |
| 15 | Consulta de recientes | `PaymentRouterIntegrationTest.recentReportsAreReturnedNewestFirstAndRespectLimit` | Pasa |

## Otros puntos del enunciado

| Punto | Estado | Observaciones |
|---|---|---|
| Persistencia de reportes | Cumplido | PostgreSQL 16 + R2DBC; verificado tras reiniciar la aplicación |
| Datos iniciales | Cumplido | `data.sql` con `ON CONFLICT DO NOTHING`; no se duplican |
| Pruebas automatizadas | Cumplido | 46 pruebas (unitarias, integración con Testcontainers, HTTP) |
| Instrucciones de ejecución | Cumplido | `README.md` |
| Decisiones técnicas | Cumplido | `DECISIONES_TECNICAS.md` |
| Uso de IA | Cumplido | `USO_IA.md` |
| Historial de commits | Cumplido | Commits por etapa en la rama `main` |
| RabbitMQ (opcional) | No implementado | Justificación y diseño propuesto (outbox) en `DECISIONES_TECNICAS.md` §8 |

## Limitaciones conocidas

- Sin pruebas automatizadas en el frontend.
- Esquema gestionado con `spring.sql.init`, sin herramienta de migraciones.
- Una referencia rechazada no puede reutilizarse con datos corregidos (responde 409); es intencional para
  preservar el resultado original, pero conviene comunicarlo a los canales.
