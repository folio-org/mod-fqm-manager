package org.folio.fqm.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.folio.fqm.domain.dto.Error;
import org.folio.fqm.domain.dto.Parameter;
import org.folio.fqm.migration.MigratableQueryInformation;
import org.folio.fqm.migration.warnings.Warning;
import org.folio.spring.i18n.service.TranslationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class MigrationQueryChangedExceptionTest {

    @Test
    void testConstructorAndBasicProperties() {
        // Create a MigratableQueryInformation object
        UUID entityTypeId = UUID.randomUUID();
        String fqlQuery = "{\"_version\":\"1.0\",\"test\":{\"$eq\":\"foo\"}}";
        List<String> fields = List.of("field1", "field2");
        MigratableQueryInformation migratedQueryInformation = MigratableQueryInformation.builder()
            .entityTypeId(entityTypeId)
            .fqlQuery(fqlQuery)
            .fields(fields)
            .build();

        // Create the exception
        MigrationQueryChangedException exception = new MigrationQueryChangedException(migratedQueryInformation);

        // Verify basic properties
        assertEquals("Migration changed more than just the version", exception.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals(migratedQueryInformation, exception.getMigratedQueryInformation());
    }

    @Test
    void testGetErrorWithCompleteMigratableQueryInformation() {
        // Create a MigratableQueryInformation object with all fields
        UUID entityTypeId = UUID.randomUUID();
        String fqlQuery = "{\"_version\":\"1.0\",\"test\":{\"$eq\":\"foo\"}}";
        List<String> fields = List.of("field1", "field2");
        MigratableQueryInformation migratedQueryInformation = MigratableQueryInformation.builder()
            .entityTypeId(entityTypeId)
            .fqlQuery(fqlQuery)
            .fields(fields)
            .build();

        // Create the exception
        MigrationQueryChangedException exception = new MigrationQueryChangedException(migratedQueryInformation);

        // Get the error
        Error error = exception.getError();

        // Verify error properties
        assertNotNull(error);
        assertEquals("Migration changed more than just the version", error.getMessage());
        assertEquals("migration.query.changed", error.getCode());

        // Verify parameters
        List<Parameter> parameters = error.getParameters();
        assertNotNull(parameters);
        assertEquals(1, parameters.size());

        Parameter parameter = parameters.get(0);
        assertEquals("migratedQueryInformation", parameter.getKey());
        assertNotNull(parameter.getValue());

        // Verify the parameter value contains the expected information
        String paramValue = parameter.getValue();
        assertTrue(paramValue.contains(entityTypeId.toString()), "Parameter value should contain entityTypeId");
        // The JSON serialization might escape quotes, so check for parts of the FQL query instead
        assertTrue(paramValue.contains("_version"), "Parameter value should contain _version field from FQL query");
        assertTrue(paramValue.contains("test"), "Parameter value should contain test field from FQL query");
        assertTrue(paramValue.contains("foo"), "Parameter value should contain foo value from FQL query");
        assertTrue(paramValue.contains("field1"), "Parameter value should contain field1");
        assertTrue(paramValue.contains("field2"), "Parameter value should contain field2");
    }

    @Test
    void testGetErrorWithNullMigratableQueryInformation() {
        // Create the exception with null MigratableQueryInformation
        MigrationQueryChangedException exception = new MigrationQueryChangedException(null);

        // Get the error
        Error error = exception.getError();

        // Verify error properties
        assertNotNull(error);
        assertEquals("Migration changed more than just the version", error.getMessage());
        assertEquals("migration.query.changed", error.getCode());

        // Verify parameters - should be empty or null since migratedQueryInformation is null
        List<Parameter> parameters = error.getParameters();
        assertTrue(parameters == null || parameters.isEmpty(),
            "Parameters should be null or empty when migratedQueryInformation is null");
    }

    @Test
    void testGetErrorWithPartialMigratableQueryInformation() {
        // Create a MigratableQueryInformation object with some null fields
        UUID entityTypeId = UUID.randomUUID();
        MigratableQueryInformation migratedQueryInformation = MigratableQueryInformation.builder()
            .entityTypeId(entityTypeId)
            .fqlQuery(null)
            .fields(null)
            .build();

        // Create the exception
        MigrationQueryChangedException exception = new MigrationQueryChangedException(migratedQueryInformation);

        // Get the error
        Error error = exception.getError();

        // Verify error properties
        assertNotNull(error);
        assertEquals("Migration changed more than just the version", error.getMessage());
        assertEquals("migration.query.changed", error.getCode());

        // Verify parameters
        List<Parameter> parameters = error.getParameters();
        assertNotNull(parameters);
        assertEquals(1, parameters.size());

        Parameter parameter = parameters.get(0);
        assertEquals("migratedQueryInformation", parameter.getKey());
        assertNotNull(parameter.getValue());

        // Verify the parameter value contains the expected information
        String paramValue = parameter.getValue();
        assertTrue(paramValue.contains(entityTypeId.toString()), "Parameter value should contain entityTypeId");
        assertTrue(paramValue.contains("null"), "Parameter value should contain null values");
    }

    @Test
    void testGetErrorWithSerializationError() throws JsonProcessingException {
        // Create a mock ObjectMapper that throws an exception when writeValueAsString is called
        ObjectMapper mockMapper = Mockito.mock(ObjectMapper.class);
        Mockito.when(mockMapper.writeValueAsString(Mockito.any()))
            .thenThrow(new JsonProcessingException("Test serialization error") {});

        // Create a test subclass that uses the mock ObjectMapper
        class TestMigrationQueryChangedException extends MigrationQueryChangedException {
            private final ObjectMapper objectMapper;

            public TestMigrationQueryChangedException(MigratableQueryInformation migratedQueryInformation, ObjectMapper objectMapper) {
                super(migratedQueryInformation);
                this.objectMapper = objectMapper;
            }

            @Override
            public Error getError() {
                Error error = new Error()
                    .message("Migration changed more than just the version")
                    .code("migration.query.changed");

                // Add the entire migrated query information as a single parameter
                if (getMigratedQueryInformation() != null) {
                    try {
                        String migratedQueryInfoJson = objectMapper.writeValueAsString(getMigratedQueryInformation());
                        error.addParametersItem(new Parameter().key("migratedQueryInformation").value(migratedQueryInfoJson));
                    } catch (JsonProcessingException e) {
                        // If serialization fails, fall back to MigratableQueryInformation.toString()
                        error.addParametersItem(new Parameter().key("serializationError").value("Failed to serialize migrated query information"));
                        error.addParametersItem(new Parameter().key("migratedQueryInformationText").value(getMigratedQueryInformation().toString()));
                    }
                }

                return error;
            }
        }

        // Create a MigratableQueryInformation object
        UUID entityTypeId = UUID.randomUUID();
        String fqlQuery = "{\"_version\":\"1.0\",\"test\":{\"$eq\":\"foo\"}}";
        List<String> fields = List.of("field1", "field2");
        MigratableQueryInformation migratedQueryInformation = MigratableQueryInformation.builder()
            .entityTypeId(entityTypeId)
            .fqlQuery(fqlQuery)
            .fields(fields)
            .build();

        // Create the exception with the mock ObjectMapper
        TestMigrationQueryChangedException exception = new TestMigrationQueryChangedException(migratedQueryInformation, mockMapper);

        // Get the error
        Error error = exception.getError();

        // Verify error properties
        assertNotNull(error);
        assertEquals("Migration changed more than just the version", error.getMessage());
        assertEquals("migration.query.changed", error.getCode());

        // Verify parameters - should include serialization error and fallback text
        List<Parameter> parameters = error.getParameters();
        assertNotNull(parameters);
        assertEquals(2, parameters.size());

        // First parameter should be the serialization error
        Parameter errorParam = parameters.get(0);
        assertEquals("serializationError", errorParam.getKey());
        assertEquals("Failed to serialize migrated query information", errorParam.getValue());

        // Second parameter should be the toString() representation
        Parameter textParam = parameters.get(1);
        assertEquals("migratedQueryInformationText", textParam.getKey());
        assertNotNull(textParam.getValue());
        assertTrue(textParam.getValue().contains(entityTypeId.toString()),
            "Parameter value should contain entityTypeId");
    }

    @Test
    void testGetErrorWithRealSerializationError() {
        // Create a custom Warning implementation that will cause serialization issues
        @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
        class UnserializableWarning implements Warning {
            // Create a circular reference that will cause serialization to fail
            private UnserializableWarning self = this;

            @Override
            public WarningType getType() {
                return WarningType.DEPRECATED_FIELD;
            }

            @Override
            public String getDescription(TranslationService translationService) {
                return "This warning will cause serialization to fail";
            }

            // Override toString to provide a meaningful representation for the test
            @Override
            public String toString() {
                return "UnserializableWarning[type=DEPRECATED_FIELD]";
            }
        }

        // Create a MigratableQueryInformation object with the unserializable warning
        UUID entityTypeId = UUID.randomUUID();
        String fqlQuery = "{\"_version\":\"1.0\",\"test\":{\"$eq\":\"foo\"}}";
        List<String> fields = List.of("field1", "field2");
        MigratableQueryInformation migratedQueryInformation = MigratableQueryInformation.builder()
            .entityTypeId(entityTypeId)
            .fqlQuery(fqlQuery)
            .fields(fields)
            .warning(new UnserializableWarning())
            .build();

        // Create the exception with the problematic MigratableQueryInformation
        MigrationQueryChangedException exception = new MigrationQueryChangedException(migratedQueryInformation);

        // Get the error - this should trigger the catch block in getError()
        Error error = exception.getError();

        // Verify error properties
        assertNotNull(error);
        assertEquals("Migration changed more than just the version", error.getMessage());
        assertEquals("migration.query.changed", error.getCode());

        // Verify parameters - should include serialization error and fallback text
        List<Parameter> parameters = error.getParameters();
        assertNotNull(parameters);
        assertEquals(2, parameters.size());

        // First parameter should be the serialization error
        Parameter errorParam = parameters.get(0);
        assertEquals("serializationError", errorParam.getKey());
        assertEquals("Failed to serialize migrated query information", errorParam.getValue());

        // Second parameter should be the toString() representation
        Parameter textParam = parameters.get(1);
        assertEquals("migratedQueryInformationText", textParam.getKey());
        assertNotNull(textParam.getValue());

        // Verify the parameter value contains the expected information
        String paramValue = textParam.getValue();
        assertTrue(paramValue.contains(entityTypeId.toString()),
            "Parameter value should contain entityTypeId");
        assertTrue(paramValue.contains("UnserializableWarning"),
            "Parameter value should contain the warning class name");
    }
}
