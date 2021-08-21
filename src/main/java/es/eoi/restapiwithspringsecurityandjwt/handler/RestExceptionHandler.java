package es.eoi.restapiwithspringsecurityandjwt.handler;

import es.eoi.restapiwithspringsecurityandjwt.RestApiWithSpringSecurityAndJwtApplication;
import es.eoi.restapiwithspringsecurityandjwt.exceptions.InvalidJwtAuthenticationException;
import es.eoi.restapiwithspringsecurityandjwt.exceptions.VehicleNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.ResponseEntity.notFound;
import static org.springframework.http.ResponseEntity.status;

@RestControllerAdvice(basePackageClasses = {RestApiWithSpringSecurityAndJwtApplication.class})
@Slf4j
public class RestExceptionHandler {
    
    @ExceptionHandler(value = {VehicleNotFoundException.class})
    public ResponseEntity vehicleNotFound(VehicleNotFoundException ex, WebRequest request) {
        log.debug("handling VehicleNotFoundException...");
        return notFound().build();
    }

    @ExceptionHandler(value = {InvalidJwtAuthenticationException.class})
    public ResponseEntity invalidJwtAuthentication(InvalidJwtAuthenticationException ex, WebRequest request) {
        log.debug("handling InvalidJwtAuthenticationException...");
        return status(UNAUTHORIZED).build();
    }
    
}
