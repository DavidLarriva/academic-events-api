package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.annotations;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RateLimitKeyStrategy;
import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.security.enums.RedisKeyPrefix;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un método de controlador con un límite de solicitudes
 * (docs/instrucciones.pdf sección 7), aplicado por RateLimitAspect. Reutilizable por
 * cualquier módulo futuro (eventos, sesiones, reportes...) sin duplicar la
 * lógica de conteo — solo se agrega la anotación con los valores de la
 * categoría que corresponda.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimit {

    RedisKeyPrefix prefix();

    int limit();

    long windowSeconds();

    RateLimitKeyStrategy keyStrategy();
}
