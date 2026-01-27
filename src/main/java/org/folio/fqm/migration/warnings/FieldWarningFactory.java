package org.folio.fqm.migration.warnings;

import org.apache.commons.lang3.function.TriFunction;

/** Factory for field warnings; accepts (fieldPrefix, field, (nullable) fql) and returns a warning */
public interface FieldWarningFactory extends TriFunction<String, String, String, FieldWarning> {}
