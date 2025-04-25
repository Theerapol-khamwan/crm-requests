package com.bank.crm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateRequestDto {
	
	@NotBlank(message = "Customer ID is required")
	private String customerId;
	
	@NotBlank(message = "Request type is required")
	private String requestType;
	
	private String requestDetails;

}
