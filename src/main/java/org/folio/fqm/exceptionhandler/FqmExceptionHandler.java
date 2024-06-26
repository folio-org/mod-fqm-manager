package org.folio.fqm.exceptionhandler;

import lombok.extern.slf4j.Slf4j;
import org.folio.fqm.domain.dto.Error;
import org.folio.fqm.exception.FqmException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
@Slf4j
public class FqmExceptionHandler extends ResponseEntityExceptionHandler {
  @ExceptionHandler({EmptyResultDataAccessException.class})
  public ResponseEntity<String> handleGenericExceptions(Exception ex, WebRequest request) {
    logger.error("Unexpected exception: " + ex.getMessage(), ex);
    return new ResponseEntity<>(ex.getLocalizedMessage(), new HttpHeaders(), HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(FqmException.class)
  public ResponseEntity<Error> handleFqmExceptions(FqmException exception,
                                                   ServletWebRequest webRequest) {
    String url = webRequest.getHttpMethod() + " " + webRequest.getRequest().getRequestURI();
    log.error("Request failed. URL: {}. Failure reason : {}", url, exception.getMessage());
    return new ResponseEntity<>(exception.getError(), exception.getHttpStatus());
  }
}
