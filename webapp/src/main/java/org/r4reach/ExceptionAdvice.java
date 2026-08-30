package org.r4reach;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Exception handler advice class for all SpringMVC controllers.
 *
 * <p>Maps: <br>
 * - IllegalArgumentException -> HTTP 400
 */
@org.springframework.web.bind.annotation.ControllerAdvice
public class ExceptionAdvice {

  /** Handles ResourceExceptions for the SpringMVC controllers. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleException(IllegalArgumentException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body("Invalid request. " + (e.getMessage() != null ? e.getMessage() : ""));
  }
}
