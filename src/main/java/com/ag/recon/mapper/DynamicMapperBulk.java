package com.ag.recon.mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.ag.config.AgLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;

@Component
public class DynamicMapperBulk {

	private static final String TAG = "DynamicMapperBulk";

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final ResourceLoader resourceLoader;

	private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

	public DynamicMapperBulk(ResourceLoader resourceLoader, JdbcTemplate jdbcTemplate) {
		this.resourceLoader = resourceLoader;
		this.jdbcTemplate = jdbcTemplate;
	}

	public void mapAndPersist(Long masterId, String fileType, List<Map<String, Object>> parsedData, String corpId)
			throws Exception {

		long startTime = System.currentTimeMillis();

		AgLogger.logInfo(TAG + " | START mapAndPersist | masterId=" + masterId + ", fileType=" + fileType + ", corpId="
				+ corpId + ", totalRecords=" + (parsedData != null ? parsedData.size() : 0));

		if (parsedData == null || parsedData.isEmpty()) {
			AgLogger.logWarn(TAG + " | No data found for insert.");
			return;
		}

		Resource resource = resourceLoader.getResource("classpath:mapping_config.json");
		AgLogger.logInfo(TAG + " | Loading mapping config from classpath:mapping_config.json");

		JsonNode configArray = objectMapper.readTree(resource.getInputStream());

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
			AgLogger.logWarn(TAG + " | No mapping found for fileType=" + fileType + ", corpId=" + corpId);
			return;
		}

		String tableName = config.get("tableName").asText();
		String sequenceName = config.get("sequenceName").asText();
		JsonNode columns = config.get("columns");

		AgLogger.logInfo(TAG + " | Mapping resolved | table=" + tableName + ", sequence=" + sequenceName
				+ ", columnCount=" + columns.size());

		Set<String> dbColumns = getDbColumns(tableName);

		AgLogger.logInfo(TAG + " | DB Columns fetched | total=" + dbColumns.size());

		boolean hasId = dbColumns.contains("ID");
		boolean hasMasterId = dbColumns.contains("MASTER_ID");
		boolean hasCorpId = dbColumns.contains("CORP_ID");

		List<String> colNames = new ArrayList<>();

		if (hasId)
			colNames.add("ID");
		if (hasMasterId)
			colNames.add("MASTER_ID");
		if (hasCorpId)
			colNames.add("CORP_ID");

		for (JsonNode col : columns) {
			String target = col.get("target").asText();
			if (dbColumns.contains(target.toUpperCase())) {
				colNames.add(target);
			} else {
				AgLogger.logWarn(TAG + " | Skipping unmapped column: " + target);
			}
		}

		AgLogger.logInfo(TAG + " | Final column list: " + colNames);

		String sql = "INSERT INTO " + tableName + " (" + String.join(",", colNames) + ") VALUES ("
				+ colNames.stream().map(c -> "?").collect(Collectors.joining(",")) + ")";

		AgLogger.logInfo(TAG + " | Generated SQL: " + sql);

		int batchSize = 500;

		// Reserve ID block
		List<Long> allIds = hasId ? reserveSequenceBlock(sequenceName, parsedData.size()) : Collections.emptyList();

		AtomicInteger globalIndex = new AtomicInteger(0);

		List<Future<?>> futures = new ArrayList<>();

		for (int i = 0; i < parsedData.size(); i += batchSize) {
			int fromIndex = i;
			int toIndex = Math.min(i + batchSize, parsedData.size());

			List<Map<String, Object>> chunk = parsedData.subList(fromIndex, toIndex);

			int batchNumber = i / batchSize;

			AgLogger.logInfo(TAG + " | Submitting batch #" + batchNumber + " | range=" + fromIndex + "-" + toIndex);

			futures.add(executor.submit(() -> {
				long batchStart = System.currentTimeMillis();

				List<Object[]> batchValues = new ArrayList<>();

				try {
					for (Map<String, Object> row : chunk) {
						int currentIndex = globalIndex.getAndIncrement();

						List<Object> values = new ArrayList<>();

						if (hasId)
							values.add(allIds.get(currentIndex));
						if (hasMasterId)
							values.add(masterId);
						if (hasCorpId)
							values.add(corpId);

						for (JsonNode col : columns) {
							String source = col.get("source").asText();
							String target = col.get("target").asText();

							if (!dbColumns.contains(target.toUpperCase())) {
								continue;
							}

							values.add(row.get(source));
						}

						batchValues.add(values.toArray());
					}

					jdbcTemplate.batchUpdate(sql, batchValues);

					long batchEnd = System.currentTimeMillis();

					AgLogger.logInfo(TAG + " | Batch SUCCESS #" + batchNumber + " | size=" + batchValues.size()
							+ " | time=" + (batchEnd - batchStart) + "ms | thread=" + Thread.currentThread().getName());

				} catch (Exception e) {
					AgLogger.logError(getClass(),
							TAG + " | Batch FAILED #" + batchNumber + " | error=" + e.getMessage(), e);
					throw e;
				}
			}));
		}

		for (Future<?> future : futures) {
			future.get();
		}

		long endTime = System.currentTimeMillis();

		AgLogger.logInfo(TAG + " | END mapAndPersist | totalTime=" + (endTime - startTime) + "ms");
	}

	private List<Long> reserveSequenceBlock(String sequenceName, int totalRows) {

		AgLogger.logInfo(TAG + " | Reserving sequence block | sequence=" + sequenceName + ", size=" + totalRows);

		Long startId = jdbcTemplate.queryForObject("SELECT NEXT VALUE FOR " + sequenceName, Long.class);

		List<Long> ids = new ArrayList<>(totalRows);

		for (int i = 0; i < totalRows; i++) {
			ids.add(startId + i);
		}

		Long nextStart = startId + totalRows;

		jdbcTemplate.execute("ALTER SEQUENCE " + sequenceName + " RESTART WITH " + nextStart);

		AgLogger.logInfo(TAG + " | Sequence reserved | start=" + startId + ", nextStart=" + nextStart);

		return ids;
	}

	private Set<String> getDbColumns(String tableName) {
		try {
			AgLogger.logInfo(TAG + " | Fetching DB columns for table=" + tableName);

			List<String> columns = jdbcTemplate.queryForList(
					"SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?", String.class, tableName);

			return columns.stream().map(String::toUpperCase).collect(Collectors.toSet());

		} catch (Exception e) {
			AgLogger.logError(getClass(), TAG + " | Failed to fetch DB columns | table=" + tableName, e);
			return Collections.emptySet();
		}
	}

	@PreDestroy
	public void destroy() {
		AgLogger.logInfo(TAG + " | Shutting down executor");
		executor.shutdown();
	}
}

//package com.ag.recon.mapper;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.Future;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.stream.Collectors;
//
//import org.springframework.core.io.Resource;
//import org.springframework.core.io.ResourceLoader;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.stereotype.Component;
//
//import com.ag.config.AgLogger;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//
//import jakarta.annotation.PreDestroy;
//
//@Component
//public class DynamicMapperBulk {
//
//	private final JdbcTemplate jdbcTemplate;
//	private final ObjectMapper objectMapper = new ObjectMapper();
//	private final ResourceLoader resourceLoader;
//
//	private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
//
//	public DynamicMapperBulk(ResourceLoader resourceLoader, JdbcTemplate jdbcTemplate) {
//		this.resourceLoader = resourceLoader;
//		this.jdbcTemplate = jdbcTemplate;
//	}
//
//	public void mapAndPersist(Long masterId, String fileType, List<Map<String, Object>> parsedData, String corpId)
//			throws Exception {
//
//		if (parsedData == null || parsedData.isEmpty()) {
//			AgLogger.logInfo("No data found for insert.");
//			return;
//		}
//
//		Resource resource = resourceLoader.getResource("classpath:mapping_config.json");
//		JsonNode configArray = objectMapper.readTree(resource.getInputStream());
//
//		JsonNode config = null;
//		for (JsonNode node : configArray) {
//			boolean fileMatch = node.get("fileType").asText().equalsIgnoreCase(fileType);
//			boolean corpMatch = node.has("corpId") && node.get("corpId").asText().equals(corpId);
//
//			if (fileMatch && corpMatch) {
//				config = node;
//				break;
//			}
//		}
//
//		if (config == null) {
//			AgLogger.logWarn("No mapping found for fileType=" + fileType + ", corpId=" + corpId);
//			return;
//		}
//
//		String tableName = config.get("tableName").asText();
//		String sequenceName = config.get("sequenceName").asText();
//		JsonNode columns = config.get("columns");
//
//		Set<String> dbColumns = getDbColumns(tableName);
//
//		boolean hasId = dbColumns.contains("ID");
//		boolean hasMasterId = dbColumns.contains("MASTER_ID");
//		boolean hasCorpId = dbColumns.contains("CORP_ID");
//
//		List<String> colNames = new ArrayList<>();
//
//		if (hasId)
//			colNames.add("ID");
//		if (hasMasterId)
//			colNames.add("MASTER_ID");
//		if (hasCorpId)
//			colNames.add("CORP_ID");
//
//		for (JsonNode col : columns) {
//			String target = col.get("target").asText();
//			if (dbColumns.contains(target.toUpperCase())) {
//				colNames.add(target);
//			}
//		}
//
//		String sql = "INSERT INTO " + tableName + " (" + String.join(",", colNames) + ") VALUES ("
//				+ colNames.stream().map(c -> "?").collect(Collectors.joining(",")) + ")";
//
//		int batchSize = 500;
//
//		// ✅ Reserve one BIG ID block for complete file
//		List<Long> allIds = hasId ? reserveSequenceBlock(sequenceName, parsedData.size()) : Collections.emptyList();
//
//		AtomicInteger globalIndex = new AtomicInteger(0);
//
//		List<Future<?>> futures = new ArrayList<>();
//
//		for (int i = 0; i < parsedData.size(); i += batchSize) {
//			int fromIndex = i;
//			int toIndex = Math.min(i + batchSize, parsedData.size());
//
//			List<Map<String, Object>> chunk = parsedData.subList(fromIndex, toIndex);
//
//			futures.add(executor.submit(() -> {
//				List<Object[]> batchValues = new ArrayList<>();
//
//				for (Map<String, Object> row : chunk) {
//					int currentIndex = globalIndex.getAndIncrement();
//
//					List<Object> values = new ArrayList<>();
//
//					if (hasId)
//						values.add(allIds.get(currentIndex));
//					if (hasMasterId)
//						values.add(masterId);
//					if (hasCorpId)
//						values.add(corpId);
//
//					for (JsonNode col : columns) {
//						String source = col.get("source").asText();
//						String target = col.get("target").asText();
//
//						if (!dbColumns.contains(target.toUpperCase())) {
//							continue;
//						}
//
//						values.add(row.get(source));
//					}
//
//					batchValues.add(values.toArray());
//				}
//
//				jdbcTemplate.batchUpdate(sql, batchValues);
//				AgLogger.logInfo("Inserted batch of " + batchValues.size() + " rows into " + tableName);
//			}));
//		}
//
//		for (Future<?> future : futures) {
//			future.get();
//		}
//	}
//
//	private List<Long> reserveSequenceBlock(String sequenceName, int totalRows) {
//		Long startId = jdbcTemplate.queryForObject("SELECT NEXT VALUE FOR " + sequenceName, Long.class);
//
//		List<Long> ids = new ArrayList<>(totalRows);
//
//		for (int i = 0; i < totalRows; i++) {
//			ids.add(startId + i);
//		}
//
//		Long nextStart = startId + totalRows;
//		jdbcTemplate.execute("ALTER SEQUENCE " + sequenceName + " RESTART WITH " + nextStart);
//
//		return ids;
//	}
//
//	private Set<String> getDbColumns(String tableName) {
//		try {
//			List<String> columns = jdbcTemplate.queryForList(
//					"SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?", String.class, tableName);
//			return columns.stream().map(String::toUpperCase).collect(Collectors.toSet());
//
//		} catch (Exception e) {
//			AgLogger.logWarn("Failed to fetch DB columns: " + e.getMessage());
//			return Collections.emptySet();
//		}
//	}
//
//	@PreDestroy
//	public void destroy() {
//		executor.shutdown();
//	}
//}
