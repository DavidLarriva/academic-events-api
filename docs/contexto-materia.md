# Contexto Técnico Completo — Spring Boot (Programación y Plataformas Web)

> Documento de referencia técnica generado a partir de las 16 prácticas de la materia (repo `icc-ppw-frameworks-backend/spring-boot`). Uso: contexto para el proyecto final de backend con Spring Boot.

---

## 0. Stack y decisiones de la materia

| Aspecto | Decisión del curso |
|---|---|
| Lenguaje | Java 17+ |
| Framework | Spring Boot 4.0.0 |
| Build tool | **Gradle** (Groovy DSL) — no Maven |
| Servidor embebido | Tomcat (por defecto) |
| Persistencia | Spring Data JPA + Hibernate + **PostgreSQL** (vía Docker) |
| Seguridad | Spring Security + JWT (HS256) propio, sin OAuth externo |
| Documentación | springdoc-openapi (Swagger UI) |
| Contenedores | Docker multi-stage + Nginx reverse proxy |
| Arquitectura | MVCS por **módulos de dominio** (no por capa técnica global) |

**Group/Package base de ejemplo:** `ec.edu.ups.icc.fundamentos01`

---

## 1. Configuración inicial del proyecto

### 1.1 Generación (Spring Initializr)

| Campo | Valor |
|---|---|
| Build Tool | Gradle – Groovy DSL |
| Language | Java |
| Spring Boot | 4.0.0 |
| Packaging | Jar |
| Java | 17 |
| Dependencies mínimas | Spring Web, Spring Boot DevTools |

### 1.2 Servidor embebido (Tomcat)

Al agregar `spring-boot-starter-web`, Spring Boot: activa auto-configuración de Spring MVC → registra Tomcat embebido → lo inicia en el puerto 8080 → habilita `@RestController`. No requiere instalación externa; el servidor viaja empaquetado en el `.jar`.

### 1.3 Ejecución

```bash
./gradlew bootRun          # desarrollo
./gradlew build -x test    # genera build/libs/app.jar
java -jar build/libs/app.jar
```

### 1.4 Primer endpoint (sanity check)

```java
@RestController
public class StatusController {
    @GetMapping("/api/status")
    public Map<String, Object> status() {
        return Map.of("service", "Spring Boot API", "status", "running",
                      "timestamp", LocalDateTime.now().toString());
    }
}
```

### 1.5 Anotaciones base

| Anotación | Función |
|---|---|
| `@SpringBootApplication` | = `@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan` |
| `@RestController` | = `@Controller` + `@ResponseBody` (respuestas JSON directas) |
| `@GetMapping` / `@PostMapping` / etc. | Verbo HTTP + ruta |

---

## 2. Estructura del proyecto (arquitectura MVCS modular)

### 2.1 Regla de oro: ComponentScan

Spring Boot escanea **solo dentro del paquete raíz** (donde vive la clase `@SpringBootApplication`) y sus subpaquetes. Cualquier clase fuera de ese árbol no se registra como bean.

Ciclo de arranque:
```
main() → @SpringBootApplication → @ComponentScan
       → detecta @RestController / @Service / @Repository / @Configuration / @Component
       → registra beans → Auto-Configuration (según dependencias) → inicia Tomcat
```

### 2.2 Capas MVCS → carpetas Spring

| Capa | Carpeta | Anotación típica |
|---|---|---|
| Presentación | `controllers/` | `@RestController` |
| Negocio | `services/` (interfaz + `*Impl`) | `@Service` |
| Dominio | `models/` | (POJO, sin anotaciones JPA) |
| Persistencia | `entities/` | `@Entity` |
| Persistencia (acceso a datos) | `repositories/` | `@Repository` extends `JpaRepository` |
| Transferencia | `dtos/` | POJO + Jakarta Validation |
| Conversión | `mappers/` | clase estática o Model con factory methods |
| Transversal | `core/` (config, exceptions, base entities/dtos) | — |

### 2.3 Estructura recomendada (por dominio, no por capa técnica global)

```
src/main/java/ec/edu/ups/icc/fundamentos01/
├── Fundamentos01Application.java
├── core/
│   ├── entities/BaseEntity.java
│   ├── dtos/ (PaginationDto, ErrorResponseDto)
│   └── exceptions/
│       ├── base/ApplicationException.java
│       ├── domain/{NotFoundException,ConflictException,BadRequestException}.java
│       ├── handler/GlobalExceptionHandler.java
│       └── response/ErrorResponse.java
├── users/
│   ├── controllers/ UsersController.java, UserProductsController.java, CurrentUserController.java
│   ├── services/ UserService.java, UserServiceImpl.java
│   ├── repositories/ UserRepository.java
│   ├── entities/ UserEntity.java
│   ├── models/ UserModel.java
│   ├── dtos/ CreateUserDto, UpdateUserDto, PartialUpdateUserDto, UserResponseDto
│   └── mappers/ UserMapper.java
├── products/  (misma subestructura: controllers/services/repositories/entities/dtos/mappers)
├── categories/ (idem)
└── security/
    ├── config/ SecurityConfig.java, JwtProperties.java, OpenApiConfig.java
    ├── controllers/ AuthController.java
    ├── dtos/ LoginRequestDto, RegisterRequestDto, AuthResponseDto, RefreshTokenRequestDto
    ├── entities/ RoleEntity.java, RefreshTokenEntity.java
    ├── enums/ RoleName.java
    ├── filters/ JwtAuthenticationFilter.java, JwtAuthenticationEntryPoint.java
    ├── repositories/ RoleRepository.java, RefreshTokenRepository.java
    ├── services/ AuthService.java, UserDetailsImpl.java, UserDetailsServiceImpl.java, RefreshTokenService.java
    └── utils/ JwtUtil.java
```

Convención de nombres del flujo de datos:
```
DTO    = lo que entra/sale por la API
Model  = lo que usa la lógica de negocio (dominio puro, sin anotaciones JPA)
Entity = lo que se guarda en BD (JPA)
Mapper = convierte entre DTO ↔ Model ↔ Entity
```

### 2.4 Gradle vs Maven (por qué Gradle)

Sintaxis compacta (`build.gradle`), builds incrementales más rápidos, mejor integración CI/CD moderna. Maven usa XML extenso y es más rígido.

---

## 3. Flujo de una petición REST (visión completa, capas acumuladas a lo largo del curso)

```
Cliente
  ↓ HTTP + JWT (si aplica)
Tomcat embebido → DispatcherServlet
  ↓
JwtAuthenticationFilter (valida access token, puebla SecurityContext)
  ↓
Controller (@RestController) — recibe DTO validado (@Valid), extrae @AuthenticationPrincipal
  ↓
@PreAuthorize (autorización por rol, si el endpoint lo exige)
  ↓
Service (interfaz) → ServiceImpl (@Service) — valida ownership, reglas de negocio
  ↓
Repository (JpaRepository) → Hibernate → PostgreSQL
  ↓
Entity → Mapper → Model → Mapper → ResponseDto
  ↓
Controller devuelve DTO (Jackson serializa a JSON)
  ↓
Si algo falla en cualquier capa → excepción → GlobalExceptionHandler → ErrorResponse uniforme
```

---

## 4. CRUD REST: controladores, DTOs, modelos, mappers (evolución progresiva)

### 4.1 Etapa 1 — CRUD directo en el controlador (didáctico, sin capas)

Se usa una lista en memoria dentro del controlador (`List<UserModel>`) solo para entender el ciclo HTTP → DTO → Modelo → Respuesta. **No se usa en producción.**

### 4.2 Modelo de dominio (sin JPA)

```java
public class UserModel {
    private Long id;
    private String name;
    private String email;
    private LocalDateTime createdAt;
    private String password;       // recibido, temporal
    private String passwordHash;    // derivado
    // getters/setters
}
```

### 4.3 DTOs — uno por acción (principio de responsabilidad única)

| DTO | Uso | Notas |
|---|---|---|
| `CreateXxxDto` | POST | sin `id`, sin `createdAt` (los genera el backend) |
| `UpdateXxxDto` | PUT (reemplazo total) | sin `id` (va en la URL) |
| `PartialUpdateXxxDto` | PATCH | campos nulos = "no actualizar" |
| `XxxResponseDto` | Respuesta | nunca expone `password`/`passwordHash`/campos internos |

### 4.4 Mapper (conversión manual, sin librerías tipo MapStruct)

```java
public class UserMapper {
    public static UserModel toModel(CreateUserDto dto) {
        UserModel model = new UserModel();
        model.setName(dto.getName());
        model.setEmail(dto.getEmail());
        model.setPasswordHash("HASH_" + dto.getPassword());
        model.setCreatedAt(LocalDateTime.now());
        return model;
    }
    public static UserResponseDto toResponse(UserModel model) {
        UserResponseDto r = new UserResponseDto();
        r.setId(model.getId()); r.setName(model.getName()); r.setEmail(model.getEmail());
        return r;
    }
}
```

### 4.5 Endpoints CRUD estándar (patrón repetido en cada recurso)

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/{recurso}` | Lista |
| GET | `/api/{recurso}/{id}` | Detalle |
| POST | `/api/{recurso}` | Crear |
| PUT | `/api/{recurso}/{id}` | Reemplazo total |
| PATCH | `/api/{recurso}/{id}` | Actualización parcial |
| DELETE | `/api/{recurso}/{id}` | Eliminar (lógico, ver §6.5) |

---

## 5. Servicios e inyección de dependencias

### 5.1 Por qué separar Service del Controller

El controlador debe **solo** recibir la petición HTTP y delegar. Toda la lógica (búsqueda, creación, reglas) se mueve a una interfaz `XxxService` + su implementación `XxxServiceImpl`.

```java
public interface UserService {
    List<UserResponseDto> findAll();
    UserResponseDto findOne(Long id);
    UserResponseDto create(CreateUserDto dto);
    UserResponseDto update(Long id, UpdateUserDto dto);
    UserResponseDto partialUpdate(Long id, PartialUpdateUserDto dto);
    void delete(Long id);
}

@Service
public class UserServiceImpl implements UserService { /* implementación */ }
```

### 5.2 Inyección por constructor (recomendada, no `new` manual)

```java
@RestController
@RequestMapping("/users")
public class UsersController {
    private final UserService service;
    public UsersController(UserService service) { this.service = service; } // Spring inyecta UserServiceImpl

    @GetMapping public List<UserResponseDto> findAll() { return service.findAll(); }
    @PostMapping public UserResponseDto create(@Valid @RequestBody CreateUserDto dto) { return service.create(dto); }
}
```

Spring detecta que `UsersController` requiere un `UserService`, busca la implementación anotada con `@Service` (`UserServiceImpl`) y la inyecta automáticamente.

---

## 6. Persistencia con JPA (PostgreSQL + Hibernate)

### 6.1 Dependencias (`build.gradle`)

```gradle
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
runtimeOnly("org.postgresql:postgresql")
implementation("org.springframework.boot:spring-boot-starter-validation")
```

### 6.2 `application.yml` — conexión

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/devdb
    username: ups
    password: ups123
  jpa:
    hibernate:
      ddl-auto: update   # dev: update | prod: validate
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

`ddl-auto`: `update` (crea/actualiza sin borrar), `create`/`create-drop` (destructivo), `validate` (solo valida, **usar en prod**), `none`.

### 6.3 `BaseEntity` — campos comunes de auditoría (herencia con `@MappedSuperclass`)

```java
@MappedSuperclass
public abstract class BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted;

    @PrePersist protected void onCreate() { this.deleted = false; this.createdAt = LocalDateTime.now(); }
    @PreUpdate  protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
```

### 6.4 Entidad concreta

```java
@Entity
@Table(name = "users")
public class UserEntity extends BaseEntity {
    @Column(nullable = false, length = 150) private String name;
    @Column(nullable = false, unique = true, length = 150) private String email;
    @Column(nullable = false) private String passwordHash;
}
```

### 6.5 Eliminado lógico (soft delete)

**No se usa `DELETE` físico.** Se marca `deleted = true` y todas las consultas de lectura filtran por `deleted = false` (vía métodos derivados como `findByDeletedFalse()` o filtrando tras `findById`).

### 6.6 Repositorio (Spring Data JPA)

```java
@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);
    boolean existsByIdAndDeletedFalse(Long id);
    Optional<UserEntity> findByIdAndDeletedFalse(Long id);
}
```

`JpaRepository<T, ID>` da gratis: `save`, `findById`, `findAll`, `delete`, `deleteById`, `existsById`, `count`. Los métodos con nombre (`findByEmail`, `findByXAndYFalse`) se traducen automáticamente a SQL por convención de nombres (**query derivation**).

### 6.7 Flujo completo Entity ↔ Model ↔ DTO

```
CreateDto → Model (Mapper) → Entity (Mapper) → repository.save(entity) → Entity guardada
         → Model (Mapper) → ResponseDto (Mapper) → Cliente
```

El **Model nunca lleva anotaciones JPA**; la Entity nunca se devuelve directamente al cliente (evita acoplar la API a la base de datos y exponer campos sensibles).

---

## 7. DTOs y validación (Jakarta Validation)

### 7.1 Dependencia

```gradle
implementation("org.springframework.boot:spring-boot-starter-validation")
```

### 7.2 Anotaciones más usadas

| Anotación | Uso |
|---|---|
| `@NotBlank` | String obligatorio, no vacío/espacios |
| `@NotNull` | valor obligatorio (numéricos, objetos) |
| `@Size(min, max)` | longitud de string/colección |
| `@Email` | formato de correo |
| `@Min` / `@Max` | rango numérico |
| `@DecimalMin(value, inclusive)` | rango decimal (precios) |
| `@NotEmpty` | colección no vacía |
| `@Pattern(regexp, message)` | regex custom (ej. password fuerte) |

```java
public class CreateUserDto {
    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 150) private String name;

    @NotBlank @Email @Size(max = 150) private String email;

    @NotBlank @Size(min = 8, message = "Mínimo 8 caracteres") private String password;
}
```

### 7.3 Activación en el controlador

```java
@PostMapping
public UserResponseDto create(@Valid @RequestBody CreateUserDto dto) { return service.create(dto); }
```

Si falla `@Valid`, Spring lanza `MethodArgumentNotValidException` **antes** de llegar al servicio → capturada por el `GlobalExceptionHandler` (§8) → `400 Bad Request` con detalle por campo.

### 7.4 Validación de negocio (en el Service, no en el DTO)

Reglas que dependen de la BD (email duplicado, no editar recursos eliminados) van en el `ServiceImpl`, lanzando excepciones de dominio:

```java
if (userRepository.findByEmail(dto.getEmail()).isPresent())
    throw new ConflictException("Email already registered");
```

---

## 8. Manejo global de errores

### 8.1 Problema que resuelve

Sin manejo centralizado, `IllegalStateException` u otros errores genéricos devuelven `500` con stack trace expuesto, aun cuando semánticamente debería ser `404`/`409`/`400`.

### 8.2 Jerarquía de excepciones propias

```java
public abstract class ApplicationException extends RuntimeException {
    private final HttpStatus status;
    protected ApplicationException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus getStatus() { return status; }
}

public class NotFoundException extends ApplicationException {
    public NotFoundException(String msg) { super(HttpStatus.NOT_FOUND, msg); }       // 404
}
public class ConflictException extends ApplicationException {
    public ConflictException(String msg) { super(HttpStatus.CONFLICT, msg); }         // 409
}
public class BadRequestException extends ApplicationException {
    public BadRequestException(String msg) { super(HttpStatus.BAD_REQUEST, msg); }    // 400
}
```

### 8.3 Formato único de error (`ErrorResponse`)

```json
{
  "timestamp": "2026-01-15T10:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "User not found",
  "path": "/api/users/999",
  "details": { "name": "El nombre es obligatorio" }   // solo en errores de validación
}
```

### 8.4 `GlobalExceptionHandler` (`@RestControllerAdvice`)

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApplicationException.class)   // Not/Conflict/BadRequest propias
    public ResponseEntity<ErrorResponse> handleApp(ApplicationException ex, HttpServletRequest req) { ... }

    @ExceptionHandler(MethodArgumentNotValidException.class)  // @Valid en @RequestBody falla
    public ResponseEntity<ErrorResponse> handleValidation(...) { ... }   // 400 + details

    @ExceptionHandler(BindException.class)           // @Valid en @ModelAttribute (query params) falla
    public ResponseEntity<ErrorResponse> handleBind(...) { ... }         // 400 + details

    @ExceptionHandler(AuthorizationDeniedException.class)  // @PreAuthorize rechaza (rol insuficiente)
    public ResponseEntity<ErrorResponse> handleAuthzDenied(...) { ... }  // 403

    @ExceptionHandler(AccessDeniedException.class)   // ownership rechazado desde el service
    public ResponseEntity<ErrorResponse> handleAccessDenied(...) { ... } // 403

    @ExceptionHandler(AuthenticationException.class) // credenciales inválidas
    public ResponseEntity<ErrorResponse> handleAuthException(...) { ... } // 401

    @ExceptionHandler(Exception.class)                // catch-all, SIEMPRE al final
    public ResponseEntity<ErrorResponse> handleUnexpected(...) { ... }   // 500, sin stacktrace al cliente
}
```

Con esto: servicios **nunca** construyen `ResponseEntity`, controladores **nunca** usan `try/catch`.

### 8.5 Mapa de códigos HTTP usados en el curso

| Código | Cuándo |
|---|---|
| 200 | OK |
| 201 | Creado (POST exitoso) |
| 204 | Sin contenido (DELETE exitoso) |
| 400 | Validación de DTO o de negocio fallida |
| 401 | Sin token / token inválido o expirado |
| 403 | Autenticado pero sin rol / no es dueño del recurso |
| 404 | Recurso inexistente o eliminado lógicamente |
| 409 | Conflicto (duplicado: email, nombre, etc.) |
| 500 | Error inesperado no controlado |

---

## 9. Relaciones entre entidades JPA

### 9.1 Tipos

| Relación | Anotación | Ejemplo del curso |
|---|---|---|
| Muchos a uno | `@ManyToOne` | `Product.owner → User`, `Product.category → Category` |
| Muchos a muchos | `@ManyToMany` + `@JoinTable` | `Product.categories ↔ Category.products` |

### 9.2 `@ManyToOne` con clave foránea

```java
@Entity @Table(name = "products")
public class ProductEntity extends BaseEntity {
    private String name; private Double price; private Integer stock;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity owner;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private CategoryEntity category;
}
```

`FetchType.LAZY` (recomendado en APIs REST): la entidad relacionada no se carga hasta que se accede a ella → evita consultas/carga innecesaria. `EAGER` carga siempre (usado deliberadamente en `UserEntity.roles` porque Spring Security necesita los roles inmediatamente al autenticar).

### 9.3 `@ManyToMany` (evolución de Product–Category)

```java
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(
    name = "product_categories",
    joinColumns = @JoinColumn(name = "product_id"),
    inverseJoinColumns = @JoinColumn(name = "category_id"))
private Set<CategoryEntity> categories = new HashSet<>();
```

Genera tabla intermedia `product_categories(product_id, category_id)`. Al consultar con `JOIN`, usar `SELECT DISTINCT` para evitar productos duplicados por múltiples categorías coincidentes.

### 9.4 Validar existencia de relaciones antes de guardar

```java
UserEntity owner = userRepository.findById(dto.getUserId())
        .orElseThrow(() -> new NotFoundException("User not found"));
if (owner.isDeleted()) throw new NotFoundException("User not found");
```

### 9.5 DTO de respuesta con objetos anidados

```json
{
  "id": 1, "name": "Laptop Gaming", "price": 1200.0,
  "owner": { "id": 1, "name": "Juan Pérez", "email": "juan@ups.edu.ec" },
  "categories": [ { "id": 1, "name": "Electrónicos" }, { "id": 2, "name": "Gaming" } ]
}
```

### 9.6 Consultas relacionales por convención

```java
List<ProductEntity> findByOwner_IdAndDeletedFalse(Long ownerId);
List<ProductEntity> findByCategory_IdAndDeletedFalse(Long categoryId);
```

---

## 10. Request params, `@ModelAttribute` y filtros dinámicos

### 10.1 Navegación vs consulta explícita

Se prefiere **consulta explícita** (repositorio del recurso a listar) en vez de navegar colecciones desde la entidad padre (`user.getProducts()`), porque permite filtrar en BD, evita N+1 y controla el SQL generado.

### 10.2 Endpoints semánticos anidados

```
GET /api/users/{id}/products?name=laptop&minPrice=500&maxPrice=1500&categoryId=2
GET /api/categories/{id}/products?name=gaming&userId=1
```

El recurso principal de la URL (`users/{id}`) da el **contexto**, pero la lógica se delega al `ProductService`/`ProductRepository` porque el recurso final consultado es `products`.

### 10.3 DTO de filtros vía query params

```java
public class ProductFilterByUserDto {
    @Size(min = 2, max = 150) private String name;
    @DecimalMin("0.0") private Double minPrice;
    @DecimalMin("0.0") private Double maxPrice;
    @Min(1) private Long categoryId;

    public boolean hasValidPriceRange() {
        return minPrice == null || maxPrice == null || maxPrice >= minPrice;
    }
}
```

```java
@GetMapping("/{id}/products")
public List<ProductResponseDto> findByUser(
        @PathVariable Long id,
        @Valid @ModelAttribute ProductFilterByUserDto filters) {
    return productService.findByUserIdWithFilters(id, filters);
}
```

`@ModelAttribute` construye el DTO desde query params; si `@Valid` falla, Spring lanza `BindException` (no `MethodArgumentNotValidException` — requiere handler propio, ver §8.4).

### 10.4 Consulta dinámica con `@Query` (filtros opcionales)

```java
@Query("""
    SELECT p FROM ProductEntity p
    WHERE p.deleted = false AND p.owner.id = :userId
      AND (COALESCE(:name,'') = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%',:name,'%')))
      AND (:minPrice IS NULL OR p.price >= :minPrice)
      AND (:maxPrice IS NULL OR p.price <= :maxPrice)
    """)
List<ProductEntity> findByOwnerIdWithFilters(@Param("userId") Long userId, @Param("name") String name,
        @Param("minPrice") Double minPrice, @Param("maxPrice") Double maxPrice);
```

Patrón `(:param IS NULL OR campo = :param)`: si el filtro llega nulo, la condición se ignora.

---

## 11. Paginación (`Page` / `Slice` / `Pageable`)

### 11.1 Por qué

`findAll()` sin paginar en tablas grandes → respuestas de varios MB y segundos de latencia. Se limita con `LIMIT`/`OFFSET` generado por Spring Data.

### 11.2 `Page` vs `Slice`

| Aspecto | `Page` | `Slice` |
|---|---|---|
| Datos | Sí | Sí |
| `totalElements` / `totalPages` | Sí | No |
| Ejecuta `COUNT` | Sí (2 queries) | No (1 query, más liviano) |
| Uso típico | Tablas administrativas | Scroll infinito / next-prev |

### 11.3 DTO de paginación

```java
public class PaginationDto {
    @Min(0) private int page = 0;
    @Min(1) @Max(100) private int size = 10;
    private String sortBy = "id";
    private String direction = "asc";
}
```

### 11.4 Repositorio

```java
@Query(value = "SELECT p FROM ProductEntity p WHERE p.deleted = false",
       countQuery = "SELECT COUNT(p) FROM ProductEntity p WHERE p.deleted = false")
Page<ProductEntity> findActivePage(Pageable pageable);

@Query("SELECT p FROM ProductEntity p WHERE p.deleted = false")
Slice<ProductEntity> findActiveSlice(Pageable pageable);
```

### 11.5 Construcción de `Pageable` (con lista blanca de campos ordenables)

```java
private Pageable createPageable(PaginationDto p) {
    Set<String> allowed = Set.of("id","name","price","stock","createdAt","updatedAt");
    if (!allowed.contains(p.getSortBy())) throw new BadRequestException("Campo no permitido: " + p.getSortBy());
    Sort.Direction dir = "desc".equalsIgnoreCase(p.getDirection()) ? Sort.Direction.DESC : Sort.Direction.ASC;
    return PageRequest.of(p.getPage(), p.getSize(), Sort.by(dir, p.getSortBy()));
}
```

No se pagina/ordena directamente por campos de relaciones (`owner.name`) sin JOIN explícito — requiere tratamiento aparte.

### 11.6 Endpoints resultantes

```
GET /api/products              (sin paginar — reservado a ADMIN, ver §12)
GET /api/products/page?page=0&size=5&sortBy=price&direction=desc
GET /api/products/slice?page=0&size=5
```

---

## 12. Seguridad — Autenticación con JWT

### 12.1 Dependencias

```gradle
implementation("org.springframework.boot:spring-boot-starter-security")
implementation("io.jsonwebtoken:jjwt-api:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
```

### 12.2 Configuración (`application.yml`)

```yaml
jwt:
  secret: ${JWT_SECRET:mySecretKeyForJWT2024MustBeAtLeast256BitsLongForHS256Algorithm}
  expiration: 1800000          # access token: 30 min
  refresh-expiration: 604800000 # refresh token: 7 días
  issuer: fundamentos01-api
  header: Authorization
  prefix: "Bearer "
```

`JwtProperties` mapea esto vía `@ConfigurationProperties(prefix = "jwt")`.

### 12.3 Modelo de roles (tabla separada, `ManyToMany`)

```java
public enum RoleName { ROLE_USER, ROLE_ADMIN }

@Entity @Table(name = "roles")
public class RoleEntity extends BaseEntity {
    @Enumerated(EnumType.STRING) @Column(unique = true) private RoleName name;
    private String description;
}
```

```java
// en UserEntity
@ManyToMany(fetch = FetchType.EAGER)   // EAGER: Spring Security necesita roles al autenticar
@JoinTable(name = "user_roles",
    joinColumns = @JoinColumn(name = "user_id"),
    inverseJoinColumns = @JoinColumn(name = "role_id"))
private Set<RoleEntity> roles = new HashSet<>();
```

Se prefiere tabla `roles` separada sobre un campo `String role` simple o `List<String>`: permite reutilización, auditoría y escalar a permisos granulares.

### 12.4 `JwtUtil` — generación y validación (resumen funcional)

Responsabilidades:
- `generateAccessToken(Authentication)` / `generateAccessTokenFromUserDetails(...)` → JWT con claim `type=access`.
- `generateRefreshToken(UserDetailsImpl)` → JWT con claim `type=refresh`, expiración larga.
- Claims incluidos: `sub` (userId), `email`, `name`, `roles` (CSV), `type`, `iss`, `iat`, `exp`.
- Firma: `Jwts.builder()...signWith(key, Jwts.SIG.HS256).compact()`, con `key = Keys.hmacShaKeyFor(secret.getBytes())`.
- `validateToken(token)` → valida firma/formato/expiración (try/catch sobre `SignatureException`, `ExpiredJwtException`, `MalformedJwtException`, etc.).
- `validateAccessToken(token)` / `validateRefreshToken(token)` → además exige que el claim `type` coincida (evita usar un refresh token como Bearer).
- `getUserIdFromToken`, `getEmailFromToken`, `getTokenType` → extraen claims tras verificar firma.

### 12.5 `UserDetailsImpl` (adaptador Entity → Spring Security)

```java
public class UserDetailsImpl implements UserDetails {
    private final Long id; private final String name, email, password;
    private final Collection<? extends GrantedAuthority> authorities;

    public static UserDetailsImpl build(UserEntity user) {
        var authorities = user.getRoles().stream()
            .map(r -> new SimpleGrantedAuthority(r.getName().name())).toList();
        return new UserDetailsImpl(user.getId(), user.getName(), user.getEmail(), user.getPasswordHash(), authorities);
    }
    // getAuthorities/getPassword/getUsername(=email)/isAccountNonExpired.../isEnabled → true fijo
}
```

### 12.6 `UserDetailsServiceImpl`

```java
@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    @Override
    public UserDetails loadUserByUsername(String email) {
        UserEntity user = userRepository.findByEmailAndDeletedFalse(email)
            .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + email));
        return UserDetailsImpl.build(user);
    }
}
```

Se invoca (a) durante login (`AuthenticationManager` → `DaoAuthenticationProvider`) y (b) en cada request autenticado (`JwtAuthenticationFilter`).

### 12.7 `JwtAuthenticationFilter` (`OncePerRequestFilter`)

Flujo por cada request:
```
1. Extrae header Authorization → quita prefijo "Bearer "
2. jwtUtil.validateAccessToken(jwt)  // rechaza refresh tokens usados como Bearer
3. email = jwtUtil.getEmailFromToken(jwt)
4. userDetails = userDetailsService.loadUserByUsername(email)
5. Crea UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
6. SecurityContextHolder.getContext().setAuthentication(authentication)
7. filterChain.doFilter(request, response)  // continúa, con o sin autenticación
```

Errores se loguean, **nunca** se relanzan (dejar que Spring Security responda 401 vía `AuthenticationEntryPoint`).

### 12.8 `JwtAuthenticationEntryPoint`

Implementa `AuthenticationEntryPoint.commence(...)`: se dispara cuando el `SecurityContext` queda vacío al llegar a un endpoint protegido (sin token o token inválido). Construye un `ErrorResponse` (mismo formato del §8.3) con `401` y lo escribe directamente en `HttpServletResponse` — corre **antes** de llegar a cualquier controlador, por eso no puede resolverse con `@RestControllerAdvice`.

### 12.9 `SecurityConfig`

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)   // habilita @PreAuthorize
public class SecurityConfig {

    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean public DaoAuthenticationProvider authenticationProvider() {
        var p = new DaoAuthenticationProvider(userDetailsService);
        p.setPasswordEncoder(passwordEncoder());
        return p;
    }

    @Bean public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)                       // no aplica en API stateless
            .exceptionHandling(e -> e.authenticationEntryPoint(unauthorizedHandler))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/status/**", "/actuator/**").permitAll()
                .anyRequest().authenticated())
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

Claves: `csrf` deshabilitado (no hay cookies de sesión, JWT viaja en header), `STATELESS` (sin `HttpSession`), filtro JWT insertado **antes** del filtro estándar de usuario/password.

### 12.10 `AuthService` — login / register

```java
public AuthResponseDto login(LoginRequestDto req) {
    Authentication auth = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword()));
    SecurityContextHolder.getContext().setAuthentication(auth);
    String accessToken = jwtUtil.generateAccessToken(auth);
    // + genera/guarda refresh token, arma AuthResponseDto con roles
}

public AuthResponseDto register(RegisterRequestDto req) {
    if (userRepository.existsByEmail(req.getEmail())) throw new ConflictException("Email ya registrado");
    UserEntity user = new UserEntity();
    user.setPassword(passwordEncoder.encode(req.getPassword()));   // BCrypt, nunca texto plano
    user.setRoles(Set.of(roleRepository.findByName(RoleName.ROLE_USER).orElseThrow(...)));
    userRepository.save(user);
    // genera JWT y devuelve login automático tras registro
}
```

### 12.11 Endpoints de autenticación

```
POST /api/auth/register   (público)
POST /api/auth/login      (público)
```

---

## 13. Autorización por roles (`@PreAuthorize`)

### 13.1 Autenticación vs autorización

Autenticación = **¿quién eres?** (JWT válido). Autorización = **¿qué puedes hacer?** (rol / ownership).

### 13.2 Dos enfoques (se combinan)

1. **Global** en `SecurityConfig`: `.requestMatchers("/api/admin/**").hasRole("ADMIN")` — primera barrera, por patrón de URL.
2. **Por método** con `@PreAuthorize` — granular, expresivo, junto al código.

```java
@GetMapping
@PreAuthorize("hasRole('ADMIN')")     // hasRole añade el prefijo ROLE_ automáticamente
public List<ProductResponseDto> findAll() { return service.findAll(); }
```

| Expresión | Significado |
|---|---|
| `hasRole('ADMIN')` | solo `ROLE_ADMIN` |
| `hasAnyRole('USER','ADMIN')` | cualquiera de los dos |
| `hasAuthority('ROLE_ADMIN')` | igual que `hasRole` pero sin prefijo automático |
| `isAuthenticated()` | cualquier usuario logueado |

### 13.3 `@AuthenticationPrincipal` — obtener el usuario actual

```java
@GetMapping("/me")
public CurrentUserResponseDto me(@AuthenticationPrincipal UserDetailsImpl currentUser) {
    Set<String> roles = currentUser.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    return new CurrentUserResponseDto(currentUser.getId(), currentUser.getName(), currentUser.getEmail(), roles);
}
```

### 13.4 401 vs 403 (recordatorio)

| Código | Causa |
|---|---|
| 401 | sin token o token inválido |
| 403 | token válido, pero rol insuficiente (`@PreAuthorize`) u ownership rechazado |

---

## 14. Validación de Ownership (propiedad de recursos)

### 14.1 Problema

Un usuario autenticado con `ROLE_USER` podía editar/eliminar productos de **otro** usuario, porque hasta la práctica anterior solo se validaba autenticación + rol global.

### 14.2 Regla de negocio

```
ROLE_ADMIN → puede modificar/eliminar cualquier recurso.
ROLE_USER  → solo puede modificar/eliminar recursos donde owner.id == currentUser.id.
```

### 14.3 El owner sale del token, nunca del body

```java
@PostMapping
public ProductResponseDto create(@Valid @RequestBody CreateProductDto dto,
                                  @AuthenticationPrincipal UserDetailsImpl currentUser) {
    return service.create(dto, currentUser);   // el DTO YA NO trae userId
}
```

Enviar `userId` en el DTO permitiría a un usuario crear recursos a nombre de otro.

### 14.4 Validación en el `ServiceImpl`

```java
private void validateOwnership(ProductEntity product, UserDetailsImpl currentUser) {
    if (hasRole(currentUser, "ROLE_ADMIN")) return;                       // ADMIN pasa siempre
    if (!product.getOwner().getId().equals(currentUser.getId()))
        throw new AccessDeniedException("No puedes modificar productos ajenos");  // → 403
}

private boolean hasRole(UserDetailsImpl user, String role) {
    return user.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority).anyMatch(a -> a.equals(role));
}
```

Aplicado antes de `update`, `partialUpdate` y `delete`.

### 14.5 Tabla de diferencia de validaciones de seguridad

| Nivel | Mecanismo | Dónde | Código si falla |
|---|---|---|---|
| Autenticación | `JwtAuthenticationFilter` | filtro | 401 |
| Autorización por rol | `@PreAuthorize` | controlador | 403 |
| Ownership | validación manual en Service | servicio | 403 |

---

## 15. Refresh Tokens

### 15.1 Motivación

Access token corto (30 min) obliga a re-login frecuente. Se agrega un **refresh token** de vida larga (7 días) para renovar sin pedir credenciales de nuevo.

### 15.2 Diferenciación por claim `type`

Cada JWT incluye `claim("type", "access"|"refresh")`. `JwtAuthenticationFilter` solo acepta `validateAccessToken`; el endpoint de refresh solo acepta `validateRefreshToken`. Esto evita que un refresh token se use como Bearer en endpoints normales.

### 15.3 Persistencia del refresh token (permite revocación)

```java
@Entity @Table(name = "refresh_tokens")
public class RefreshTokenEntity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private UserEntity user;
    @Column(nullable = false, unique = true, length = 1000) private String token;
    @Column(nullable = false) private LocalDateTime expiresAt;
    @Column(nullable = false) private boolean revoked = false;

    public boolean isExpired() { return expiresAt.isBefore(LocalDateTime.now()); }
}
```

> En producción se recomienda guardar un **hash** del token, no el valor completo (aquí se guarda completo por fines didácticos).

### 15.4 `RefreshTokenService` — ciclo de vida

- `createRefreshToken(user, userDetails)`: genera JWT + persiste con `expiresAt`.
- `validateAndGetActiveToken(token)`: valida firma+tipo, existencia en BD, no revocado, no expirado, usuario activo.
- `revoke(entity)`: marca `revoked = true`.
- `revokeAllByUser(user)`: usado en login para dejar **una sola sesión activa** (opcional según diseño).

### 15.5 Rotación de refresh token

Cada `/auth/refresh` **revoca** el refresh token usado y genera un par nuevo (access + refresh). Evita reutilización indefinida del mismo refresh token.

```
Login → accessToken A + refreshToken A
Refresh → revoca A → accessToken B + refreshToken B
Refresh → revoca B → accessToken C + refreshToken C
```

### 15.6 Endpoints agregados

```
POST /api/auth/refresh   { "refreshToken": "..." }  → nuevo accessToken + refreshToken
POST /api/auth/logout    { "refreshToken": "..." }  → revoca ese refresh token (204)
```

Ambos son públicos en `SecurityConfig` (`/auth/**`) porque no se validan con access token sino dentro del propio `AuthService`.

### 15.7 Expiración vs revocación

| Concepto | Significado |
|---|---|
| Expiración | pasó el tiempo de vida configurado |
| Revocación | el servidor lo invalidó explícitamente (logout, rotación) antes de expirar |

---

## 16. Documentación con Swagger / OpenAPI

### 16.1 Dependencia

```gradle
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
```

### 16.2 URLs (con `context-path: /api`)

```
http://localhost:8080/api/swagger-ui/index.html   # UI interactiva
http://localhost:8080/api/v3/api-docs             # JSON OpenAPI
```

### 16.3 Permitir Swagger en `SecurityConfig`

```java
.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
```

(sin el prefijo `/api`, porque Spring Security evalúa rutas internas antes del context-path).

### 16.4 `OpenApiConfig` — info + esquema Bearer

```java
@Configuration
public class OpenApiConfig {
    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        SecurityScheme bearer = new SecurityScheme()
            .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT");
        return new OpenAPI()
            .info(new Info().title("API PPW").version("1.0.0"))
            .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME, bearer));
    }
}
```

Esto habilita el botón **Authorize** en Swagger UI.

### 16.5 Anotaciones de documentación por endpoint

```java
@Tag(name = "Productos", description = "...")
@SecurityRequirement(name = "bearerAuth")     // a nivel de clase si TODO el controller requiere JWT
@RestController @RequestMapping("/products")
public class ProductsController {

    @Operation(summary = "Crear producto", description = "...")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Creado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "409", description = "Nombre duplicado")
    })
    @PostMapping
    public ProductResponseDto create(...) { ... }
}
```

DTOs se documentan con `@Schema(description=..., example=...)` a nivel de clase y de campo.

### 16.6 Errores comunes de Swagger

| Síntoma | Causa / solución |
|---|---|
| Swagger no abre | falta dependencia o mal la URL |
| Abre pero sin endpoints | `/v3/api-docs` devuelve 401 → falta `permitAll()` |
| No aparece botón Authorize | falta `OpenApiConfig` con `SecurityScheme` |
| Token no se envía | falta `@SecurityRequirement("bearerAuth")` en el controller |
| Error de auth en Swagger | se pegó `"token": "eyJ..."` en vez de solo `eyJ...` |

---

## 17. Despliegue en producción

### 17.1 JAR ejecutable vs WAR

Se usa **JAR** (incluye Tomcat embebido, `java -jar app.jar`), no WAR (requiere Tomcat externo, enfoque legacy).

### 17.2 Profiles por ambiente

```
application.yml        → configuración común
application-dev.yml    → valores por defecto de desarrollo (con fallback vía ${VAR:default})
application-prod.yml   → SIN valores por defecto (fallo explícito si falta una variable obligatoria)
```

Activación: `SPRING_PROFILES_ACTIVE=prod` (env var), `--spring.profiles.active=prod` (arg), o en el IDE.

Ejemplo `application-prod.yml` (fragmentos clave):
```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate      # nunca update/create en prod
server:
  port: ${PORT:8080}
  error:
    include-stacktrace: never
jwt:
  secret: ${JWT_SECRET}
```

### 17.3 Spring Boot Actuator (health checks)

```gradle
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

```
GET /actuator/health   → { "status": "UP" }
```

Se permite públicamente `/actuator/health` (o `/api/actuator/health` según context-path) y se protege el resto con `hasRole('ADMIN')`.

### 17.4 Dockerfile multi-stage (build con JDK, runtime con JRE, usuario no-root)

```dockerfile
# ETAPA 1: build
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace/app
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar -x test --no-daemon
RUN mkdir -p build/dependency && cd build/dependency && jar -xf ../libs/app.jar

# ETAPA 2: runtime
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring spring
ARG DEPENDENCY=/workspace/app/build/dependency
COPY --from=builder --chown=spring:spring ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=builder --chown=spring:spring ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=builder --chown=spring:spring ${DEPENDENCY}/BOOT-INF/classes /app
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java","-Xms256m","-Xmx512m","-cp","/app:/app/lib/*","ec.edu.ups.icc.fundamentos01.Fundamentos01Application"]
```

Separar `BOOT-INF/lib` (dependencias, cambian poco) de `BOOT-INF/classes` (código, cambia seguido) permite a Docker **reutilizar capas** cuando solo cambia el código.

Config no sensible fijada con `ENV` está bien; **credenciales nunca en el Dockerfile** — se entregan con `--env-file` o variables al hacer `docker run`.

### 17.5 `.dockerignore`

Excluir: `.gradle/`, `build/`, `.git/`, `.idea/`, `.env*`, `*.log`. **No** excluir `gradlew`, `gradle/`, `build.gradle.kts`, `src/`.

### 17.6 Levantar API + Nginx sin Docker Compose (comandos sueltos)

```bash
docker network create app-network

docker run -d --name mi-api --network app-network \
  --env-file .env.prod mi-api:1.0     # sin publicar puerto: solo visible dentro de la red

docker run -d --name nginx --network app-network -p 80:80 \
  -v "$(pwd)/nginx/default.conf:/etc/nginx/conf.d/default.conf:ro" \
  nginx:alpine
```

`nginx/default.conf` (reverse proxy):
```nginx
upstream spring_backend { server mi-api:8080; }
server {
    listen 80;
    location / {
        proxy_pass http://spring_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Dentro de la red Docker, los contenedores se resuelven por **nombre** (`mi-api`), no por `localhost`.

### 17.7 Despliegue nativo con systemd (sin Docker)

```ini
[Unit]
Description=Spring Boot API
After=network.target

[Service]
User=miapi
EnvironmentFile=/opt/mi-api/.env
ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar /opt/mi-api/mi-api.jar --spring.profiles.active=prod
Restart=always
[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now mi-api
sudo journalctl -u mi-api -f
```

### 17.8 PaaS (Render / Railway / Heroku) — portabilidad de la misma imagen

La **misma imagen Docker** sirve para local, Ubuntu Server y PaaS: el ambiente se selecciona 100% por variables de entorno (`SPRING_PROFILES_ACTIVE`, `DATABASE_URL`, `PORT`, `JWT_SECRET`, ...), nunca hardcodeado ni reconstruyendo la imagen.

| PaaS | Free tier | Notas |
|---|---|---|
| Render | 750h/mes | `render.yaml` (Blueprint), health check en `/actuator/health` |
| Railway | $5 crédito/mes | PostgreSQL con 1 click |
| Heroku | desde $7/mes | `Procfile` + `system.properties` |

### 17.9 Buenas prácticas de producción (checklist)

- Profiles `dev`/`prod`, secretos solo por variables de entorno, `.env*` fuera de git.
- `ddl-auto: validate` en producción (nunca `update`/`create`).
- `server.error.include-stacktrace: never`.
- HTTPS obligatorio (Nginx + Let's Encrypt o TLS del PaaS).
- Health checks con Actuator + `HEALTHCHECK` en Docker.
- Usuario no-root dentro del contenedor.
- Logs a `WARN`/`INFO` en prod (no `DEBUG`).

---

## 18. Errores comunes (troubleshooting rápido)

| Problema | Causa típica |
|---|---|
| Controller/Service no se detecta | está fuera del paquete raíz del `@ComponentScan` |
| `500` en vez de `404`/`409` | falta excepción de dominio + handler en `GlobalExceptionHandler` |
| `401` en endpoint que debería ser público | falta agregarlo en `permitAll()` de `SecurityConfig` |
| `403` inesperado | rol insuficiente (`@PreAuthorize`) u ownership (owner distinto al usuario actual) |
| Refresh token usado como Bearer no debería funcionar | verificar `validateAccessToken` vs `validateRefreshToken` (claim `type`) |
| `LazyInitializationException` | acceso a relación `LAZY` fuera de sesión transaccional / fuera del mapper correcto |
| Producto/entidad "fantasma" tras eliminar | falta filtrar por `deleted = false` en las consultas de lectura |
| `502 Bad Gateway` (Nginx) | contenedor de la API caído o fuera de la red Docker; nombre mal escrito en `upstream` |
| JAR no arranca ("No main manifest attribute") | falta `springBoot { mainClass.set(...) }` / `tasks.bootJar { archiveFileName.set(...) }` |
| Swagger sin botón Authorize | falta `SecurityScheme` en `OpenApiConfig` |

---

## 19. Checklist para aplicar en el proyecto final

- [ ] Definir dominios/módulos (carpetas por recurso, no por capa técnica global).
- [ ] Por cada recurso: `entities/`, `models/` (si se sigue el patrón Model+Entity), `dtos/` (Create/Update/PartialUpdate/Response), `mappers/`, `repositories/`, `services/` (interfaz + Impl), `controllers/`.
- [ ] `BaseEntity` con `id`, `createdAt`, `updatedAt`, `deleted` (soft delete).
- [ ] Validación Jakarta en todos los DTOs de entrada + `@Valid` en controladores.
- [ ] `core/exceptions/` con jerarquía `ApplicationException` + `GlobalExceptionHandler` + `ErrorResponse` uniforme.
- [ ] Relaciones `@ManyToOne`/`@ManyToMany` con `FetchType.LAZY` salvo roles de seguridad.
- [ ] Paginación (`Page`/`Slice`) en listados que puedan crecer.
- [ ] Seguridad JWT completa: registro, login, roles, `@PreAuthorize`, ownership, refresh token.
- [ ] Nunca recibir `userId`/owner desde el body: siempre desde `@AuthenticationPrincipal`.
- [ ] Documentar con Swagger/OpenAPI (`@Tag`, `@Operation`, `@ApiResponses`, `@SecurityRequirement`).
- [ ] `application-dev.yml` / `application-prod.yml` separados; secretos por variables de entorno.
- [ ] Dockerfile multi-stage + `.dockerignore` + Nginx reverse proxy si aplica.
- [ ] Probar todo con Postman/Bruno: casos felices, 400, 401, 403, 404, 409.
