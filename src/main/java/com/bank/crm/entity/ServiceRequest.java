package com.bank.crm.entity;

import java.time.LocalDateTime;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "service_requests", schema = "dbo")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String customerId; // รหัสลูกค้า

	@Column(nullable = false)
	private String requestType; // ประเภทคำขอ

	@Column(columnDefinition="nvarchar(max)")
	private String requestDetails; // รายละเอียดคำขอ

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private RequestStatus status; // สถานะคำขอ

	private String assignedTo; // ผู้รับผิดชอบใน Back-office (ถ้ามี)

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
		status = RequestStatus.PENDING; // สถานะเริ่มต้น
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
}
