package com.ag.controller;

import java.io.File;
import java.io.FileOutputStream;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.ag.config.AgLogger;
import com.ag.recon.service.FileIngestionService;

@Controller
public class FileUploadController {

	private final FileIngestionService ingestionService;

	public FileUploadController(FileIngestionService ingestionService) {
		this.ingestionService = ingestionService;
	}

	// Display the upload page
	@GetMapping("/upload")
	public String showUploadPage() {
		return "upload";
	}

	// Handle file upload
	@PostMapping("/upload")
	public String handleFileUpload(@RequestParam("file") MultipartFile file, @RequestParam("corpId") String corpId,
			Model model) {

		if (file.isEmpty()) {
			model.addAttribute("message", "Please select a file to upload!");
			return "upload";
		}

		try {
			String uploadDir = "uploads/";
			File directory = new File(uploadDir);

			// Create directory if it doesn't exist
			if (!directory.exists()) {
				directory.mkdirs();
			}

			// Construct a unique file name
			String originalFilename = file.getOriginalFilename();
			String fileName = "upload-" + System.currentTimeMillis() + "-" + originalFilename;
			File targetFile = new File(directory, fileName);

			// Save the file to the directory
			try (FileOutputStream fos = new FileOutputStream(targetFile)) {
				fos.write(file.getBytes());
			}

			ingestionService.ingestFile(targetFile.getAbsolutePath(), corpId);

			model.addAttribute("message", "File Uploaded Successfully!");

			// Delete temp file after processing
//			tempFile.deleteOnExit();
		} catch (Exception e) {
			AgLogger.logError(getClass(), "File upload failed", e);
			model.addAttribute("message", "Failed to ingest file: " + e.getMessage());
		}

		return "upload";
	}
}