package org.folio.fqm.migration.warnings;

import java.util.function.Function;

/** Factory for entity warnings; accepts (fql) and returns a warning */
public interface EntityTypeWarningFactory extends Function<String, EntityTypeWarning> {}
