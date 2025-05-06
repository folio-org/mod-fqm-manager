package org.folio.fqm.service;

import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * Provides current date and time services for the application.
 * This class is responsible for supplying the current system date.
 *
 * This service mostly exists to simplify testing, by providing a simple method to specify a constant time as "now"
 */
@Service
public class ClockService {
  public Date now() {
    return new Date();
  }
}
