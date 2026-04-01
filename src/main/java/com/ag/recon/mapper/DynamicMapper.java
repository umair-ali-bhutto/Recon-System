package com.ag.recon.mapper;

import java.sql.Types;
import java.util.List;
import java.util.Map;
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
 * Supports multiple file types and corporate IDs from a single JSON array
 * configuration.
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

		// Load JSON array of mappings
		JsonNode configArray = objectMapper.readTree(resource.getInputStream());

		// Find mapping for the current fileType AND corpId
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

		for (Map<String, Object> row : parsedData) {
			List<String> colNames = columns.findValues("target").stream().map(JsonNode::asText)
					.collect(Collectors.toList());

			List<Object> values = columns.findValues("source").stream().map(srcNode -> row.get(srcNode.asText()))
					.collect(Collectors.toList());

			// Add corpId as a column if it exists in the config
			if (config.has("corpId")) {
				colNames.add("corp_id"); // assuming DB column name is 'corp_id'
				values.add(corpId);
			}

			String sql = "INSERT INTO " + table + " (" + String.join(",", colNames) + ") VALUES ("
					+ colNames.stream().map(c -> "?").collect(Collectors.joining(",")) + ")";

			int[] types = columns.findValues("type").stream().mapToInt(node -> switch (node.asText().toUpperCase()) {
			case "STRING" -> Types.VARCHAR;
			case "DECIMAL" -> Types.DECIMAL;
			case "DATE" -> Types.DATE;
			case "TIMESTAMP" -> Types.TIMESTAMP;
			case "INTEGER" -> Types.INTEGER;
			default -> Types.VARCHAR;
			}).toArray();

			// Add corpId type
			if (config.has("corpId")) {
				types = appendType(types, Types.VARCHAR);
			}

			try {
				AgLogger.logInfo("sql: " + sql);
				jdbcTemplate.update(sql, values.toArray(), types);
			} catch (Exception e) {
				AgLogger.logError(getClass(), "Failed to insert row into table " + table + ": " + e.getMessage(), e);
			}
		}
	}

	/** Helper method to append a type to the existing types array */
	private int[] appendType(int[] original, int newType) {
		int[] extended = new int[original.length + 1];
		System.arraycopy(original, 0, extended, 0, original.length);
		extended[original.length] = newType;
		return extended;
	}
}