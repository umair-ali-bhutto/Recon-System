package com.ag.recon.mapper;

import java.sql.Types;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.ag.config.AgLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DynamicMapper: Maps parsed file data to DB tables based on JSON metadata.
 * Logs missing source columns and ensures DB targets exist.
 */
@Component
public class DynamicMapper {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResourceLoader resourceLoader;

    public DynamicMapper(ResourceLoader resourceLoader, JdbcTemplate jdbcTemplate) {
        this.resourceLoader = resourceLoader;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void mapAndPersist(String fileType, List<Map<String, Object>> parsedData, String corpId) throws Exception {

        Resource resource = resourceLoader.getResource("classpath:mapping_config.json");
        JsonNode configArray = objectMapper.readTree(resource.getInputStream());

        // Find mapping for fileType + corpId
        JsonNode config = null;
        for (JsonNode node : configArray) {
            boolean fileMatch = node.get("fileType").asText().equalsIgnoreCase(fileType);
            boolean corpMatch = node.has("corpId") && node.get("corpId").asText().equals(corpId);

            if (fileMatch && corpMatch) {
                config = node;
                break;
            }
        }

        if (config == null) {
            AgLogger.logInfo("No mapping found for fileType=" + fileType + " and corpId=" + corpId);
            return;
        }

        String table = config.get("table").asText();
        JsonNode columns = config.get("columns");

        // Optional: verify target columns exist in DB
        Set<String> dbColumns = getDbColumns(table);

        for (Map<String, Object> row : parsedData) {
            List<String> colNames = new ArrayList<>();
            List<Object> values = new ArrayList<>();
            List<Integer> typesList = new ArrayList<>();

            for (JsonNode col : columns) {
                String source = col.get("source").asText();
                String target = col.get("target").asText();
                String typeStr = col.get("type").asText().toUpperCase();

                // Check if target exists in DB
                if (!dbColumns.contains(target.toUpperCase())) {
                    AgLogger.logWarn("Target column '" + target + "' does not exist in table " + table);
                    continue; // skip this column
                }

                colNames.add(target);

                // Check if source exists in row
                Object value = row.get(source);
                if (value == null) {
                    AgLogger.logWarn("Source column '" + source + "' missing in file row, setting NULL");
                }
                values.add(value);

                typesList.add(switch (typeStr) {
                    case "STRING" -> Types.VARCHAR;
                    case "DECIMAL" -> Types.DECIMAL;
                    case "DATE" -> Types.DATE;
                    case "TIMESTAMP" -> Types.TIMESTAMP;
                    case "INTEGER" -> Types.INTEGER;
                    default -> Types.VARCHAR;
                });
            }

            // Add corpId
            if (config.has("corpId") && dbColumns.contains("CORP_ID")) {
                colNames.add("corp_id");
                values.add(corpId);
                typesList.add(Types.VARCHAR);
            }

            String sql = "INSERT INTO " + table + " (" + String.join(",", colNames) + ") VALUES ("
                    + colNames.stream().map(c -> "?").collect(Collectors.joining(",")) + ")";

            try {
                jdbcTemplate.update(sql, values.toArray(), typesList.stream().mapToInt(i -> i).toArray());
                AgLogger.logInfo("Inserted row into " + table + ": " + values);
            } catch (Exception e) {
                AgLogger.logError(getClass(), "Failed to insert row: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Fetch DB table columns (case-insensitive) to verify target existence.
     */
    private Set<String> getDbColumns(String tableName) {
        try {
            List<String> columns = jdbcTemplate.queryForList(
                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?",
                    String.class, tableName);
            return columns.stream().map(String::toUpperCase).collect(Collectors.toSet());
        } catch (Exception e) {
            AgLogger.logWarn("Failed to fetch DB columns for table " + tableName + ": " + e.getMessage());
            return Collections.emptySet();
        }
    }
}