package com.bank.crm.service;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bank.crm.dto.BackOfficeForwardDto;
import com.bank.crm.dto.CreateRequestDto;
import com.bank.crm.dto.ServiceRequestDto;
import com.bank.crm.dto.UpdateRequestStatusDto;
import com.bank.crm.dto.event.PaymentCompletedEvent;
import com.bank.crm.dto.event.TriggerRequestEvent;
import com.bank.crm.entity.ActionStatus;
import com.bank.crm.entity.RequestStatus;
import com.bank.crm.entity.ServiceRequest;
import com.bank.crm.repository.ServiceRequestRepository;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;

@Service
@Transactional
@Validated
public class CrmService {

	private static final Logger log = LoggerFactory.getLogger(CrmService.class);

	@Value("${backoffice.service.url}")
	private String backOfficeBaseUrl;

	@Value("${payment.service.url}")
	private String paymentBaseUrl;

	@Value("${other.microservice.url}")
	private String otherServiceUrl;

	private ServiceRequestRepository repo;
	private RestTemplate restTemplate;

	public CrmService(ServiceRequestRepository serviceRequestRepository, RestTemplate restTemplate) {
		this.repo = serviceRequestRepository;
		this.restTemplate = restTemplate;
	}

	// Client ---CreateRequestDto: ข้อมูลคำขอ---> CRM Microservice
	// ---บันทึกข้อมูลลงDB(Status: PENDING)---> CRM Microservice
	// ---ServiceRequestDto: ข้อมูลที่บันทึก + ID---> Client
	public ServiceRequestDto createServiceRequest(CreateRequestDto dto) {

		ServiceRequest newRequest = new ServiceRequest();
		newRequest.setCustomerId(dto.getCustomerId());
		newRequest.setRequestType(dto.getRequestType());
		newRequest.setRequestDetails(dto.getRequestDetails());

		ServiceRequest savedRequest = repo.save(newRequest);
		log.info("Created new service request with ID: {}", savedRequest.getId());

		return ServiceRequestDto.fromEntity(savedRequest);

	}

	// CRM Microservice find ServiceRequest By "Id"
	public ServiceRequestDto getServiceRequestById(Long id) {
		ServiceRequest request = repo.findById(id).orElseThrow(
				() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ServiceRequest not found with id: " + id));
		return ServiceRequestDto.fromEntity(request);
	}

	// CRM Microservice find All ServiceRequest
	public List<ServiceRequestDto> getAllServiceRequests() {
		return repo.findAll().stream().map(ServiceRequestDto::fromEntity).collect(Collectors.toList());
	}

	public ServiceRequestDto updateServiceRequestStatus(Long id, UpdateRequestStatusDto dto) {
		ServiceRequest request = repo.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("ServiceRequest not found with id: " + id));

		request.setStatus(dto.getStatus());

		if (dto.getAssignedTo() != null && dto.getAssignedTo().isBlank()) {
			request.setAssignedTo(dto.getAssignedTo());
		}

		ServiceRequest updatedRequest = repo.save(request);
		log.info("Updated status for service request ID {}: {}", id, dto.getStatus());

		return ServiceRequestDto.fromEntity(updatedRequest);

	}

	public ServiceRequestDto forwardRequestToBackOffice(Long id) {
		// --- จำลองการส่งต่อ ---
		// 1. ดึงข้อมูล Entity (เหมือนเดิม)
		ServiceRequest request = repo.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("ServiceRequest not found with id: " + id));

		if (request.getStatus() == RequestStatus.FORWARDED || request.getStatus() == RequestStatus.COMPLETED) {
			log.warn("Request ID {} is already forwarded or completed. Skipping.", id);
			return ServiceRequestDto.fromEntity(request);
		}

		// 2. เตรียมข้อมูลสำหรับส่งไป Back Office (ใช้ DTO)
		BackOfficeForwardDto forwardDto = new BackOfficeForwardDto();
		forwardDto.setOriginalRequestId(id);
		forwardDto.setCustomerId(request.getCustomerId());
		forwardDto.setRequestType(request.getRequestType());
		forwardDto.setDetails(request.getRequestDetails());

		// 3.พยายามส่งข้อมูลไปยัง Back Office
		log.info("Forwarding request ID {} to Back Office URL: {}", id, backOfficeBaseUrl);

		try {
			ResponseEntity<String> response = restTemplate.postForEntity(backOfficeBaseUrl, forwardDto, String.class);

			if (response.getStatusCode().is2xxSuccessful()) {
				log.info("Successfully forwarded request ID {}. Back office response: {}", id,
						response.getStatusCode());

				request.setStatus(RequestStatus.FORWARDED);
				request.setAssignedTo("BackOfficeQueue");
			} else {
				log.error("Back office returned non-successful status for request ID {}: {}", id,
						response.getStatusCode());

				request.setStatus(RequestStatus.FORWARD_FAILED);
				request.setAssignedTo("BackOfficeQueue");
			}
		} catch (RestClientException e) {
			log.error("Error connecting to back office for request ID {}: {}", id, e.getMessage(), e);
			request.setStatus(RequestStatus.FORWARD_FAILED);
			request.setAssignedTo("BackOfficeQueue");
		}

		ServiceRequest finalUpdatedRequest = repo.save(request);
		log.info("Persisted final status [{}] for request ID {}.", finalUpdatedRequest.getStatus(), id);

		return ServiceRequestDto.fromEntity(finalUpdatedRequest);

	}

	// รับTrigger จาก Microservice -> PaymentCompletedTrigger ประมวลผล Event
	// แจ้งการชำระเงินสำเร็จ (Payment Completed) จากระบบภายนอก โดยจะค้นหา Service
	public ServiceRequestDto processPaymentCompletedTrigger(@Valid PaymentCompletedEvent event) {
		log.info("Trigger PaymentCompletedEvent: eventId={}, serviceRequestId={}", event.getEventId(),
				event.getServiceRequestId());

		try {
			// 1. ค้นหา Service Request ที่เกี่ยวข้อง
			Long requestId = event.getServiceRequestId();
			ServiceRequest request = repo.findById(requestId).orElseThrow(() -> new EntityNotFoundException(
					"No ServiceRequest found for ID received from PaymentCompletedEvent: " + requestId));

			log.debug("Found ServiceRequest ID: {} Current Status: {}", request.getId(), request.getStatus());

			// 2. ตรวจสอบสถานะปัจจุบัน (Idempotency & Business Rule Check)
			if (request.getStatus() == RequestStatus.COMPLETED) {
				log.warn("ServiceRequest ID: {} is already in COMPLETED state. Do not repeat for eventId: {}.",
						request.getId(), event.getEventId());
				return null;
			}

			// ถ้าถูกยกเลิกไปแล้ว ไม่ควรเปลี่ยนเป็น Completed
			if (request.getStatus() == RequestStatus.CANCELED) {
				log.error("ServiceRequest ID: {} is in CANCELED state, cannot be changed to COMPLETED from eventId: {}",
						request.getId(), event.getEventId());
				throw new IllegalStateException(
						"Unable to continue: ServiceRequest (ID:" + request.getId() + ") has been canceled.");
			}

			// 3. อัปเดตสถานะและข้อมูล (ถ้าจำเป็น)
			request.setStatus(RequestStatus.COMPLETED);
			request.setRequestDetails("paymentTransactionRef : " + event.getPaymentTransactionRef() + " , "
					+ "amountPaid : " + event.getAmountPaid());
			request.setRequestType("PaymentCompletedTrigger");

			ServiceRequest finalUpdatedRequest = repo.save(request);

			log.info(
					"Successfully updated ServiceRequest ID: {} status to COMPLETED from PaymentCompletedEvent eventId: {}",
					request.getId(), event.getEventId());

			return ServiceRequestDto.fromEntity(finalUpdatedRequest);

		} catch (EntityNotFoundException | IllegalStateException e) {
			log.error("An error occurred while processing PaymentCompletedEvent (eventId: {}): {}", event.getEventId(),
					e.getMessage());
			throw e;
		} catch (Exception e) {
			log.error("An unexpected error occurred while processing PaymentCompletedEvent (eventId: {}): {}",
					event.getEventId(), e.getMessage(), e);
			throw new RuntimeException("An error occurred while processing PaymentCompletedEvent for serviceRequestId: "
					+ event.getServiceRequestId(), e);
		}

	}

	// เมธอดสำหรับประมวลผล Trigger ที่ได้รับจาก Microservice อื่น โดยจะอัปเดต
	// ServiceRequest ที่เกี่ยวข้อง
	// ทำให้การทำงานกับ DB เป็น Transaction เดียวกัน
	public void processIncomingTrigger(@Valid TriggerRequestEvent triggerRequest) {
		log.info("Processing incoming trigger: {}", triggerRequest);

		if (triggerRequest.getRelatedId() == null) {
			log.warn("Incoming trigger is missing relatedId (ServiceRequest ID). Skipping.");
			return;
		}

		// ค้นหา ServiceRequest จาก ID ที่ได้รับมา
		ServiceRequest request = repo.findById(triggerRequest.getRelatedId())
				.orElseThrow(() -> new EntityNotFoundException(
						"ServiceRequest not found with id: " + triggerRequest.getRelatedId()));

		log.info("Found ServiceRequest to update: ID {}", request.getId());

		// ประมวลผลตาม Action ที่ระบุมา
		ActionStatus action = triggerRequest.getAction();
		Map<String, Object> data = triggerRequest.getData();

		if (ActionStatus.UPDATE_STATUS == action) {
			if (data != null && data.containsKey("newStatus")) {
				try {
					String newStatusStr = (String) data.get("newStatus");
					RequestStatus newStatus = RequestStatus.valueOf(newStatusStr.toUpperCase());
					request.setStatus(newStatus);
				} catch (IllegalArgumentException e) {
					log.error("Invalid status value '{}' received in trigger data for action 'update_status'.",
							data.get("newStatus"));

				} catch (ClassCastException e) {
					log.error("Expected 'newStatus' in data to be a String for action 'update_status'. Data: {}", data);
				}
			} else {
				log.warn("Action 'update_status' received but 'newStatus' is missing in data.");
			}
		} else if (ActionStatus.ASSIGN_AGENT == action) {
			if (data != null && data.containsKey("agentId")) {
				String agentId = (String) data.get("agentId");
				request.setAssignedTo(agentId);
				log.info("Assigning agent '{}' to ServiceRequest ID: {}", agentId, request.getId());
			} else {
				log.warn("Action 'assign_agent' received but 'agentId' is missing in data.");
			}
		} else {
			log.warn("Unknown or unhandled action received: {}", action);
		}

		// บันทึกการเปลี่ยนแปลงลง DB
		repo.save(request);
		log.info("Finished processing incoming trigger for ServiceRequest ID: {}", request.getId());

	}

	// เมธอดสำหรับ Trigger Microservice อื่น (ใช้ ServiceRequest data)
	public void triggerOtherMicroservice(Long requestId, ActionStatus action) {
		ServiceRequest request = repo.findById(requestId)
				.orElseThrow(() -> new EntityNotFoundException("ServiceRequest not found with id: " + requestId));

		log.info("Triggering action '{}' on other microservice for ServiceRequest ID: {} (Customer: {})", action,
				requestId, request.getCustomerId());

		String url = otherServiceUrl + "/api/external/actions"; // ตัวอย่าง Endpoint ของ Service ปลายทาง

		Map<String, Object> requestBody = new HashMap<>();
		requestBody.put("sourceService", "my-crm-service");
		requestBody.put("action", action);
		requestBody.put("relatedCrmRequestId", request.getId());
		requestBody.put("customerId", request.getCustomerId());
		requestBody.put("requestType", request.getRequestType());
		requestBody.put("details", request.getRequestDetails());

		try {
			ResponseEntity<String> response = restTemplate.postForEntity(url, requestBody, String.class);
		} catch (RestClientException e) {
			log.error("Error triggering other microservice for request {}: {}", requestId, e.getMessage(), e);
			throw new RuntimeException("Failed to trigger other microservice for request ID: " + requestId, e);
		}

	}

}
