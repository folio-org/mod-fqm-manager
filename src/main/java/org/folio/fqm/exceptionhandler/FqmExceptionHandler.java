package org.folio.fqm.exceptionhandler;

import lombok.extern.log4j.Log4j2;
import org.folio.fqm.domain.dto.Error;
import org.folio.fqm.exception.FqmException;
import org.folio.fqm.exception.UnknownException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Log4j2
@ControllerAdvice
public class FqmExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(FqmException.class)
  public ResponseEntity<Error> handleFqmExceptions(FqmException exception, ServletWebRequest webRequest) {
    String url = webRequest.getHttpMethod() + " " + webRequest.getRequest().getRequestURI();
    log.error("Request failed with internal exception. URL: {}. Failure reason:", url, exception);
    return new ResponseEntity<>(exception.getError(), exception.getHttpStatus());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Error> handleGenericExceptions(Exception exception, ServletWebRequest webRequest) {
    log.error(
      "Request failed with an unhandled exception. URL: {} {}. Failure reason:",
      webRequest.getHttpMethod(),
      webRequest.getRequest().getRequestURI(),
      exception
    );
    FqmException fqmException = log.throwing(new UnknownException(exception));
    return new ResponseEntity<>(fqmException.getError(), fqmException.getHttpStatus());
  }
}
