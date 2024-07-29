package org.folio.fqm.service;

import org.apache.commons.lang3.NotImplementedException;
import org.folio.fqm.client.ModPermissionsClient;
import org.folio.fqm.client.ModPermissionsClient.UserPermissions;
import org.folio.fqm.exception.MissingPermissionsException;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionsServiceTest {

  private final FolioExecutionContext context = mock(FolioExecutionContext.class);
  private final ModPermissionsClient modPermissionsClient = mock(ModPermissionsClient.class);
  private final EntityTypeFlatteningService entityTypeFlatteningService = mock(EntityTypeFlatteningService.class);
  private final PermissionsService permissionsService = new PermissionsService(context, modPermissionsClient, entityTypeFlatteningService);

  @BeforeEach
  void setUp() {
    permissionsService.isEureka = false; // Force the service to use the mod-permissions client
  }

  void setUpMocks(String... permissions) {
    var userId = UUID.randomUUID();
    when(context.getUserId()).thenReturn(userId);
    when(modPermissionsClient.getPermissionsForUser(userId.toString())).thenReturn(new UserPermissions(List.of(permissions), permissions.length));
  }

  private EntityType getTestEntityType() {
    EntityType entityType = new EntityType(UUID.randomUUID().toString(), "entity type name", true, false)
      .sources(List.of(new EntityTypeSource("db", "source_alias")));
    when(entityTypeFlatteningService.getFlattenedEntityType(any(UUID.class))).thenReturn(entityType);
    return entityType;
  }

  @Test
  void thePermissionsServiceShouldUseTheModPermissionsClientByDefault() {
    setUpMocks("permission1", "permission2");
    assertEquals(2, permissionsService.getUserPermissions().size());
  }

  @Test
  void userWithNoPermissionsCanOnlyAccessEntityTypesWithNoPermissions() {
    setUpMocks(); // User has no permissions
    EntityType entityType = getTestEntityType();
    assertDoesNotThrow(() -> permissionsService.verifyUserHasNecessaryPermissionsForEntityType(entityType), "No permissions are required");

    entityType.requiredPermissions(List.of("permission1"));
    assertThrows(MissingPermissionsException.class, () -> permissionsService.verifyUserHasNecessaryPermissionsForEntityType(entityType), "The does not have the required permission");
  }

  @Test
  void userHasNecessaryPermissions() {
    // Given a user with 2 permissions
    setUpMocks("permission1", "permission2");
    // When the entity type requires various possible permissions (which the user has)
    Map.<List<String>, String>of(
        List.of(), "No permissions are required",
        List.of("permission1"), "A single permission is required and the user has it",
        List.of("permission2"), "The user's 2nd permission is the required one",
        List.of("permission1", "permission2"), "Both of the user's permissions are required"
      )
      .forEach((permissions, message) -> {
        EntityType entityType = getTestEntityType().requiredPermissions(permissions);
        // Then the user should be able to perform the operation
        assertDoesNotThrow(() -> permissionsService.verifyUserHasNecessaryPermissionsForEntityType(entityType), message);
      });
  }

  @Test
  void userDoesNotHaveNecessaryPermissions() {
    setUpMocks("permission1", "permission2");
    EntityType entityType = getTestEntityType();

    entityType.requiredPermissions(List.of("permission3"));
    assertThrows(MissingPermissionsException.class, () -> permissionsService.verifyUserHasNecessaryPermissionsForEntityType(entityType), "The user does not have the required permission");

    entityType.requiredPermissions(List.of("permission1", "permission3"));
    MissingPermissionsException exception = assertThrows(MissingPermissionsException.class, () -> permissionsService.verifyUserHasNecessaryPermissionsForEntityType(entityType), "The only has some of the required permissions");

    assertEquals(Set.of("permission3"), exception.getMissingPermissions());
    assertTrue(exception.getMessage().contains("permission3"));
    assertFalse(exception.getMessage().contains("permission1"));
  }

  @Test
  void eurekaSupportIsNotImplementedYet() {
    permissionsService.isEureka = true; // Force the service to use mod-roles-keycloak
    setUpMocks();
    assertThrows(NotImplementedException.class, permissionsService::getUserPermissions);
  }
}
