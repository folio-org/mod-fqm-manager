package org.folio.fqm.exception;

/**
 * Runtime exception used when JSON serialization or deserialization fails in internal module code.
 */
public class JsonParsingException extends IllegalStateException {

  public JsonParsingException(Throwable cause) {
    super("Unable to process JSON", cause);
  }
}
