package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.base.ApplicationException;
import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApplicationException {

    public ForbiddenException(String message) {
        this("FORBIDDEN", message);
    }

    public ForbiddenException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }
}
