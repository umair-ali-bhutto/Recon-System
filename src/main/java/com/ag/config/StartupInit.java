package com.ag.config;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class StartupInit {

	@PostConstruct
	public void init() {
		AgLogger.logDebug("INIT CALLED");
	}

	@PreDestroy
	public void destroy() {
		AgLogger.logDebug("DESTROY CALLED");
	}
}
