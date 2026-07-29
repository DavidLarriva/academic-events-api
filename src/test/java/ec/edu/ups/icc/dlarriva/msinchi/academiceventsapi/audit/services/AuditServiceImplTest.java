package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.entities.AuditLogEntity;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.enums.AuditResult;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.audit.repositories.AuditLogRepository;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.filters.CorrelationIdFilter;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.utils.ClientIpResolver;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.users.entities.UserEntity;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private HttpServletRequest request;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ClientIpResolver clientIpResolver = new ClientIpResolver();

    private AuditServiceImpl auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditServiceImpl(auditLogRepository, entityManager, clientIpResolver, request);
    }

    @Test
    void recordAttachesActorReferenceWithoutSelectWhenActorIdPresent() {
        when(request.getRemoteAddr()).thenReturn("192.168.1.10");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/events");
        when(request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME)).thenReturn("corr-123");

        UserEntity actorRef = new UserEntity();
        actorRef.setId(7L);
        when(entityManager.getReference(UserEntity.class, 7L)).thenReturn(actorRef);

        auditService.record(7L, "EVENT_CREATED", "EVENT", 55L, null, Map.of("title", "Taller"),
                AuditResult.SUCCESS);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLogEntity saved = captor.getValue();

        assertEquals(actorRef, saved.getActor());
        assertEquals("EVENT_CREATED", saved.getAction());
        assertEquals("EVENT", saved.getResourceType());
        assertEquals(55L, saved.getResourceId());
        assertEquals(AuditResult.SUCCESS, saved.getResult());
        assertEquals("192.168.1.10", saved.getIpAddress());
        assertEquals("POST", saved.getHttpMethod());
        assertEquals("/api/events", saved.getEndpoint());
        assertEquals("corr-123", saved.getCorrelationId());
        assertNull(saved.getPreviousValue());
    }

    @Test
    void recordLeavesActorNullWhenActorIdIsNull() {
        when(request.getRemoteAddr()).thenReturn("192.168.1.10");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME)).thenReturn("corr-999");

        auditService.record(null, "LOGIN_FAILED", "USER", null, null,
                Map.of("email", "nadie@academic.test"), AuditResult.FAILED);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLogEntity saved = captor.getValue();

        assertNull(saved.getActor());
        assertEquals(AuditResult.FAILED, saved.getResult());
        verify(entityManager, never()).getReference(eq(UserEntity.class), any());
    }

    @Test
    void recordSerializesPreviousAndNewValueAsJson() throws Exception {
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getMethod()).thenReturn("PATCH");
        when(request.getRequestURI()).thenReturn("/api/registrations/1/status");
        when(request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME)).thenReturn(null);

        auditService.record(3L, "REGISTRATION_STATUS_CHANGED", "REGISTRATION", 1L,
                Map.of("status", "PENDING"), Map.of("status", "CONFIRMED"), AuditResult.SUCCESS);

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLogEntity saved = captor.getValue();

        JsonNode previous = objectMapper.readTree(saved.getPreviousValue());
        JsonNode updated = objectMapper.readTree(saved.getNewValue());
        assertEquals("PENDING", previous.get("status").asText());
        assertEquals("CONFIRMED", updated.get("status").asText());
    }

    @Test
    void recordSuccessDelegatesWithSuccessResultAndPreviousValue() {
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/auth/register");
        when(request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME)).thenReturn(null);

        auditService.recordSuccess(9L, "REGISTER_SUCCESS", "USER", 9L, null,
                Map.of("email", "nueva@academic.test"));

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals(AuditResult.SUCCESS, captor.getValue().getResult());
    }

    @Test
    void recordFailureDelegatesWithFailedResultAndNullPreviousValue() {
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME)).thenReturn(null);

        auditService.recordFailure(null, "LOGIN_FAILED", "USER", null, Map.of("email", "x@academic.test"));

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLogEntity saved = captor.getValue();
        assertEquals(AuditResult.FAILED, saved.getResult());
        assertNull(saved.getPreviousValue());
    }
}
