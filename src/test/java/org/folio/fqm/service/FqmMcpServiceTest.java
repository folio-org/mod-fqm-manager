package org.folio.fqm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.folio.fqm.domain.dto.EntityTypeSummaries;
import org.folio.fqm.domain.dto.EntityTypeSummary;
import org.folio.querytool.domain.dto.ColumnValues;
import org.folio.querytool.domain.dto.EntityType;
import org.folio.querytool.domain.dto.ValueWithLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FqmMcpServiceTest {

  private ObjectMapper objectMapper;
  private FqmMcpService fqmMcpService;
  private TestEntityTypeService entityTypeService;
  private TestMigrationService migrationService;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    entityTypeService = new TestEntityTypeService();
    migrationService = new TestMigrationService();
    fqmMcpService = new FqmMcpService(
      entityTypeService,
      migrationService,
      objectMapper,
      "2025-03-26",
      "mod-fqm-manager",
      "test-version",
      "Test MCP instructions"
    );
  }

  @Test
  void shouldExposeInitializeMetadata() {
    McpSchema.InitializeResult initializeResult = fqmMcpService.initialize();

    assertEquals("2025-03-26", initializeResult.protocolVersion());
    assertNotNull(initializeResult.serverInfo());
    assertEquals("Test MCP instructions", initializeResult.instructions());
    assertNotNull(initializeResult.capabilities().tools());
    assertNull(initializeResult.capabilities().resources());
    assertNull(initializeResult.capabilities().prompts());
  }

  @Test
  void shouldListRegisteredTools() {
    McpSchema.ListToolsResult listToolsResult = fqmMcpService.listTools();

    assertEquals(3, listToolsResult.tools().size());
    assertEquals(FqmMcpService.TOOL_LIST_ENTITY_TYPES, listToolsResult.tools().get(0).name());
    assertEquals(FqmMcpService.TOOL_GET_ENTITY_TYPE, listToolsResult.tools().get(1).name());
    assertEquals(FqmMcpService.TOOL_GET_FIELD_VALUES, listToolsResult.tools().get(2).name());
  }

  @Test
  void shouldReturnEntityTypeSummariesFromListEntityTypesTool() {
    UUID entityTypeId = UUID.randomUUID();
    entityTypeService.entityTypeSummaries = List.of(new EntityTypeSummary().id(entityTypeId).label("Users").isCustom(false));
    migrationService.latestVersion = "42";

    McpSchema.CallToolResult toolResult = fqmMcpService.callTool(McpSchema.CallToolRequest.builder()
      .name(FqmMcpService.TOOL_LIST_ENTITY_TYPES)
      .arguments(Map.of())
      .build());

    EntityTypeSummaries response = objectMapper.convertValue(toolResult.structuredContent(), EntityTypeSummaries.class);
    assertFalse(Boolean.TRUE.equals(toolResult.isError()));
    assertEquals(1, response.getEntityTypes().size());
    assertEquals(entityTypeId, response.getEntityTypes().get(0).getId());
    assertEquals("42", response.getVersion());
  }

  @Test
  void shouldReturnEntityTypeDefinitionFromGetEntityTypeTool() {
    UUID entityTypeId = UUID.randomUUID();
    entityTypeService.entityType = new EntityType().id(entityTypeId.toString()).name("users")._private(false);

    McpSchema.CallToolResult toolResult = fqmMcpService.callTool(McpSchema.CallToolRequest.builder()
      .name(FqmMcpService.TOOL_GET_ENTITY_TYPE)
      .arguments(Map.of("entityTypeId", entityTypeId.toString()))
      .build());

    EntityType response = objectMapper.convertValue(toolResult.structuredContent(), EntityType.class);
    assertFalse(Boolean.TRUE.equals(toolResult.isError()));
    assertEquals(entityTypeId.toString(), response.getId());
    assertEquals("users", response.getName());
  }

  @Test
  void shouldReturnFieldValuesFromGetFieldValuesTool() {
    UUID entityTypeId = UUID.randomUUID();
    entityTypeService.columnValues = new ColumnValues().content(List.of(new ValueWithLabel().value("open").label("Open")));

    McpSchema.CallToolResult toolResult = fqmMcpService.callTool(McpSchema.CallToolRequest.builder()
      .name(FqmMcpService.TOOL_GET_FIELD_VALUES)
      .arguments(Map.of(
        "entityTypeId", entityTypeId.toString(),
        "fieldName", "status",
        "search", "open"
      ))
      .build());

    ColumnValues response = objectMapper.convertValue(toolResult.structuredContent(), ColumnValues.class);
    assertFalse(Boolean.TRUE.equals(toolResult.isError()));
    assertEquals(1, response.getContent().size());
    assertEquals("open", response.getContent().get(0).getValue());
  }

  private static final class TestEntityTypeService extends EntityTypeService {
    private List<EntityTypeSummary> entityTypeSummaries = List.of();
    private EntityType entityType = new EntityType();
    private ColumnValues columnValues = new ColumnValues().content(List.of());

    private TestEntityTypeService() {
      super(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Override
    public List<EntityTypeSummary> getEntityTypeSummary(Set<UUID> entityTypeIds, boolean includeInaccessible, boolean includeAll) {
      return entityTypeSummaries;
    }

    @Override
    public EntityType getEntityTypeDefinition(UUID entityTypeId, boolean includeHidden) {
      return entityType;
    }

    @Override
    public ColumnValues getFieldValues(UUID entityTypeId, String fieldName, String searchText) {
      return columnValues;
    }
  }

  private static final class TestMigrationService extends MigrationService {
    private String latestVersion = "0";

    private TestMigrationService() {
      super(null, null, null, null, null, null, null);
    }

    @Override
    public String getLatestVersion() {
      return latestVersion;
    }
  }
}
