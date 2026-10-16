# Uso de inteligencia artificial

Durante el desarrollo usé Cursor como asistente de programación.

## En qué actividades

Definí en las instrucciones el alcance, la arquitectura por capas, las tecnologías y las restricciones clave:
programación reactiva sin bloqueos, control de concurrencia respaldado por la base de datos, restricción
`UNIQUE` sobre la referencia de pago y no alterar las reglas del enunciado. Con eso, la herramienta se usó para
generar el código del backend, del frontend y de las pruebas, para ejecutar la compilación y las pruebas, y como
apoyo en la redacción de la documentación.

## Qué resultados se aprovecharon

- El `UPDATE` condicional atómico para el descuento de saldo dentro de una transacción reactiva.
- La resolución de la carrera de inserción de referencias repetidas apoyada en el `UNIQUE` y el rollback.
- La separación entre errores de formato (400, no se persisten) y rechazos de negocio (se persisten).
- La interfaz Angular integrada con los endpoints reales.

## Cómo se validaron

- `mvn test`: 46 pruebas, 0 fallos, contra PostgreSQL real (Testcontainers), incluidas las de concurrencia.
- En los logs de las pruebas de concurrencia se comprobó que la competencia era real (el pago ganador cambia
  entre ejecuciones y se ejecuta el camino del `UNIQUE`).
- Aplicación en ejecución contra PostgreSQL: peticiones HTTP para cada escenario, peticiones en paralelo sobre
  una misma orden y reinicio para confirmar que los datos semilla no se duplican.
- Recorrido completo de la interfaz contra el backend real.

## Qué se corrigió o descartó

- Respuestas inconsistentes entre el POST y la consulta posterior del mismo reporte (escala del monto y offset
  de la fecha); se normalizaron a escala 2 y UTC.
- Un predicado de ruta que devolvía 404 en lugar de un error claro, un indicador de carga que se apagaba antes de
  tiempo y un selector que mostraba un valor distinto al aplicado.
- Se descartaron RabbitMQ (opcional), Redis, JWT, WebClient y Kafka por no resolver ningún requisito del
  enunciado.

## Información sensible

- No se compartieron credenciales reales, datos de clientes ni código de terceros, son datos ficticios

