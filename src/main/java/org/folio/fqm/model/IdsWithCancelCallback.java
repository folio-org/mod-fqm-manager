package org.folio.fqm.model;

import java.util.List;

public record IdsWithCancelCallback(List<String[]> ids, Runnable cancelFunction) {

  public void cancel() {
    cancelFunction.run();
  }
}
