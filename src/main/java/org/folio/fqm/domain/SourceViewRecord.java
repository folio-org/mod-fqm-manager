package org.folio.fqm.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DB object for source view records, from source_views table. Represents a
 * {@link SourceViewDefinition} after the view itself has been created
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "source_views")
public class SourceViewRecord {

  @Id
  private String name;

  private String definition;
  private String sourceFile;
  private Instant lastUpdated;
}
