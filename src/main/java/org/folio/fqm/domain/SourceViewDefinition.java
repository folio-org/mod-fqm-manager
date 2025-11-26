package org.folio.fqm.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collection;
import java.util.List;
import javax.annotation.CheckForNull;
import lombok.With;

/**
 * Container for view definitions read from JSON(5) files in src/main/resources/db/source-views
 */
@With
public record SourceViewDefinition(
  String name,
  @CheckForNull List<SourceViewDependency> dependsOn,
  String sql,
  @JsonIgnore String sourceFilePath
) {
  @JsonFormat(shape = Shape.ARRAY)
  public record SourceViewDependency(String schema, String table) {}

  public boolean isAvailable(Collection<SourceViewDependency> availableDependencies) {
    return this.dependsOn == null || availableDependencies.containsAll(this.dependsOn);
  }
}
