package com.bank.crm.service;

import java.time.LocalDateTime;

import com.bank.crm.dto.*;
import com.bank.crm.dto.event.*;
import com.bank.crm.entity.*;
import com.bank.crm.repository.ServiceRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CRM Service - Unit Tests")
public class CrmServiceTest {

	@Mock
	private ServiceRequestRepository requestRepository;

	@Mock
	private RestTemplate restTemplate;

	@InjectMocks
	private CrmService crmService;

	private ServiceRequest sampleRequest;
	private ServiceRequestDto sampleRequestDto;
	private Long existingRequestId = 1L;

	@BeforeEach
	void setUp() {
		sampleRequest = new ServiceRequest(existingRequestId, "CUST100", "ACCOUNT_BALANCE",
				"Check balance for account ending 1234", RequestStatus.PENDING, null, null, null);

		sampleRequest.setCreatedAt(LocalDateTime.now().minusDays(1));

		sampleRequestDto = ServiceRequestDto.fromEntity(sampleRequest);
		ReflectionTestUtils.setField(crmService, "backOfficeBaseUrl", "http://mock-backoffice.local");
		ReflectionTestUtils.setField(crmService, "paymentBaseUrl", "http://mock-payment.local");
		ReflectionTestUtils.setField(crmService, "otherServiceUrl", "http://mock-other.local");
	}

	@Test
	@DisplayName("[createServiceRequest] Should save request and return DTO")
	void createServiceRequest_shouldSaveAndReturnDto() {

		CreateRequestDto createDto = new CreateRequestDto();
		createDto.setCustomerId("CUST200");
		createDto.setRequestType("NEW_CARD");
		createDto.setRequestDetails("Request new debit card.");

		when(requestRepository.save(any(ServiceRequest.class))).thenAnswer(invocation -> {
			ServiceRequest reqToSave = invocation.getArgument(0);

			reqToSave.setId(2L);
			reqToSave.setStatus(RequestStatus.PENDING);
			reqToSave.setCreatedAt(LocalDateTime.now());
			return reqToSave;
		});

		ServiceRequestDto resultDto = crmService.createServiceRequest(createDto);

		assertNotNull(resultDto);
		assertEquals(createDto.getCustomerId(), resultDto.getCustomerId());
		assertEquals(createDto.getRequestType(), resultDto.getRequestType());
		assertEquals(RequestStatus.PENDING, resultDto.getStatus());
		assertEquals(2L, resultDto.getId());
		assertNotNull(resultDto.getCreatedAt());

		verify(requestRepository, times(1)).save(any(ServiceRequest.class));
	}

	@Test
	@DisplayName("[getServiceRequestById] Should return DTO when found")
	void getServiceRequestById_whenFound_shouldReturnDto() {
		when(requestRepository.findById(existingRequestId)).thenReturn(Optional.of(sampleRequest));

		ServiceRequestDto resultDto = crmService.getServiceRequestById(existingRequestId);

		assertNotNull(resultDto);
		assertEquals(existingRequestId, resultDto.getId());
		assertEquals(sampleRequest.getCustomerId(), resultDto.getCustomerId());

		verify(requestRepository, times(1)).findById(existingRequestId);
	}

	@Test
	@DisplayName("[getAllServiceRequests] Should return list of DTOs")
	void getAllServiceRequests_shouldReturnListOfDtos() {
		ServiceRequest anotherRequest = new ServiceRequest(2L, "CUST300", "LOAN_INFO", null, RequestStatus.IN_PROGRESS,
				null, LocalDateTime.now(), null);
		when(requestRepository.findAll()).thenReturn(Arrays.asList(sampleRequest, anotherRequest));

		List<ServiceRequestDto> results = crmService.getAllServiceRequests();

		assertNotNull(results);
		assertEquals(2, results.size()); // ตรวจสอบจำนวน
		assertEquals(sampleRequest.getId(), results.get(0).getId());
		assertEquals(anotherRequest.getId(), results.get(1).getId());

		verify(requestRepository, times(1)).findAll();
	}

	@Test
	@DisplayName("[updateServiceRequestStatus] Should update status and return DTO")
	void updateServiceRequestStatus_shouldUpdateStatusAndReturnDto() {
		UpdateRequestStatusDto updateDto = new UpdateRequestStatusDto();
		updateDto.setStatus(RequestStatus.IN_PROGRESS);

		ServiceRequest requestToUpdate = sampleRequest; 
		when(requestRepository.findById(existingRequestId)).thenReturn(Optional.of(requestToUpdate));
		
		when(requestRepository.save(any(ServiceRequest.class))).thenAnswer(invocation -> {
			ServiceRequest saved = invocation.getArgument(0);
			
			saved.setUpdatedAt(LocalDateTime.now());
			return saved;
		});

		ServiceRequestDto resultDto = crmService.updateServiceRequestStatus(existingRequestId, updateDto);

		assertNotNull(resultDto);
		assertEquals(RequestStatus.IN_PROGRESS, resultDto.getStatus());
		assertNotNull(resultDto.getUpdatedAt());

		ArgumentCaptor<ServiceRequest> captor = ArgumentCaptor.forClass(ServiceRequest.class);
		verify(requestRepository, times(1)).findById(existingRequestId);
		verify(requestRepository, times(1)).save(captor.capture());
		assertEquals(RequestStatus.IN_PROGRESS, captor.getValue().getStatus());
	}

	@Test
	@DisplayName("[forwardRequestToBackOffice] Should call RestTemplate and update status to FORWARDED on success")
	void forwardRequestToBackOffice_whenRestTemplateSucceeds_shouldUpdateStatusToForwarded() {
		ServiceRequest requestToForward = sampleRequest;
		when(requestRepository.findById(existingRequestId)).thenReturn(Optional.of(requestToForward));
		when(requestRepository.save(any(ServiceRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ResponseEntity<String> mockSuccessResponse = new ResponseEntity<>("Forwarded OK", HttpStatus.OK);
		when(restTemplate.postForEntity(anyString(), any(BackOfficeForwardDto.class), eq(String.class)))
				.thenReturn(mockSuccessResponse);

		ServiceRequestDto resultDto = crmService.forwardRequestToBackOffice(existingRequestId);

		assertNotNull(resultDto);
		assertEquals(RequestStatus.FORWARDED, resultDto.getStatus());
		assertEquals("BackOfficeQueue", resultDto.getAssignedTo()); 

		verify(requestRepository, times(1)).findById(existingRequestId);
		verify(restTemplate, times(1)).postForEntity(anyString(), any(BackOfficeForwardDto.class), eq(String.class));
		verify(requestRepository, times(1)).save(any(ServiceRequest.class));
	}

	@Test
	@DisplayName("[processPaymentCompletedTrigger] Should update status to COMPLETED when valid")
	void processPaymentCompletedTrigger_whenValid_shouldUpdateStatusToCompleted() {
		// Arrange
		PaymentCompletedEvent event = new PaymentCompletedEvent("evt-pmt-1", existingRequestId, "txn-pmt-1",
				BigDecimal.valueOf(100), OffsetDateTime.now());
		ServiceRequest requestToComplete = sampleRequest;
		when(requestRepository.findById(existingRequestId)).thenReturn(Optional.of(requestToComplete));
		when(requestRepository.save(any(ServiceRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ServiceRequestDto resultDto = crmService.processPaymentCompletedTrigger(event);

		assertNotNull(resultDto);
		assertEquals(RequestStatus.COMPLETED, resultDto.getStatus());
		assertTrue(resultDto.getRequestDetails().contains(event.getPaymentTransactionRef()));
		assertEquals("PaymentCompletedTrigger", resultDto.getRequestType());

		verify(requestRepository, times(1)).findById(existingRequestId);
		ArgumentCaptor<ServiceRequest> captor = ArgumentCaptor.forClass(ServiceRequest.class);
		verify(requestRepository, times(1)).save(captor.capture());
		assertEquals(RequestStatus.COMPLETED, captor.getValue().getStatus());
	}


	@Test
	@DisplayName("[processIncomingTrigger] Should update status when action is UPDATE_STATUS")
	void processIncomingTrigger_whenActionIsUpdateStatus_shouldUpdateStatus() {
		// Arrange
		Map<String, Object> data = new HashMap<>();
		data.put("newStatus", "IN_PROGRESS");
		TriggerRequestEvent event = new TriggerRequestEvent("BackOfficeSvc", ActionStatus.UPDATE_STATUS,
				existingRequestId, data);
		ServiceRequest requestToUpdate = sampleRequest;
		when(requestRepository.findById(existingRequestId)).thenReturn(Optional.of(requestToUpdate));

		crmService.processIncomingTrigger(event);

		ArgumentCaptor<ServiceRequest> captor = ArgumentCaptor.forClass(ServiceRequest.class);
		verify(requestRepository, times(1)).findById(existingRequestId);
		verify(requestRepository, times(1)).save(captor.capture()); 
		ServiceRequest savedRequest = captor.getValue();
		assertEquals(RequestStatus.IN_PROGRESS, savedRequest.getStatus());
	}

	@Test
	@DisplayName("[triggerOtherMicroservice] Should call RestTemplate with correct arguments")
	void triggerOtherMicroservice_shouldCallRestTemplateWithCorrectArguments() {
		ServiceRequest requestToTrigger = sampleRequest;
		ActionStatus action = ActionStatus.NOTIFY_STATUS_UPDATE;
		when(requestRepository.findById(existingRequestId)).thenReturn(Optional.of(requestToTrigger));

		ResponseEntity<String> mockSuccessResponse = new ResponseEntity<>("Trigger OK", HttpStatus.OK);

		lenient().when(restTemplate.postForEntity(anyString(), any(Map.class), eq(String.class)))
				.thenReturn(mockSuccessResponse);

		crmService.triggerOtherMicroservice(existingRequestId, action);

		verify(requestRepository, times(1)).findById(existingRequestId);

		ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
		verify(restTemplate, times(1)).postForEntity(urlCaptor.capture(), bodyCaptor.capture(), eq(String.class));

		assertTrue(urlCaptor.getValue().endsWith("/api/external/actions"), "URL should end with /api/external/actions");
		Map<String, Object> capturedBody = bodyCaptor.getValue();
		assertEquals("my-crm-service", capturedBody.get("sourceService"));
		assertEquals(action, capturedBody.get("action"));
		assertEquals(requestToTrigger.getId(), capturedBody.get("relatedCrmRequestId"));
		assertEquals(requestToTrigger.getCustomerId(), capturedBody.get("customerId"));
		assertEquals(requestToTrigger.getRequestType(), capturedBody.get("requestType"));
		assertEquals(requestToTrigger.getRequestDetails(), capturedBody.get("details"));
	}

}
