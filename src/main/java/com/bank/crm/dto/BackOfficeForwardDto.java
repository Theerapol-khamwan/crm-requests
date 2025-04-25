package com.bank.crm.dto;

import java.time.LocalDateTime;
import jakarta.persistence.PrePersist;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BackOfficeForwardDto {
	private Long originalRequestId;
	private String customerId;
	private String requestType;
	private String details;
	private LocalDateTime requestTimestamp;

	@PrePersist
	protected void onCreate() {
		requestTimestamp = LocalDateTime.now();
	}
}
