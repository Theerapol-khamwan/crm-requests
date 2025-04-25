package com.bank.crm.dto;

import java.time.LocalDateTime;

import com.bank.crm.entity.RequestStatus;
import com.bank.crm.entity.ServiceRequest;

import lombok.Data;


@Data
public class ServiceRequestDto {

	private Long id;
	private String customerId;
	private String requestType;
	private String requestDetails;
	private RequestStatus status;
	private String assignedTo;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	//Entity -> DTO
	public static ServiceRequestDto fromEntity(ServiceRequest entity) {
		if (entity == null)
			return null;
		ServiceRequestDto dto = new ServiceRequestDto();
		dto.setId(entity.getId());
		dto.setCustomerId(entity.getCustomerId());
		dto.setRequestType(entity.getRequestType());
		dto.setRequestDetails(entity.getRequestDetails());
		dto.setStatus(entity.getStatus());
		dto.setAssignedTo(entity.getAssignedTo());
		dto.setCreatedAt(entity.getCreatedAt());
		dto.setUpdatedAt(entity.getUpdatedAt());

		return dto;

	}

}
