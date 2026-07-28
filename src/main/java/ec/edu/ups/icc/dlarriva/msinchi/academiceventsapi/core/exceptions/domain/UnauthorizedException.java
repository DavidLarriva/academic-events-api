package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.base.ApplicationException;
import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApplicationException {

    public UnauthorizedException(String message) {
        this("UNAUTHORIZED", message);
    }

    public UnauthorizedException(String code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message);
    }
}
