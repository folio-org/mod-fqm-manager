package org.folio.fqm.service;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.folio.fqm.domain.dto.EntityTypeSummaries;
import org.folio.querytool.domain.dto.ColumnValues;
import org.folio.querytool.domain.dto.EntityType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Lightweight in-process MCP service for this module.
 *
 * <p>This proof-of-concept service exposes runtime FQM metadata through MCP so a future LLM-backed
 * query suggestion flow can retrieve the context it needs before generating suggestions.
 */
@Service
public class FqmMcpService {

  public static final String TOOL_LIST_ENTITY_TYPES = "list-entity-types";
  public static final String TOOL_GET_ENTITY_TYPE = "get-entity-type";
  public static final String TOOL_GET_FIELD_VALUES = "get-field-values";

  private static final String DEFAULT_PROTOCOL_VERSION = "2025-03-26";
  private static final String DEFAULT_SERVER_NAME = "mod-fqm-manager";
  private static final String DEFAULT_SERVER_VERSION = "dev";
  private static final String DEFAULT_INSTRUCTIONS =
    "Use the available tools to inspect runtime FQM metadata before generating query suggestions.";

  private static final McpSchema.JsonSchema LIST_ENTITY_TYPES_INPUT_SCHEMA = new McpSchema.JsonSchema(
    "object",
    Map.ofEntries(
      Map.entry("ids", Map.of(
        "type", "array",
        "items", Map.of("type", "string", "format", "uuid"),
        "description", "Optional list of entity type ids to limit the results."
      )),
      Map.entry("includeAll", Map.of(
        "type", "boolean",
        "default", false,
        "description", "Whether to include private entity types."
      ))
    ),
    List.of(),
    false,
    null,
    null
  );

  private static final McpSchema.JsonSchema GET_ENTITY_TYPE_INPUT_SCHEMA = new McpSchema.JsonSchema(
    "object",
    Map.ofEntries(
      Map.entry("entityTypeId", Map.of(
        "type", "string",
        "format", "uuid",
        "description", "Entity type id to load."
      )),
      Map.entry("includeHidden", Map.of(
        "type", "boolean",
        "default", false,
        "description", "Whether to include hidden columns."
      ))
    ),
    List.of("entityTypeId"),
    false,
    null,
    null
  );

  private static final McpSchema.JsonSchema GET_FIELD_VALUES_INPUT_SCHEMA = new McpSchema.JsonSchema(
    "object",
    Map.ofEntries(
      Map.entry("entityTypeId", Map.of(
        "type", "string",
        "format", "uuid",
        "description", "Entity type id that owns the field."
      )),
      Map.entry("fieldName", Map.of(
        "type", "string",
        "description", "Field name to inspect."
      )),
      Map.entry("search", Map.of(
        "type", "string",
        "description", "Optional search text used to narrow returned values."
      ))
    ),
    List.of("entityTypeId", "fieldName"),
    false,
    null,
    null
  );

  private final EntityTypeService entityTypeService;
  private final MigrationService migrationService;
  private final ObjectMapper objectMapper;
  private final String protocolVersion;
  private final String serverName;
  private final String serverVersion;
  private final String instructions;

  /**
   * Creates the embedded MCP service using the existing FQM services.
   *
   * @param entityTypeService service that exposes runtime entity type metadata
   * @param migrationService service that exposes the current FQM version
   * @param objectMapper mapper used to turn DTOs into MCP structured content
   * @param configuredProtocolVersion configured MCP protocol version, if any
   * @param configuredServerName configured server name, if any
   * @param configuredServerVersion configured server version, if any
   * @param configuredInstructions configured server instructions, if any
   */
  public FqmMcpService(
    EntityTypeService entityTypeService,
    MigrationService migrationService,
    ObjectMapper objectMapper,
    @Value("${mod-fqm-manager.mcp.protocol-version:}") String configuredProtocolVersion,
    @Value("${mod-fqm-manager.mcp.server-name:}") String configuredServerName,
    @Value("${mod-fqm-manager.mcp.server-version:}") String configuredServerVersion,
    @Value("${mod-fqm-manager.mcp.instructions:}") String configuredInstructions
  ) {
    this.entityTypeService = entityTypeService;
    this.migrationService = migrationService;
    this.objectMapper = objectMapper;
    this.protocolVersion = configuredProtocolVersion == null || configuredProtocolVersion.isBlank()
      ? DEFAULT_PROTOCOL_VERSION
      : configuredProtocolVersion;
    this.serverName = configuredServerName == null || configuredServerName.isBlank()
      ? DEFAULT_SERVER_NAME
      : configuredServerName;
    this.serverVersion = resolveServerVersion(configuredServerVersion);
    this.instructions = configuredInstructions == null || configuredInstructions.isBlank()
      ? DEFAULT_INSTRUCTIONS
      : configuredInstructions;
  }

  /**
   * Returns the basic MCP server metadata and advertised capabilities.
   *
   * @return initialize response for the embedded MCP service
   */
  public McpSchema.InitializeResult initialize() {
    return new McpSchema.InitializeResult(
      protocolVersion,
      McpSchema.ServerCapabilities.builder()
        .tools(true)
        .build(),
      new McpSchema.Implementation(serverName, serverVersion),
      instructions
    );
  }

  /**
   * Lists the MCP tools currently exposed by this embedded service.
   *
   * @return registered MCP tools
   */
  public McpSchema.ListToolsResult listTools() {
    return new McpSchema.ListToolsResult(
      List.of(
        tool(TOOL_LIST_ENTITY_TYPES, "List accessible FQM entity types and the current FQM version.", LIST_ENTITY_TYPES_INPUT_SCHEMA),
        tool(TOOL_GET_ENTITY_TYPE, "Load a flattened FQM entity type definition.", GET_ENTITY_TYPE_INPUT_SCHEMA),
        tool(TOOL_GET_FIELD_VALUES, "Load available values for an FQM field.", GET_FIELD_VALUES_INPUT_SCHEMA)
      ),
      null
    );
  }

  /**
   * Calls a registered MCP tool by name.
   *
   * @param request tool call request
   * @return tool result
   */
  public McpSchema.CallToolResult callTool(McpSchema.CallToolRequest request) {
    Map<String, Object> arguments = request.arguments() == null ? Map.of() : Map.copyOf(request.arguments());
    return switch (request.name()) {
      case TOOL_LIST_ENTITY_TYPES -> listEntityTypes(arguments);
      case TOOL_GET_ENTITY_TYPE -> getEntityType(arguments);
      case TOOL_GET_FIELD_VALUES -> getFieldValues(arguments);
      default -> throw new IllegalArgumentException("Unknown MCP tool: " + request.name());
    };
  }

  private String resolveServerVersion(String configuredServerVersion) {
    if (configuredServerVersion != null && !configuredServerVersion.isBlank()) {
      return configuredServerVersion;
    }

    String implementationVersion = FqmMcpService.class.getPackage().getImplementationVersion();
    return implementationVersion == null || implementationVersion.isBlank() ? DEFAULT_SERVER_VERSION : implementationVersion;
  }

  private McpSchema.Tool tool(String name, String description, McpSchema.JsonSchema inputSchema) {
    return McpSchema.Tool.builder()
      .name(name)
      .description(description)
      .inputSchema(inputSchema)
      .build();
  }

  private McpSchema.CallToolResult listEntityTypes(Map<String, Object> arguments) {
    ListEntityTypesRequest request = objectMapper.convertValue(arguments, ListEntityTypesRequest.class);
    Set<UUID> ids = request.ids() == null ? Set.of() : Set.copyOf(request.ids());

    EntityTypeSummaries response = new EntityTypeSummaries()
      .entityTypes(entityTypeService.getEntityTypeSummary(
        ids,
        false,
        Boolean.TRUE.equals(request.includeAll())
      ))
      .version(migrationService.getLatestVersion());

    return McpSchema.CallToolResult.builder()
      .content(List.of(new McpSchema.TextContent("Returned FQM entity type summaries.")))
      .structuredContent(objectMapper.convertValue(response, Object.class))
      .isError(false)
      .build();
  }

  private McpSchema.CallToolResult getEntityType(Map<String, Object> arguments) {
    GetEntityTypeRequest request = objectMapper.convertValue(arguments, GetEntityTypeRequest.class);
    if (request.entityTypeId() == null) {
      throw new IllegalArgumentException("The get-entity-type MCP tool requires entityTypeId.");
    }

    EntityType response = entityTypeService.getEntityTypeDefinition(request.entityTypeId(), Boolean.TRUE.equals(request.includeHidden()));
    return McpSchema.CallToolResult.builder()
      .content(List.of(new McpSchema.TextContent("Returned an FQM entity type definition.")))
      .structuredContent(objectMapper.convertValue(response, Object.class))
      .isError(false)
      .build();
  }

  private McpSchema.CallToolResult getFieldValues(Map<String, Object> arguments) {
    GetFieldValuesRequest request = objectMapper.convertValue(arguments, GetFieldValuesRequest.class);
    if (request.entityTypeId() == null) {
      throw new IllegalArgumentException("The get-field-values MCP tool requires entityTypeId.");
    }
    if (request.fieldName() == null || request.fieldName().isBlank()) {
      throw new IllegalArgumentException("The get-field-values MCP tool requires fieldName.");
    }

    ColumnValues response = entityTypeService.getFieldValues(request.entityTypeId(), request.fieldName(), request.search());
    return McpSchema.CallToolResult.builder()
      .content(List.of(new McpSchema.TextContent("Returned FQM field values.")))
      .structuredContent(objectMapper.convertValue(response, Object.class))
      .isError(false)
      .build();
  }

  private record ListEntityTypesRequest(List<UUID> ids, Boolean includeAll) {
  }

  private record GetEntityTypeRequest(UUID entityTypeId, Boolean includeHidden) {
  }

  private record GetFieldValuesRequest(UUID entityTypeId, String fieldName, String search) {
  }
}
