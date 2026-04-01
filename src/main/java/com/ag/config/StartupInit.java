package com.ag.config;

import org.springframework.stereotype.Component;

import com.ag.recon.service.FileIngestionService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class StartupInit {

	private final FileIngestionService ingestionService;

	public StartupInit(FileIngestionService ingestionService) {
		this.ingestionService = ingestionService;
	}

	@PostConstruct
	public void init() {
		AgLogger.logDebug("INIT CALLED");

		try {
			String filePath = "/home/ACCESS/umair.ali/Downloads/RECON-CSV/2.csv";
			ingestionService.ingestFile(filePath, "00004");
			AgLogger.logInfo("File ingestion completed!");
		} catch (Exception e) {
			AgLogger.logError(getClass(), "Exception", e);
		}

	}

	@PreDestroy
	public void destroy() {
		AgLogger.logDebug("DESTROY CALLED");
	}
}
