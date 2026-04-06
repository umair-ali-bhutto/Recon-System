package com.ag.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.ag.config.AgLogger;
import com.ag.recon.entity.ReconFileMaster;
import com.ag.recon.enums.FileStatus;
import com.ag.recon.service.FileIngestionService;
import com.ag.recon.service.ReconFileMasterService;

@Controller
public class FileUploadController {

	private final FileIngestionService ingestionService;
	private final ReconFileMasterService fileMasterService;

	/**
	 * true = keep original file in uploads + create copy in processed false = move
	 * file from uploads to processed
	 */
	private static final boolean COPY_ONLY = true;

	public FileUploadController(FileIngestionService ingestionService, ReconFileMasterService fileMasterService) {
		this.ingestionService = ingestionService;
		this.fileMasterService = fileMasterService;
	}

	@GetMapping("/upload")
	public String showUploadPage() {
		return "upload";
	}

	@PostMapping("/upload")
	public String handleFileUpload(@RequestParam("file") MultipartFile file, @RequestParam("corpId") String corpId,
			Model model) {

		if (file.isEmpty()) {
			model.addAttribute("message", "Please select a file to upload!");
			return "upload";
		}

		if (corpId == null || corpId.trim().isEmpty()) {
			model.addAttribute("message", "Please provide Corporate ID.");
			return "upload";
		}

		File uploadedFile = null;

		try {
			// ==========================================
			// 1) SAVE IN UPLOAD FOLDER
			// ==========================================
			String uploadDir = "uploads/" + corpId + "/";
			File uploadDirectory = new File(uploadDir);

			if (!uploadDirectory.exists()) {
				uploadDirectory.mkdirs();
			}

			String originalFilename = file.getOriginalFilename();
			String uploadFileName = "UPLOADS_" + System.currentTimeMillis() + "_" + originalFilename;

			uploadedFile = new File(uploadDirectory, uploadFileName);

			try (FileOutputStream fos = new FileOutputStream(uploadedFile)) {
				fos.write(file.getBytes());
			}

			// insert into db
			ReconFileMaster fileRecord = fileMasterService.createFileRecord(corpId, originalFilename,
					uploadedFile.getAbsolutePath(), "SYSTEM");

			AgLogger.logInfo("File saved in upload folder: " + uploadedFile.getAbsolutePath());

			// ==========================================
			// 2) PROCESS FILE
			// ==========================================
			ingestionService.ingestFile(fileRecord.getId(), uploadedFile.getAbsolutePath(), corpId);

			// ==========================================
			// 3) COPY OR MOVE TO PROCESSED
			// ==========================================
			copyOrMoveToProcessed(uploadedFile, corpId, originalFilename, COPY_ONLY);

			fileMasterService.updateStatus(fileRecord.getId(), FileStatus.PROCESSED, "SYSTEM");

			model.addAttribute("message", "File uploaded, processed, and stored successfully!");

		} catch (Exception e) {
			AgLogger.logError(getClass(), "File upload failed Exception", e);
			model.addAttribute("message", "Failed to process file: " + e.getMessage());
		}

		return "upload";
	}

	/**
	 * Copy OR Move file to:
	 * processed/{corpId}/{yyyy-MM-dd}/{currentTimeMillis}_PROCESSED_file.csv
	 */
	private void copyOrMoveToProcessed(File sourceFile, String corpId, String originalFilename, boolean copyOnly)
			throws Exception {

		String currentDate = LocalDate.now().toString();

		String processedDir = "processed/" + corpId + "/" + currentDate + "/";
		File processedDirectory = new File(processedDir);

		if (!processedDirectory.exists()) {
			processedDirectory.mkdirs();
		}

		String finalFileName = "PROCESSED_" + System.currentTimeMillis() + "_" + originalFilename;

		File targetFile = new File(processedDirectory, finalFileName);

		if (copyOnly) {
			// ======================================
			// COPY MODE
			// ======================================
			Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

			AgLogger.logInfo("File copied to processed folder: " + targetFile.getAbsolutePath());

		} else {
			// ======================================
			// MOVE MODE
			// ======================================
			Files.move(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

			AgLogger.logInfo("File moved to processed folder: " + targetFile.getAbsolutePath());
		}
	}
}