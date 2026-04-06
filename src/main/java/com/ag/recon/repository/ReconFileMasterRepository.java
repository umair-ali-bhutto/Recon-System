package com.ag.recon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ag.recon.entity.ReconFileMaster;

@Repository
public interface ReconFileMasterRepository extends JpaRepository<ReconFileMaster, Long> {
}