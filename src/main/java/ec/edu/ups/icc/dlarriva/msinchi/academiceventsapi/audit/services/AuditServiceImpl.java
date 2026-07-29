package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.dtos.AuditLogFilterDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.dtos.AuditLogResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.entities.AuditLogEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.enums.AuditResult;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.mappers.AuditLogMapper;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.repositories.AuditLogRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PagedResponseDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.dtos.PaginationDto;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.filters.CorrelationIdFilter;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.utils.ClientIpResolver;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.entities.UserEntity;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class AuditServiceImpl implements AuditService {

    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "action", "resourceType", "result", "createdAt");

    /*
     * Instancia propia, no inyectada: spring-boot-starter-webmvc (starter
     * modular de Boot 4) no publica un bean ObjectMapper vía
     * JacksonAutoConfiguration como sí hacía spring-boot-starter-web en Boot
     * 3 (confirmado en runtime: NoSuchBeanDefinitionException al intentar
     * inyectarlo). No hace falta ningún módulo extra (JavaTimeModule, etc.)
     * porque acá solo se serializan Map<String,Object> con Strings/booleans
     * simples, igual que AuditLogMapper del lado de lectura.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AuditLogRepository auditLogRepository;
    private final EntityManager entityManager;
    private final ClientIpResolver clientIpResolver;
    private final HttpServletRequest request;

    public AuditServiceImpl(AuditLogRepository auditLogRepository, EntityManager entityManager,
                             ClientIpResolver clientIpResolver, HttpServletRequest request) {
        this.auditLogRepository = auditLogRepository;
        this.entityManager = entityManager;
        this.clientIpResolver = clientIpResolver;
        this.request = request;
    }

    /**
     * Sin try/catch alrededor del save: si la auditoría falla, se prefiere
     * que la request entera falle de forma visible (500 vía
     * GlobalExceptionHandler) a que un log de seguridad se pierda en
     * silencio. Cuando se llama dentro de un @Transactional de negocio (ej.
     * EventServiceImpl.create), participa de esa misma transacción a
     * propósito: si el evento no llega a crearse, tampoco debe quedar
     * registrado que se creó.
     */
    @Override
    public void record(Long actorId, String action, String resourceType, Long resourceId,
                        Map<String, Object> previousValue, Map<String, Object> newValue, AuditResult result) {
        AuditLogEntity entity = new AuditLogEntity();
        if (actorId != null) {
            // Referencia sin SELECT: solo se necesita el FK, no los datos del actor.
            entity.setActor(entityManager.getReference(UserEntity.class, actorId));
        }
        entity.setAction(action);
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setPreviousValue(toJson(previousValue));
        entity.setNewValue(toJson(newValue));
        entity.setResult(result);
        entity.setIpAddress(clientIpResolver.resolve(request));
        entity.setHttpMethod(request.getMethod());
        entity.setEndpoint(request.getRequestURI());
        entity.setCorrelationId((String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME));
        auditLogRepository.save(entity);
    }

    @Override
    public PagedResponseDto<AuditLogResponseDto> findPage(AuditLogFilterDto filters, PaginationDto pagination) {
        Pageable pageable = pagination.toPageable(ALLOWED_SORT_FIELDS);
        Page<AuditLogEntity> page = auditLogRepository.search(filters.getActorId(), filters.getAction(),
                filters.getResourceType(), filters.getFrom(), filters.getTo(), pageable);
        return PagedResponseDto.of(page, entity -> AuditLogMapper.toResponse(AuditLogMapper.toModel(entity)));
    }

    private String toJson(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar un valor de auditoría a JSON", e);
        }
    }
}
