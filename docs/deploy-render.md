# Despliegue en Render

Este documento acompaña a `Dockerfile`, `.dockerignore` y `render.yaml`
(raíz del repo). Cubre docs/instrucciones.md §15 y contexto-materia.md §17.
**Nadie ejecutó el deploy real todavía** — `render.yaml` tiene
`autoDeployTrigger: "off"` a propósito para que el primer deploy lo dispares
vos, manualmente, cuando quieras.

## 0. Requisito: cuenta/servicios en Render

Necesitás una cuenta de Render (y conectar este repo de GitHub/GitLab desde
el dashboard como "Blueprint") — **no la creé yo, avisame si ya la tenés o
si necesitás que te guíe para crearla.**

## 1. Cómo se crea todo

En el dashboard de Render: **New → Blueprint**, apuntá al repo, rama con
estos archivos. Render lee `render.yaml` y propone crear 3 recursos:

| Recurso | Nombre | Qué es |
|---|---|---|
| `academic-events-db` | PostgreSQL gestionado | plan `free`, base `academic_events_db` |
| `academic-events-redis` | Key Value (Redis-compatible, corre Valkey) | plan `free` |
| `academic-events-api` | Web service (Docker) | el backend, `plan: free` |

Al confirmar, Render aprovisiona los 3 pero **no** hace el primer deploy del
backend automáticamente (`autoDeployTrigger: "off"`) hasta que vos lo
dispares desde el dashboard (botón "Deploy" o `git push` una vez que lo
cambies a `commit`).

## 2. Paso manual único: base de datos

**No hace falta correr `00_create_database.sql` en Render.** Ese script es
solo para cuando alguien crea el Postgres a mano (local, una VM, un
`docker run postgres` suelto): en Render, el campo `databaseName:
academic_events_db` de `render.yaml` hace que la base se cree
automáticamente al aprovisionar la instancia — confirmado contra la
documentación vigente de Render (Blueprint YAML Reference), no es una
suposición.

El esquema (`V1__initial_schema_and_data.sql`, con las tablas, triggers y
los datos semilla del profesor) tampoco requiere ningún paso manual:
Flyway lo aplica **automáticamente en el primer arranque** del backend
contra esa base — es el mismo comportamiento por defecto que ya ves en
`dev`, Render no cambia nada ahí.

Entonces el único paso "manual" real es indirecto: **esperar a que el
Postgres termine de aprovisionarse antes de disparar el primer deploy del
backend**, para que `DB_HOST`/`DB_PORT`/etc. ya existan cuando Render arme
las variables de entorno del web service.

⚠️ **Free Postgres expira**: el plan `free` de Postgres en Render se borra
30 días después de creado (+ 14 días de gracia para subir de plan antes del
borrado definitivo, con los datos incluidos). Si la evaluación del proyecto
puede caer después de esa ventana, subí `plan: free` a `plan: basic-256mb`
(o el que corresponda) en `render.yaml` antes de esa fecha — no lo hice yo
porque implica costo y no me lo pediste explícitamente.

## 3. Variables que vas a tener que completar vos (`sync: false`)

Render las deja vacías a propósito (no las puede adivinar ni yo pude
fijarlas). Se completan una sola vez desde el dashboard del servicio
`academic-events-api` → **Environment**, después de crear el Blueprint:

| Variable | Qué poner |
|---|---|
| `ALLOWED_ORIGINS` | Dominio(s) real(es) del frontend, separados por coma (ej. `https://mi-frontend.vercel.app`) |
| `SWAGGER_USERNAME` / `SWAGGER_PASSWORD` | Credenciales que vos elijas para el Basic Auth de `/swagger-ui/**` en prod (docs/instrucciones.md §11) |
| `REDIS_PASSWORD` | Ver sección 4 |

El resto (`DB_HOST/PORT/NAME/USERNAME/PASSWORD`, `REDIS_HOST/PORT`,
`JWT_SECRET`, `JWT_*_EXPIRATION`, `JAVA_TOOL_OPTIONS`,
`SPRING_PROFILES_ACTIVE`) ya quedan resueltos automáticamente por
`render.yaml` (`fromDatabase`, `fromService`, `generateValue` o `value`
fijo) — no hay que tocarlos.

## 4. Por qué `REDIS_PASSWORD` queda en `sync: false`

Render, para el tipo de servicio `keyvalue` (Redis-compatible), solo expone
`host`, `port` y `connectionString` vía `fromService` en el Blueprint —
**no** expone un `property: password` independiente. No pude confirmar con
certeza si las conexiones internas entre servicios del mismo proyecto de
Render exigen esa contraseña o no (la documentación no lo deja explícito).

Por eso, después del primer deploy: entrá al dashboard del servicio
`academic-events-redis` → copiá el password que Render le asigna → pegalo
como `REDIS_PASSWORD` en `academic-events-api` → Environment. Si la app
arranca bien con `REDIS_PASSWORD` vacío (conexión interna sin auth), no
hace falta este paso — pero dejarlo documentado como posible paso manual es
más seguro que asumir que no hace falta.

## 5. Por qué `DB_URL` no aparece como variable literal en prod

`docs/instrucciones.md` §16 pide `DB_URL` como variable mínima, y así sigue
funcionando en `dev` (`application-dev.yaml`, sin cambios). Pero Render no
entrega un JDBC URL listo para su Postgres gestionado (solo piezas sueltas:
host/puerto/nombre, o un `connectionString` en formato `postgres://...` que
el driver JDBC de PostgreSQL no acepta directamente — no soporta
`usuario:password@` embebido en la URL). Decisión acordada con el usuario:
`application-prod.yaml` compone el JDBC URL a partir de `DB_HOST`, `DB_PORT`
y `DB_NAME` (cada uno wireado automáticamente desde el Postgres de Render vía
`fromDatabase`); `DB_USERNAME`/`DB_PASSWORD` se mantienen exactamente como
la especificación los nombra.

## 6. Memoria de la JVM (`JAVA_TOOL_OPTIONS`)

Confirmado con el usuario: plan `free` de Render para el backend (512 MB de
RAM total, contenedor completo, límite duro). El Dockerfile **no**
hardcodea `-Xmx`; los límites vienen 100% de `JAVA_TOOL_OPTIONS` (variable
de entorno, ya en `render.yaml`), para poder subirlos el día que cambien de
plan sin reconstruir la imagen.

**Estos valores están verificados, no son un cálculo teórico.** Antes de
dejarlos en `render.yaml` construí la imagen local (`docker build`) y la
corrí con `--memory=512m --memory-swap=512m` (mismo límite duro que Render
Free) contra el Postgres/Redis del `docker-compose` local, con el perfil
`prod`:

- Primer intento, `-Xmx320m -XX:MaxMetaspaceSize=96m`: **el contenedor
  murió con `OutOfMemoryError: Metaspace`** al arrancar Spring Security —
  Spring Security 7 + AOP (`@Transactional`, `@PreAuthorize`,
  `RateLimitAspect`) + Hibernate generan más clases/proxies en runtime de
  lo que 96MB de metaspace alcanzan.
- Sin ningún límite de memoria: el proceso se estabiliza solo en **~408MB**
  apenas arranca (antes de servir ninguna request) — ya deja poquísimo
  margen contra el techo de 512MB del plan Free.
- Valores finales, con `--memory=512m` real:
  ```
  -Xms128m -Xmx256m -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC -XX:MaxDirectMemorySize=32m
  ```
  Con esto: arranca en ~7s, `/api/actuator/health` responde `200 UP`,
  Swagger pide Basic Auth (401 sin credenciales, funciona con
  `SWAGGER_USERNAME`/`SWAGGER_PASSWORD`), `/api/actuator/metrics` devuelve
  `401` sin token ADMIN (protegido, como debe ser) — y tras varias
  requests la memoria se mantiene estable en **~335MB / 512MB**, con
  margen real para picos de tráfico sin que Render mate el contenedor por
  exceso de memoria (OOM-kill).

`-XX:+UseSerialGC` porque en una instancia de 1 vCPU los recolectores
paralelos/G1 no aportan (compiten por el mismo core) y Serial GC tiene el
menor overhead de memoria fija.

Si más adelante suben a un plan con más RAM, solo hay que editar el
`value:` de `JAVA_TOOL_OPTIONS` en `render.yaml` — cero cambios de código
ni de imagen. Si el margen de ~177MB no alcanza bajo tráfico real
(concurrencia alta, varios reportes PDF/Excel generándose a la vez), sería
la primera señal de que conviene subir del plan Free.

## 7. El contenedor no escribe archivos permanentes

Confirmado: no hay `VOLUME` declarado en el `Dockerfile` ni ningún
directorio de escritura montado. Los reportes PDF/Excel (prompts 17-18,
`RegistrationPdfReportGenerator`, `RegistrationExcelReportGenerator`,
`RegistrationCertificatePdfGenerator`) se generan **100% en memoria**
(`ByteArrayOutputStream`) bajo demanda y se devuelven directo en el cuerpo
de la respuesta HTTP — nunca tocan el disco del contenedor. Esto es
justamente lo que exige docs/instrucciones.md §15 ("El contenedor de Spring
Boot no deberá almacenar archivos permanentes; los reportes se generarán
bajo demanda").

## 8. Checklist antes de disparar el primer deploy real

- [ ] Cuenta de Render creada y repo conectado como Blueprint
- [ ] Esperar a que `academic-events-db` y `academic-events-redis` terminen de aprovisionar
- [ ] Completar `ALLOWED_ORIGINS`, `SWAGGER_USERNAME`, `SWAGGER_PASSWORD` (sección 3)
- [ ] Revisar si `REDIS_PASSWORD` hace falta (sección 4)
- [ ] Confirmar que el plan `free` del Postgres alcanza para la ventana de evaluación (sección 2), o subirlo de plan
- [ ] Cambiar `autoDeployTrigger` de `"off"` a `"commit"` en `render.yaml` (o disparar el deploy manual desde el dashboard) cuando quieras el primer arranque real
- [ ] Verificar `GET https://<tu-servicio>.onrender.com/api/actuator/health` → `{"status":"UP"}`
- [ ] Verificar Swagger UI (`/swagger-ui.html`) pide las credenciales configuradas
