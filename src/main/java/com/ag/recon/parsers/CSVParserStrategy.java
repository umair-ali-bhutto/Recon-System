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