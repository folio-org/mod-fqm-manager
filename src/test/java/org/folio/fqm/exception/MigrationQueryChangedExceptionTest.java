package org.folio.fqm.exception;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.folio.fqm.domain.dto.Error;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.warnings.Warning;
import org.folio.spring.i18n.service.TranslationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MigrationQueryChangedExceptionTest {

  private static final UUID ENTITY_TYPE_ID = UUID.fromString("83376069-0de9-5900-9a8c-38e50fc4ac4d");
  private static final String FQL_QUERY = "{\"_version\":\"1\",\"test\":{\"\":\"foo\"}}";
  private static final List<String> FIELDS = List.of("field1", "field2");
  private static final MigratableQueryInformation MIGRATABLE_QUERY_INFORMATION_FULL = MigratableQueryInformation
    .builder()
    .entityTypeId(ENTITY_TYPE_ID)
    .fqlQuery(FQL_QUERY)
    .fields(FIELDS)
    .build();

  @Test
  void testConstructorAndBasicProperties() throws IOException {
    MigrationQueryChangedException exception = new MigrationQueryChangedException(MIGRATABLE_QUERY_INFORMATION_FULL);

    assertThat(exception.getMessage(), is("Migration changed more than just the version"));
    assertThat(exception.getHttpStatus(), is(HttpStatus.BAD_REQUEST));
    assertThat(exception.getMigratedQueryInformation(), is(MIGRATABLE_QUERY_INFORMATION_FULL));

    Error error = exception.getError();

    assertThat(error.getMessage(), is("Migration changed more than just the version"));
    assertThat(error.getCode(), is(Error.CodeEnum.MIGRATION_QUERY_CHANGED));
    assertThat(
      error.getParameters(),
      contains(allOf(hasProperty("key", equalTo("migratedQueryInformation")), hasProperty("value", notNullValue())))
    );

    String parameterValue = error.getParameters().get(0).getValue();
    assertThat(
      new ObjectMapper().readValue(parameterValue, MigratableQueryInformation.class),
      is(equalTo(MIGRATABLE_QUERY_INFORMATION_FULL))
    );
  }

  @Test
  void testGetErrorWithNullMigratableQueryInformation() {
    MigrationQueryChangedException exception = new MigrationQueryChangedException(null);
    Error error = exception.getError();

    assertThat(error, is(notNullValue()));
    assertThat(error.getParameters(), is(anyOf(nullValue(), empty())));
  }

  @Test
  void testGetErrorWithPartialMigratableQueryInformation() throws IOException {
    MigratableQueryInformation migratedQueryInformation = MigratableQueryInformation
      .builder()
      .entityTypeId(ENTITY_TYPE_ID)
      .fqlQuery(null)
      .fields(null)
      .build();

    MigrationQueryChangedException exception = new MigrationQueryChangedException(migratedQueryInformation);
    Error error = exception.getError();

    assertThat(
      error.getParameters(),
      contains(allOf(hasProperty("key", equalTo("migratedQueryInformation")), hasProperty("value", notNullValue())))
    );

    String parameterValue = error.getParameters().get(0).getValue();
    assertThat(
      new ObjectMapper().readValue(parameterValue, MigratableQueryInformation.class),
      is(equalTo(migratedQueryInformation))
    );
  }

  @Test
  void testGetErrorWithSerializationError() {
    // mocks always fail Jackson serialization
    final class UnserializableWarning implements Warning {

      private final UnserializableWarning self = this;

      @Override
      public String toString() {
        return self.getClass().getName();
      }

      @Override
      public WarningType getType() {
        throw new UnsupportedOperationException("Unimplemented method 'getType'");
      }

      @Override
      public String getDescription(TranslationService translationService) {
        throw new UnsupportedOperationException("Unimplemented method 'getDescription'");
      }
    }

    MigratableQueryInformation explosiveInformation = MIGRATABLE_QUERY_INFORMATION_FULL.withWarnings(
      List.of(new UnserializableWarning())
    );
    MigrationQueryChangedException exception = new MigrationQueryChangedException(explosiveInformation);
    Error error = exception.getError();

    assertThat(
      error.getParameters(),
      contains(
        allOf(
          hasProperty("key", equalTo("serializationError")),
          hasProperty("value", equalTo("Failed to serialize migrated query information"))
        ),
        allOf(
          hasProperty("key", equalTo("migratedQueryInformationText")),
          hasProperty("value", equalTo(explosiveInformation.toString()))
        )
      )
    );
  }
}
