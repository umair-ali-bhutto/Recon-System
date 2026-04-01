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