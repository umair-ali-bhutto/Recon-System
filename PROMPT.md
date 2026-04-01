Absolutely! Let's design your **Dynamic Recon System – File Ingestion Module** in a **metadata-driven way** using **Spring Boot 3.1+ and Java 17**. I’ll provide:

1. **High-level architecture / class diagram explanation**
2. **mapping_config.json structure**
3. **Full Spring Boot code** with proper comments
4. **Guide to add new file formats**

---

## **1️⃣ High-Level Architecture**

```
+------------------+
|   ReconRunner    |  <-- Spring Boot Runner, triggers ingestion
+------------------+
          |
          v
+------------------+
|  FileIngestionService  |
+------------------+
          |
          v
+------------------+       +------------------+       +------------------+
|  FileParserStrategy |<---| ParserFactory     |--->   | CSVParserStrategy |
|  (Interface)       |     +------------------+       | XLSXParserStrategy|
+------------------+                                 | TXTParserStrategy |
          |                                          +------------------+
          v
+------------------+
|  DynamicMapper   |  <-- maps parsed data to DB via mapping_config.json
+------------------+
          |
          v
+------------------+
| Database Layer   |
| (Spring Data JPA |
| or JdbcTemplate) |
+------------------+
```

**Flow:**

1. Runner calls `FileIngestionService` with a file path.
2. Service detects the file type → uses `ParserFactory` to get the right `FileParserStrategy`.
3. Strategy parses the file into a **List<Map<String, Object>>**.
4. `DynamicMapper` reads **mapping_config.json** to map columns to DB tables/columns.
5. Data is dynamically inserted using Spring Data JPA or `JdbcTemplate`.
6. Adding a new file format only requires implementing a new strategy and registering it in the factory.

---

## **2️⃣ mapping_config.json**

```json
{
  "fileType": "csv",
  "table": "transactions",
  "columns": [
    {
      "source": "TransactionID",
      "target": "txn_id",
      "type": "STRING"
    },
    {
      "source": "Amount",
      "target": "amount",
      "type": "DECIMAL"
    },
    {
      "source": "Date",
      "target": "txn_date",
      "type": "DATE"
    }
  ]
}
```

* **source** = column name or index in the file.
* **target** = DB column name.
* **type** = data type (`STRING`, `DECIMAL`, `DATE`, `INTEGER`).
* Supports **new files or tables** by adding new JSON configs.

---

## **3️⃣ Updated pom.xml Dependencies**

Add missing dependencies for Excel parsing, CSV parsing, JSON, and dynamic DB access:

```xml
<!-- Apache POI for Excel -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.3</version>
</dependency>

<!-- OpenCSV for CSV parsing -->
<dependency>
    <groupId>com.opencsv</groupId>
    <artifactId>opencsv</artifactId>
    <version>5.7.1</version>
</dependency>

<!-- Jackson for JSON/YAML -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
<dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-yaml</artifactId>
</dependency>

<!-- Apache Commons IO -->
<dependency>
    <groupId>commons-io</groupId>
    <artifactId>commons-io</artifactId>
    <version>2.11.0</version>
</dependency>
```

---

## **4️⃣ Spring Boot Implementation**

### **4.1 FileParserStrategy.java**

```java
package com.ag.recon.parsers;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface FileParserStrategy {
    List<Map<String, Object>> parse(File file) throws Exception;
}
```

---

### **4.2 ParserFactory.java**

```java
package com.ag.recon.parsers;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class ParserFactory {

    private final Map<String, FileParserStrategy> strategies = new HashMap<>();

    public ParserFactory(CSVParserStrategy csvParser,
                         XLSXParserStrategy xlsxParser,
                         TXTParserStrategy txtParser) {
        strategies.put("csv", csvParser);
        strategies.put("xlsx", xlsxParser);
        strategies.put("txt", txtParser);
        // Add new parser strategies here
    }

    public FileParserStrategy getParser(String extension) {
        return strategies.get(extension.toLowerCase());
    }
}
```

---

### **4.3 CSVParserStrategy.java**

```java
package com.ag.recon.parsers;

import com.opencsv.CSVReader;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileReader;
import java.util.*;

@Component
public class CSVParserStrategy implements FileParserStrategy {

    @Override
    public List<Map<String, Object>> parse(File file) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new FileReader(file))) {
            String[] headers = reader.readNext(); // first row as header
            String[] line;
            while ((line = reader.readNext()) != null) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    row.put(headers[i], line[i]);
                }
                rows.add(row);
            }
        }
        return rows;
    }
}
```

---

### **4.4 XLSXParserStrategy.java**

```java
package com.ag.recon.parsers;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

@Component
public class XLSXParserStrategy implements FileParserStrategy {

    @Override
    public List<Map<String, Object>> parse(File file) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();
            Row headerRow = rowIterator.next();
            List<String> headers = new ArrayList<>();
            headerRow.forEach(cell -> headers.add(cell.getStringCellValue()));

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                Map<String, Object> map = new HashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = row.getCell(i);
                    map.put(headers.get(i), cell == null ? null : cell.toString());
                }
                rows.add(map);
            }
        }
        return rows;
    }
}
```

---

### **4.5 TXTParserStrategy.java** (simple line-based parser)

```java
package com.ag.recon.parsers;

import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

@Component
public class TXTParserStrategy implements FileParserStrategy {

    @Override
    public List<Map<String, Object>> parse(File file) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> lines = Files.readAllLines(file.toPath());
        for (String line : lines) {
            Map<String, Object> row = new HashMap<>();
            row.put("line", line); // generic key for TXT
            rows.add(row);
        }
        return rows;
    }
}
```

---

### **4.6 DynamicMapper.java**

```java
package com.ag.recon.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Paths;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DynamicMapper {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DynamicMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void mapAndPersist(String fileType, List<Map<String, Object>> parsedData) throws Exception {
        // Load mapping config JSON
        JsonNode config = objectMapper.readTree(Paths.get("mapping_config.json").toFile());
        if (!config.get("fileType").asText().equalsIgnoreCase(fileType)) return;

        String table = config.get("table").asText();
        JsonNode columns = config.get("columns");

        for (Map<String, Object> row : parsedData) {
            List<String> colNames = columns.findValues("target").stream().map(JsonNode::asText).collect(Collectors.toList());
            List<Object> values = columns.findValues("source").stream().map(srcNode -> row.get(srcNode.asText())).collect(Collectors.toList());

            String sql = "INSERT INTO " + table + " (" + String.join(",", colNames) + ") VALUES (" +
                    colNames.stream().map(c -> "?").collect(Collectors.joining(",")) + ")";

            int[] types = columns.findValues("type").stream()
                    .mapToInt(node -> switch (node.asText().toUpperCase()) {
                        case "STRING" -> Types.VARCHAR;
                        case "DECIMAL" -> Types.DECIMAL;
                        case "DATE" -> Types.DATE;
                        case "INTEGER" -> Types.INTEGER;
                        default -> Types.VARCHAR;
                    }).toArray();

            jdbcTemplate.update(sql, values.toArray(), types);
        }
    }
}
```

---

### **4.7 FileIngestionService.java**

```java
package com.ag.recon.service;

import com.ag.recon.mapper.DynamicMapper;
import com.ag.recon.parsers.FileParserStrategy;
import com.ag.recon.parsers.ParserFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Map;

@Service
public class FileIngestionService {

    private final ParserFactory parserFactory;
    private final DynamicMapper dynamicMapper;

    public FileIngestionService(ParserFactory parserFactory, DynamicMapper dynamicMapper) {
        this.parserFactory = parserFactory;
        this.dynamicMapper = dynamicMapper;
    }

    public void ingestFile(String filePath) throws Exception {
        File file = new File(filePath);
        String extension = getExtension(file.getName());
        FileParserStrategy parser = parserFactory.getParser(extension);

        if (parser == null) throw new RuntimeException("No parser found for " + extension);

        List<Map<String, Object>> parsedData = parser.parse(file);
        dynamicMapper.mapAndPersist(extension, parsedData);
    }

    private String getExtension(String fileName) {
        int i = fileName.lastIndexOf('.');
        return i > 0 ? fileName.substring(i + 1) : "";
    }
}
```

---

### **4.8 ReconRunner.java (CommandLineRunner)**

```java
package com.ag.recon;

import com.ag.recon.service.FileIngestionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReconRunner implements CommandLineRunner {

    private final FileIngestionService ingestionService;

    public ReconRunner(FileIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    public static void main(String[] args) {
        SpringApplication.run(ReconRunner.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        String filePath = "files/sample.csv"; // Hardcoded file path
        ingestionService.ingestFile(filePath);
        System.out.println("File ingestion completed!");
    }
}
```

---

## **5️⃣ How to Add a New File Format**

1. Implement `FileParserStrategy` for the new format.
2. Annotate it with `@Component`.
3. Register it in `ParserFactory` constructor.
4. Update `mapping_config.json` for this file type.
5. No changes to service/mapper required.

---

✅ This setup is **fully metadata-driven**, **extensible**, and supports **multiple file types**.

---

If you want, I can also provide a **diagram showing database dynamic mapping flow** along with **example insert logs** to visualize it fully.

Do you want me to do that next?
