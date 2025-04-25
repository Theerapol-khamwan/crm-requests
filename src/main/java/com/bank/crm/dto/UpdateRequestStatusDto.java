package com.bank.crm.dto;

import com.bank.crm.entity.RequestStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateRequestStatusDto {

	@NotNull(message = "Status is required")
	private RequestStatus status;

	private String assignedTo;
}
