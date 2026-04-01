package com.ag.recon.parsers;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface FileParserStrategy {
    List<Map<String, Object>> parse(File file) throws Exception;
}