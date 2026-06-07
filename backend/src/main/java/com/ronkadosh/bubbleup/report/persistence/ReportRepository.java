package com.ronkadosh.bubbleup.report.persistence;

import com.ronkadosh.bubbleup.report.model.Report;
import com.ronkadosh.bubbleup.report.model.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    Page<Report> findByStatus(ReportStatus status, Pageable pageable);

    long countByStatus(ReportStatus status);
}
