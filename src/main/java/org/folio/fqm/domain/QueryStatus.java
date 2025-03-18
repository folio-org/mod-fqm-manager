package org.folio.fqm.domain;

public enum QueryStatus {
  IN_PROGRESS("IN_PROGRESS"),
  SUCCESS("SUCCESS"),
  FAILED("FAILED"),
  CANCELLED("CANCELLED"),
  MAX_SIZE_EXCEEDED("MAX_SIZE_EXCEEDED");

  private final String status;

  QueryStatus(final String status) {
    this.status = status;
  }

  @Override
  public String toString() {
    return status;
  }
}
