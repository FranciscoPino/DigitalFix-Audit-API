# ms-digitalfix-audit

API de consulta de timeline y auditoría de órdenes. Los endpoints son de solo lectura.

## Arquitectura y patrón

Bounded context DDD con modelo `TimelineEvent`, controlador de lectura y repositorio JDBC. El consumidor Kafka registra eventos en PostgreSQL `audit.timeline_events`; `eventId` evita duplicados ante reentregas. Las consultas devuelven el payload almacenado.

## Configuración local

Completa `.env` con `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` y `DB_SSL_MODE=require`. Puerto local: `8085`.

Kafka usa `KAFKA_BOOTSTRAP_SERVERS=localhost:19092,localhost:19093,localhost:19094`, `KAFKA_AUDIT_TOPIC=audit.timeline`, `KAFKA_DLT_TOPIC=audit.timeline.audit.DLT` y `KAFKA_GROUP_ID=digitalfix-audit` por defecto. Son configurables como variables de entorno. Para levantar el clúster y sus tópicos, consulta [Kafka local](../kafka/README.md).

Workorders publica el mensaje JSON con `eventId` como clave Kafka y el contrato de `integration.outbox_events`:

```json
{
  "eventId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  "eventType": "WORK_ORDER_CLOSED",
  "aggregateType": "WORK_ORDER",
  "aggregateId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "createdAt": "2026-01-01T10:00:00Z",
  "payload": {"workOrderId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "status": "CLOSED", "changedBy": "user-1"}
}
```

Los campos corresponden a `event_id`, `event_type`, `aggregate_type`, `aggregate_id`, `created_at` y `payload` de la outbox. Mantén el mismo `eventId` al reintentar; `payload.changedBy`, cuando existe, se registra como actor.

El consumidor confirma el offset tras procesar cada registro; después de tres reintentos de 5 segundos envía fallos a su DLT. Si falla esa publicación, no se confirma el registro. `earliest` se aplica a grupos nuevos; no reinicia offsets existentes.

Limitación de identidad: el código existente de workorders asigna `changedBy` al identificador del cliente, no necesariamente al usuario autenticado que cambió el estado. Tampoco captura rol ni IP de origen; por ello esos datos no constituyen una auditoría de identidad verificable.

## Ejecutar y probar

```powershell
mvn clean verify
mvn spring-boot:run
```

Las pruebas opcionales de integración con Kafka requieren tópicos de entrada y DLT de prueba ya creados, con el mismo número de particiones. Usan un repositorio simulado y no escriben en PostgreSQL:

```powershell
$env:DIGITALFIX_KAFKA_TEST = "true"
$env:KAFKA_AUDIT_TOPIC = "<topic-de-prueba>"
$env:KAFKA_DLT_TOPIC = "<topic-dlt-de-prueba>"
$env:KAFKA_GROUP_ID = "digitalfix-audit-test-$([guid]::NewGuid())"
mvn test
```

Para probar además la persistencia, `DIGITALFIX_TEST_DB_URL` debe apuntar a una base PostgreSQL **vacía y desechable**, con usuario `postgres` y contraseña `test`. Las pruebas crean esquemas dentro de una transacción que revierten al finalizar. Sin esta variable se omiten.

En Postman usa la URL BFF `http://localhost:8080` con `Authorization: Bearer {{accessToken}}` cuando esté activa la validación JWT; también puedes apuntar directamente a `http://localhost:8085` durante desarrollo local.

| Método | Ruta | Permiso |
|---|---|---|
| GET | `/api/audit/{workOrderId}` | Admin o Auditor |
| GET | `/api/audit?userId=...&eventType=...` | Admin o Auditor |

Ejemplo:

```powershell
curl.exe "http://localhost:8080/api/audit/<WORK_ORDER_UUID>" -H "Authorization: Bearer $env:ACCESS_TOKEN"
```
