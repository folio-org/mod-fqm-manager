package org.folio.fqm.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.*;
import org.folio.fqm.client.ModPermissionsClient;
import org.folio.fqm.client.ModRolesKeycloakClient;
import org.folio.fqm.exception.MissingPermissionsException;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.EntityTypeSource;
import org.folio.spring.FolioExecutionContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PermissionsRegularServiceTest {

  private final FolioExecutionContext context = mock(FolioExecutionContext.class);
  private final ModPermissionsClient modPermissionsClient = mock(ModPermissionsClient.class);
  private final ModRolesKeycloakClient modRolesKeyclockClient = mock(ModRolesKeycloakClient.class);
  private final EntityTypeFlatteningService entityTypeFlatteningService = mock(EntityTypeFlatteningService.class);
  private final PermissionsRegularService permissionsService = new PermissionsRegularService(
    context,
    modPermissionsClient,
    modRolesKeyclockClient,
    entityTypeFlatteningService
  );
  private static final String TENANT_ID = "tenant_01";

  void setUpMocks(String... permissions) {
    var userId = UUID.randomUUID();
    when(context.getUserId()).thenReturn(userId);
    when(context.getTenantId()).thenReturn(TENANT_ID);

    if (permissionsService.isEureka) {
      when(modRolesKeyclockClient.getPermissionsUser(TENANT_ID, userId))
        .thenReturn(new ModRolesKeycloakClient.UserPermissions(List.of(permissions), userId));
    } else {
      when(modPermissionsClient.getPermissionsForUser(TENANT_ID, userId.toString()))
        .thenReturn(new ModPermissionsClient.UserPermissions(List.of(permissions), permissions.length));
    }
  }


  private EntityType getTestEntityType() {
    EntityType entityType = new EntityType(UUID.randomUUID().toString(), "entity type name", true, false)
      .sources(List.of(new EntityTypeSource("db", "source_alias")));
    when(entityTypeFlatteningService.getFlattenedEntityType(any(UUID.class), eq(null))).thenReturn(entityType);
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
    when(context.getTenantId()).thenReturn(TENANT_ID);
    assertDoesNotThrow(
      () -> permissionsService.verifyUserHasNecessaryPermissions(TENANT_ID, entityType, false),
      "No permissions are required"
    );

    entityType.requiredPermissions(List.of("permission1"));
    assertThrows(
      MissingPermissionsException.class,
      () -> permissionsService.verifyUserHasNecessaryPermissions(TENANT_ID, entityType, false),
      "The does not have the required permission"
    );
  }

  @Test
  void userHasNecessaryPermissions() {
    // Given a user with 2 permissions
    setUpMocks("permission1", "permission2");
    // When the entity type requires various possible permissions (which the user has)
    Map
      .<List<String>, String>of(
        List.of(),
        "No permissions are required",
        List.of("permission1"),
        "A single permission is required and the user has it",
        List.of("permission2"),
        "The user's 2nd permission is the required one",
        List.of("permission1", "permission2"),
        "Both of the user's permissions are required"
      )
      .forEach((permissions, message) -> {
        EntityType entityType = getTestEntityType().requiredPermissions(permissions);
        // Then the user should be able to perform the operation
        assertDoesNotThrow(() -> permissionsService.verifyUserHasNecessaryPermissions(entityType, false), message);
      });
  }

  @Test
  void userDoesNotHaveNecessaryPermissions() {
    setUpMocks("permission1", "permission2");
    EntityType entityType = getTestEntityType();

    entityType.requiredPermissions(List.of("permission3"));
    assertThrows(
      MissingPermissionsException.class,
      () -> permissionsService.verifyUserHasNecessaryPermissions(TENANT_ID, entityType, false),
      "The user does not have the required permission"
    );

    entityType.requiredPermissions(List.of("permission1", "permission3"));
    MissingPermissionsException exception = assertThrows(
      MissingPermissionsException.class,
      () -> permissionsService.verifyUserHasNecessaryPermissions(TENANT_ID, entityType, false)
    );

    assertEquals(Set.of("permission3"), exception.getMissingPermissions());
    assertTrue(exception.getMessage().contains("permission3"));
    assertFalse(exception.getMessage().contains("permission1"));
  }

  @Test
  void shouldCheckFqmPermissionsIfRequested() {
    setUpMocks("permission1", "permission2", "fqm.entityTypes.item.get");
    EntityType entityType = getTestEntityType();
    entityType.requiredPermissions(List.of("permission1"));
    Set<String> expectedMissingPermissions = Set.of("fqm.query.async.results.get", "fqm.query.async.post");

    MissingPermissionsException exception = assertThrows(
      MissingPermissionsException.class,
      () -> permissionsService.verifyUserHasNecessaryPermissions(TENANT_ID, entityType, true)
    );

    assertEquals(expectedMissingPermissions, exception.getMissingPermissions());
  }

  @Test
  void WhenEurekaIsTrue() {
    permissionsService.isEureka = true;
    setUpMocks("permission1", "permission2");
    Set<String> userPermissions = permissionsService.getUserPermissions();

    ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
    verify(modRolesKeyclockClient).getPermissionsUser(eq(TENANT_ID), userIdCaptor.capture());
    assertEquals(2, userPermissions.size());

    // Ensure no interactions with modPermissionsClient
    verifyNoInteractions(modPermissionsClient);
  }

  @Test
  void WhenEurekaIsFalse() {
    permissionsService.isEureka = false;
    setUpMocks("permission1", "permission2");
    Set<String> userPermissions = permissionsService.getUserPermissions();

    ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(modPermissionsClient).getPermissionsForUser(eq(TENANT_ID), userIdCaptor.capture());
    assertEquals(2, userPermissions.size());

    // Ensure no interactions with modRolesKeyclockClient
    verifyNoInteractions(modRolesKeyclockClient);
  }

}
