package com.ag.recon.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.ag.recon.entity.ReconFileMaster;
import com.ag.recon.enums.FileStatus;
import com.ag.recon.repository.ReconFileMasterRepository;

@Service
public class ReconFileMasterService {

	private final ReconFileMasterRepository repository;

	public ReconFileMasterService(ReconFileMasterRepository repository) {
		this.repository = repository;
	}

	/**
	 * Insert uploaded file entry
	 */
	public ReconFileMaster createFileRecord(String corpId, String fileName, String filePath, String user) {

		ReconFileMaster fileMaster = new ReconFileMaster();
		fileMaster.setCorpId(corpId);
		fileMaster.setFileName(fileName);
		fileMaster.setFilePath(filePath);
		fileMaster.setFileStatus(FileStatus.UPLOADED);
		fileMaster.setInsertedOn(Timestamp.valueOf(LocalDateTime.now()));
		fileMaster.setInsertedBy(user);

		return repository.save(fileMaster);
	}

	/**
	 * Update processing status
	 */
	public void updateStatus(Long id, FileStatus status, String updatedBy) {

		ReconFileMaster fileMaster = repository.findById(id)
				.orElseThrow(() -> new RuntimeException("File record not found"));

		fileMaster.setFileStatus(status);
		fileMaster.setUpdatedOn(Timestamp.valueOf(LocalDateTime.now()));
		fileMaster.setUpdatedBy(updatedBy);

		repository.save(fileMaster);
	}
}