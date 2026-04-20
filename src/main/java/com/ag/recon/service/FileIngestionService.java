package com.ag.recon.service;

import com.ag.recon.enums.FileStatus;
import com.ag.recon.mapper.DynamicMapperOld;
import com.ag.recon.mapper.DynamicMapperBulk;
import com.ag.recon.parsers.FileParserStrategy;
import com.ag.recon.parsers.ParserFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Map;

@Service
public class FileIngestionService {

	private final ParserFactory parserFactory;
	private final DynamicMapperBulk dynamicMapperBulk;
	private final ReconFileMasterService fileMasterService;

	public FileIngestionService(ParserFactory parserFactory, DynamicMapperBulk dynamicMapperBulk,
			ReconFileMasterService fileMasterService) {
		this.parserFactory = parserFactory;
		this.dynamicMapperBulk = dynamicMapperBulk;
		this.fileMasterService = fileMasterService;
	}

	public void ingestFile(Long masterId, String filePath, String corpId) throws Exception {

		fileMasterService.updateStatus(masterId, FileStatus.PROCESSING, "SYSTEM");

		File file = new File(filePath);
		String extension = getExtension(file.getName());
		FileParserStrategy parser = parserFactory.getParser(extension);

		if (parser == null)
			throw new RuntimeException("No parser found for " + extension);

		List<Map<String, Object>> parsedData = parser.parse(file);
		dynamicMapperBulk.mapAndPersist(masterId, extension, parsedData, corpId);
	}

	private String getExtension(String fileName) {
		int i = fileName.lastIndexOf('.');
		return i > 0 ? fileName.substring(i + 1) : "";
	}
}