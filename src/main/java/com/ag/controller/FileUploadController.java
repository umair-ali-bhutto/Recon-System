package com.ag.controller;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;

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

	private static final boolean COPY_ONLY = true;

	private static final Path UPLOAD_BASE = Paths.get("Uploader", "uploads").toAbsolutePath().normalize();
	private static final Path PROCESSED_BASE = Paths.get("Uploader", "processed").toAbsolutePath().normalize();

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

		try {
			String safeCorpId = sanitizeCorpId(corpId);
			String safeOriginalName = sanitizeFileName(file.getOriginalFilename());

			// ==============================
			// 1) SAVE IN UPLOAD FOLDER
			// ==============================
			Path uploadDirectory = safeResolve(UPLOAD_BASE, safeCorpId);
			Files.createDirectories(uploadDirectory);

			long millis = System.currentTimeMillis();
			String uploadFileName = "UPLOADS_" + millis + "_" + UUID.randomUUID() + ".csv";

			Date date = new Date(millis);
			AgLogger.logInfo(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date));

			Path uploadedFilePath = safeResolve(uploadDirectory, uploadFileName);
			Files.write(uploadedFilePath, file.getBytes());

			File uploadedFile = uploadedFilePath.toFile();

			ReconFileMaster fileRecord = fileMasterService.createFileRecord(safeCorpId, safeOriginalName,
					uploadedFile.getAbsolutePath(), "SYSTEM");

			AgLogger.logInfo("File saved: " + uploadedFile.getAbsolutePath());

			// ==============================
			// 2) PROCESS FILE
			// ==============================
			ingestionService.ingestFile(fileRecord.getId(), uploadedFile.getAbsolutePath(), safeCorpId);

			// ==============================
			// 3) COPY OR MOVE
			// ==============================
			copyOrMoveToProcessed(uploadedFilePath, safeCorpId, COPY_ONLY);

			fileMasterService.updateStatus(fileRecord.getId(), FileStatus.PROCESSED, "SYSTEM");

			model.addAttribute("message", "File uploaded, processed, and stored successfully!");

		} catch (IllegalArgumentException | SecurityException e) {
			AgLogger.logError(getClass(), "Validation failed", e);
			model.addAttribute("message", e.getMessage());
		} catch (Exception e) {
			AgLogger.logError(getClass(), "File upload failed", e);
			model.addAttribute("message", "Failed to process file: " + e.getMessage());
		}

		return "upload";
	}

	/**
	 * Copy OR Move file safely
	 */
	private void copyOrMoveToProcessed(Path sourcePath, String corpId, boolean copyOnly) throws Exception {

		String currentDate = LocalDate.now().toString();

		Path processedDirectory = safeResolve(PROCESSED_BASE, corpId, currentDate);
		Files.createDirectories(processedDirectory);

		String finalFileName = "PROCESSED_" + System.currentTimeMillis() + "_" + UUID.randomUUID() + ".csv";

		Path targetPath = safeResolve(processedDirectory, finalFileName);

		if (copyOnly) {
			Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
			AgLogger.logInfo("File copied: " + targetPath);
		} else {
			Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
			AgLogger.logInfo("File moved: " + targetPath);
		}
	}

	// ==============================
	// 🔐 SECURITY METHODS
	// ==============================

	private Path safeResolve(Path base, String... parts) {

		Path resolved = base;

		for (String part : parts) {

			// Reject dangerous input early
			if (part.contains("..") || part.contains("/") || part.contains("\\")) {
				throw new SecurityException("Invalid path component: " + part);
			}

			resolved = resolved.resolve(part);
		}

		Path normalizedBase = base.toAbsolutePath().normalize();
		Path normalizedResolved = resolved.toAbsolutePath().normalize();

		if (!normalizedResolved.startsWith(normalizedBase)) {
			throw new SecurityException("Path traversal attempt detected");
		}

		return normalizedResolved;
	}

	private String sanitizeCorpId(String corpId) {

		if (corpId == null || !corpId.matches("\\d{1,10}")) {
			throw new IllegalArgumentException("Invalid Corporate ID.");
		}

		return corpId;
	}

	private String sanitizeFileName(String fileName) {

		if (fileName == null || fileName.trim().isEmpty()) {
			throw new IllegalArgumentException("Invalid file name.");
		}

		String cleanName = Paths.get(fileName).getFileName().toString();

		return cleanName.replaceAll("[^A-Za-z0-9._-]", "_");
	}
}