package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.base.ApplicationException;
import org.springframework.http.HttpStatus;

public class ConflictException extends ApplicationException {

    public ConflictException(String message) {
        this("CONFLICT", message);
    }

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
