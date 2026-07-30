# Diagrama entidad-relación

Corresponde exactamente a `src/main/resources/db/migration/V1__initial_schema_and_data.sql`
(la migración de Flyway provista por el profesor). Este diagrama documenta ese
schema tal cual quedó definido en las 9 tablas, no un diseño aparte.

> Este mismo diagrama está embebido en el [README](../README.md#modelo-de-datos)
> — este archivo es solo una copia standalone para quien prefiera abrirlo
> directamente sin buscarlo dentro del README. Si el schema cambia, actualizar
> ambos.

```mermaid
erDiagram
    USERS ||--o{ USER_ROLES : "tiene"
    ROLES ||--o{ USER_ROLES : "asignado en"
    USERS ||--o{ EVENTS : "organiza"
    CATEGORIES ||--o{ EVENTS : "clasifica"
    EVENTS ||--o{ SESSIONS : "contiene"
    EVENTS ||--o{ REGISTRATIONS : "recibe"
    USERS ||--o{ REGISTRATIONS : "se inscribe como participante"
    USERS ||--o{ REFRESH_TOKENS : "posee"
    USERS ||--o{ AUDIT_LOGS : "actúa como (nullable)"

    USERS {
        bigint id PK
        varchar first_name
        varchar last_name
        varchar email UK
        varchar password_hash
        varchar status "ACTIVE | BLOCKED"
        timestamptz created_at
        timestamptz updated_at
    }

    ROLES {
        bigint id PK
        varchar name UK "ADMIN | ORGANIZER | PARTICIPANT"
        varchar description
        timestamptz created_at
    }

    USER_ROLES {
        bigint user_id PK_FK
        bigint role_id PK_FK
        timestamptz assigned_at
    }

    CATEGORIES {
        bigint id PK
        varchar name "único case-insensitive"
        varchar description
        boolean active "eliminación lógica"
        timestamptz created_at
        timestamptz updated_at
    }

    EVENTS {
        bigint id PK
        varchar title
        text description
        varchar modality "PRESENTIAL | VIRTUAL | HYBRID"
        varchar location
        varchar virtual_url
        int capacity
        int available_capacity
        timestamptz registration_start_at
        timestamptz registration_end_at
        timestamptz start_at
        timestamptz end_at
        varchar status "DRAFT | PUBLISHED | FINISHED | CANCELLED"
        bigint organizer_id FK
        bigint category_id FK
        boolean deleted "eliminación lógica"
        bigint version "optimistic locking"
        timestamptz created_at
        timestamptz updated_at
    }

    SESSIONS {
        bigint id PK
        bigint event_id FK
        varchar title
        text description
        timestamptz start_at
        timestamptz end_at
        varchar location
        varchar virtual_url
        timestamptz created_at
        timestamptz updated_at
    }

    REGISTRATIONS {
        bigint id PK
        uuid registration_code UK "comprobante público"
        bigint event_id FK
        bigint participant_id FK
        varchar status "PENDING | CONFIRMED | REJECTED | CANCELLED"
        timestamptz registered_at
        timestamptz status_updated_at
        timestamptz confirmed_at
        timestamptz cancelled_at
        bigint version "optimistic locking"
    }

    REFRESH_TOKENS {
        bigint id PK
        uuid token_id UK "claim jti del refresh JWT"
        bigint user_id FK
        varchar token_hash UK "SHA-256, nunca el token en claro"
        timestamptz expires_at
        timestamptz revoked_at
        timestamptz created_at
        varchar created_by_ip
        uuid replaced_by_token_id "rotación"
    }

    AUDIT_LOGS {
        bigint id PK
        bigint actor_id FK "nullable (ej. login fallido sin usuario)"
        varchar action
        varchar resource_type
        bigint resource_id
        jsonb previous_value
        jsonb new_value
        varchar result "SUCCESS | FAILED"
        varchar ip_address
        varchar http_method
        varchar endpoint
        varchar correlation_id
        timestamptz created_at
    }
```

## Notas sobre restricciones no visibles en el diagrama

- `user_roles`: clave primaria compuesta `(user_id, role_id)`; `role_id` con
  `ON DELETE RESTRICT`, `user_id` con `ON DELETE CASCADE`.
- `categories.name`: único case-insensitive vía índice funcional sobre
  `LOWER(name)`, no una `UNIQUE` plana.
- `events`: `CHECK` de coherencia modalidad↔ubicación (`PRESENTIAL` exige
  `location` y prohíbe `virtual_url`, `VIRTUAL` al revés, `HYBRID` exige
  ambos), `CHECK` de orden de fechas
  (`registration_start_at < registration_end_at <= start_at < end_at`), y
  `available_capacity BETWEEN 0 AND capacity`.
- `registrations`: `UNIQUE(event_id, participant_id)` — como máximo una fila
  por combinación, sin importar el `status` (una inscripción cancelada se
  reabre, no se vuelve a insertar).
- `refresh_tokens.replaced_by_token_id` no tiene `FOREIGN KEY` real en el
  script: es solo una referencia lógica al `token_id` de la fila que la
  reemplazó durante la rotación.
- `audit_logs.actor_id`: `ON DELETE SET NULL` (la auditoría sobrevive aunque
  se elimine el usuario referenciado).
