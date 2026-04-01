package com.ag.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AgLogger {

	private static final Logger DEFAULT_LOGGER = LogManager.getLogger(AgLogger.class);

	public static void logInfo(Class<?> clazz, String message) {
		LogManager.getLogger(clazz).info(message);
	}

	public static void logInfo(String message) {
		DEFAULT_LOGGER.info(message);
	}

	public static void logInfo(String name, String message) {
		DEFAULT_LOGGER.info("{} -> {}", name, message);
	}

	public static void logDebug(Class<?> clazz, String message) {
		LogManager.getLogger(clazz).debug(message);
	}

	public static void logDebug(String message) {
		DEFAULT_LOGGER.debug(message);
	}

	public static void logWarn(Class<?> clazz, String message) {
		LogManager.getLogger(clazz).warn(message);
	}

	public static void logWarn(String message) {
		DEFAULT_LOGGER.warn(message);
	}

	public static void logError(Class<?> clazz, String message, Exception e) {
		LogManager.getLogger(clazz).error(message, e);
	}
}