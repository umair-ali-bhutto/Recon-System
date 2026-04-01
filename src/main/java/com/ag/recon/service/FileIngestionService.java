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

	public void ingestFile(String filePath, String corpId) throws Exception {
		File file = new File(filePath);
		String extension = getExtension(file.getName());
		FileParserStrategy parser = parserFactory.getParser(extension);

		if (parser == null)
			throw new RuntimeException("No parser found for " + extension);

		List<Map<String, Object>> parsedData = parser.parse(file);
		dynamicMapper.mapAndPersist(extension, parsedData, corpId);
	}

	private String getExtension(String fileName) {
		int i = fileName.lastIndexOf('.');
		return i > 0 ? fileName.substring(i + 1) : "";
	}
}