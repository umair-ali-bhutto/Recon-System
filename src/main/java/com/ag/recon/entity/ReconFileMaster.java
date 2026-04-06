package com.ag.recon.entity;

import java.sql.Timestamp;

import com.ag.recon.enums.FileStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "RECON_FILE_MASTER")
public class ReconFileMaster {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "RECON_FILE_MASTER_SEQ")
	@SequenceGenerator(name = "RECON_FILE_MASTER_SEQ", sequenceName = "RECON_FILE_MASTER_SEQ", allocationSize = 1)
	private Long id;

	@Column(name = "corp_id", nullable = false)
	private String corpId;

	@Column(name = "file_name", nullable = false)
	private String fileName;

	@Column(name = "file_path", nullable = false)
	private String filePath;

	@Enumerated(EnumType.STRING)
	@Column(name = "file_status", nullable = false)
	private FileStatus fileStatus;

	@Column(name = "inserted_on", nullable = false)
	private Timestamp insertedOn;

	@Column(name = "inserted_by")
	private String insertedBy;

	@Column(name = "updated_on")
	private Timestamp updatedOn;

	@Column(name = "updated_by")
	private String updatedBy;

	// getter/setter

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getCorpId() {
		return corpId;
	}

	public void setCorpId(String corpId) {
		this.corpId = corpId;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public FileStatus getFileStatus() {
		return fileStatus;
	}

	public void setFileStatus(FileStatus fileStatus) {
		this.fileStatus = fileStatus;
	}

	public Timestamp getInsertedOn() {
		return insertedOn;
	}

	public void setInsertedOn(Timestamp insertedOn) {
		this.insertedOn = insertedOn;
	}

	public String getInsertedBy() {
		return insertedBy;
	}

	public void setInsertedBy(String insertedBy) {
		this.insertedBy = insertedBy;
	}

	public Timestamp getUpdatedOn() {
		return updatedOn;
	}

	public void setUpdatedOn(Timestamp updatedOn) {
		this.updatedOn = updatedOn;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(String updatedBy) {
		this.updatedBy = updatedBy;
	}

}