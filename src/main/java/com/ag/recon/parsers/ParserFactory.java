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