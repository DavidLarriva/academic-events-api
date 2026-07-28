package ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.domain;

import ec.edu.ups.icc.dlarriva.msinchi.academiceventsapi.core.exceptions.base.ApplicationException;
import org.springframework.http.HttpStatus;

public class BadRequestException extends ApplicationException {

    public BadRequestException(String message) {
        this("BAD_REQUEST", message);
    }

    public BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
