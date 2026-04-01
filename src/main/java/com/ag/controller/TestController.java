package com.ag.controller;

import org.json.simple.JSONObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ag.config.AgLogger;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class TestController {

	@SuppressWarnings("unchecked")
	@GetMapping(value = "/test")
	public JSONObject test(HttpServletRequest httpServletRequest) {
		JSONObject job = new JSONObject();
		try {
			job.put("code", "0000");
			job.put("message", "Success");
		} catch (Exception e) {
			job.put("code", "0000");
			job.put("message", "Success");
			AgLogger.logError(getClass(), "Exception", e);
		}
		return job;
	}
}
