package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.base.ApplicationException;
import org.springframework.http.HttpStatus;

public class NotFoundException extends ApplicationException {

    public NotFoundException(String message) {
        this("NOT_FOUND", message);
    }

    public NotFoundException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }
}
