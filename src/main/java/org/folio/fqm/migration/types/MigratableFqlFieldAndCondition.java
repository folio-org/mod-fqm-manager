package org.folio.fqm.migration.types;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.With;

@With
public record MigratableFqlFieldAndCondition(
  UUID entityTypeId,
  String fieldPrefix,
  String field,
  String operator,
  JsonNode value
)
  implements MigratableFqlField<MigratableFqlFieldAndCondition> {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  public JsonNode getConditionObject() {
    return objectMapper.createObjectNode().set(operator, value);
  }
  public JsonNode getFieldAndConditionObject() {
    return objectMapper.createObjectNode().set(fieldPrefix + field, this.getConditionObject());
  }
  public MigratableFqlFieldAndCondition dereferenced(UUID innerEntityTypeId, String sourceName, String remainder) {
    return new MigratableFqlFieldAndCondition(
      innerEntityTypeId,
      this.fieldPrefix + sourceName + ".",
      remainder,
      this.operator,
      this.value
    );
  }
}
