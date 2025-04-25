package com.bank.crm.controller;

import java.util.List;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import com.bank.crm.dto.CreateRequestDto;
import com.bank.crm.dto.ServiceRequestDto;
import com.bank.crm.dto.UpdateRequestStatusDto;
import com.bank.crm.dto.event.PaymentCompletedEvent;
import com.bank.crm.dto.event.TriggerRequestEvent;
import com.bank.crm.entity.ActionStatus;
import com.bank.crm.service.CrmService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/crm/requests")
@Tag(name = "CRM Service Request API", description = "APIs for managing customer service requests")
public class CrmController {

	private CrmService crmService;

	public CrmController(CrmService crmService) {
		this.crmService = crmService;
	}

	@PostMapping
	@Operation(summary = "Create a new service request", description = "Receives customer request details and saves it.")
	@ApiResponse(responseCode = "201", description = "Request created successfully")
	@ApiResponse(responseCode = "400", description = "Invalid input data")
	public ResponseEntity<ServiceRequestDto> createRequest(@Valid @RequestBody CreateRequestDto createDto) {
		ServiceRequestDto createdRequest = crmService.createServiceRequest(createDto);
		return new ResponseEntity<>(createdRequest, HttpStatus.CREATED);

	}

	@GetMapping("/{id}")
	@Operation(summary = "Get a service request by ID")
	@ApiResponse(responseCode = "200", description = "Request found")
	@ApiResponse(responseCode = "404", description = "Request not found")
	public ResponseEntity<ServiceRequestDto> ServiceRequestDto(@PathVariable Long id) {
		ServiceRequestDto requestDto = crmService.getServiceRequestById(id);
		return ResponseEntity.ok(requestDto);
	}

	@GetMapping
	@Operation(summary = "Get all service requests")
	@ApiResponse(responseCode = "200", description = "Requests retrieved successfully")
	public ResponseEntity<List<ServiceRequestDto>> getAllRequests() {
		List<ServiceRequestDto> requests = crmService.getAllServiceRequests();
		return ResponseEntity.ok(requests);
	}

	@PutMapping("/{id}/status")
	@Operation(summary = "Update the status of a service request")
	@ApiResponse(responseCode = "200", description = "Status updated successfully")
	@ApiResponse(responseCode = "400", description = "Invalid status data")
	@ApiResponse(responseCode = "404", description = "Request not found")
	public ResponseEntity<ServiceRequestDto> updateRequestStatus(@PathVariable Long id,
			@Valid @RequestBody UpdateRequestStatusDto statusDto) {
		ServiceRequestDto updatedRequest = crmService.updateServiceRequestStatus(id, statusDto);
		return ResponseEntity.ok(updatedRequest);

	}

	@PostMapping("/{id}/forward")
	@Operation(summary = "Forward a request to the back-office (simulation)")
	@ApiResponse(responseCode = "200", description = "Request forwarded successfully")
	@ApiResponse(responseCode = "404", description = "Request not found")
	public ResponseEntity<ServiceRequestDto> forwardRequest(@PathVariable Long id) {
		ServiceRequestDto forwardedRequest = crmService.forwardRequestToBackOffice(id);
		return ResponseEntity.ok(forwardedRequest);
	}

	@PostMapping("/trigger/payment-completed")
	@Operation(summary = "Receive a successful payment notification trigger from the Payment Service")
	@ApiResponse(responseCode = "202", description = "Receive events into the system for processing")
	@ApiResponse(responseCode = "400", description = "The information in the Event is incorrect")
	@ApiResponse(responseCode = "404", description = "No related Service Request found")
	@ApiResponse(responseCode = "409", description = "Unable to process request in current state (Conflict)")
	public ResponseEntity<ServiceRequestDto> handlePaymentCompletedTrigger(
			@RequestBody @Valid PaymentCompletedEvent event) {
		try {
			ServiceRequestDto paymentCompletedTrigger = crmService.processPaymentCompletedTrigger(event);
			return ResponseEntity.ok(paymentCompletedTrigger);
		} catch (EntityNotFoundException e) {
			return ResponseEntity.notFound().build();
		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.CONFLICT).build();
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}

	}

	
	@PostMapping("/trigger/receive")
	@Operation(summary = "Receive Trigger from Another Microservice")
	@ApiResponse(responseCode = "202", description = "Trigger received successfully and accepted for processing.")
	@ApiResponse(responseCode = "404", description = "Not Found - The ServiceRequest specified by 'relatedId' was not found.")
	@ApiResponse(responseCode = "500", description = "Internal Server Error - An unexpected error occurred while processing the trigger.")
	public ResponseEntity<String> receiveTrigger(@Valid @RequestBody TriggerRequestEvent triggerRequest) {
		try {
			crmService.processIncomingTrigger(triggerRequest);

			return ResponseEntity.accepted().body("Trigger received and processing started for action: "
					+ triggerRequest.getAction() + " on ServiceRequest ID: " + triggerRequest.getRelatedId());
		} catch (EntityNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("Error processing trigger: " + e.getMessage());
		}

	}

	
	@PostMapping("/trigger/send/{requestId}")
	@Operation(summary = "Send Trigger to Another Microservice")
	@ApiResponse(responseCode = "200", description = "Trigger sent successfully to the downstream microservice.")
	@ApiResponse(responseCode = "404", description = "Not Found - The ServiceRequest specified by 'requestId' was not found.")
	@ApiResponse(responseCode = "500", description = "Internal Server Error - Failed to send the trigger, possibly due to an issue calling the downstream service or an internal error.")
	public ResponseEntity<String> sendTrigger(@PathVariable Long requestId, @RequestParam ActionStatus action) {

		try {
			crmService.triggerOtherMicroservice(requestId, action);
			return ResponseEntity
					.ok("Trigger sent successfully for ServiceRequest ID: " + requestId + " with action: " + action);
		} catch (EntityNotFoundException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());

		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("Failed to send trigger: " + e.getMessage());
		}

	}
}
