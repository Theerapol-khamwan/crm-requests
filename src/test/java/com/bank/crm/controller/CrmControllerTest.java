package com.bank.crm.controller;

import java.time.LocalDateTime;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.bank.crm.dto.CreateRequestDto;
import com.bank.crm.dto.ServiceRequestDto;
import com.bank.crm.dto.UpdateRequestStatusDto;
import com.bank.crm.entity.RequestStatus;
import com.bank.crm.service.CrmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@WebMvcTest(CrmController.class)
@DisplayName("CRM Controller - Endpoint Tests")
public class CrmControllerTest {

	@MockitoBean
	private CrmService crmService;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("POST /api/crm/requests - Success Case (201 OK)")
	void whenPostRequest_thenCreateRequest_shouldReturnCreated() throws Exception {
		CreateRequestDto inputDto = new CreateRequestDto();
		inputDto.setCustomerId("CUST123");
		inputDto.setRequestType("Balance Inquiry");
		inputDto.setRequestDetails("Check savings account balance.");

		ServiceRequestDto outputDto = new ServiceRequestDto();
		outputDto.setId(1L);
		outputDto.setCustomerId("CUST123");
		outputDto.setRequestType("Balance Inquiry");
		outputDto.setStatus(RequestStatus.PENDING);
		outputDto.setCreatedAt(LocalDateTime.now());

		given(crmService.createServiceRequest(any(CreateRequestDto.class))).willReturn(outputDto);

		mockMvc.perform(post("/api/crm/requests").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(inputDto))).andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", is(1))).andExpect(jsonPath("$.customerId", is("CUST123")))
				.andExpect(jsonPath("$.status", is(RequestStatus.PENDING.toString())));
	}

	@Test
	@DisplayName("GET /api/crm/requests/{id} - Success Case (200 OK)")
	void whenGetRequestById_givenValidId_shouldReturnRequest() throws Exception {
		Long requestId = 1L;
		ServiceRequestDto requestDto = new ServiceRequestDto();
		requestDto.setId(requestId);
		requestDto.setCustomerId("CUST123");
		requestDto.setStatus(RequestStatus.PENDING);

		given(crmService.getServiceRequestById(requestId)).willReturn(requestDto);

		mockMvc.perform(get("/api/crm/requests/{id}", requestId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is(requestId.intValue())))
				.andExpect(jsonPath("$.customerId", is("CUST123")));
	}

	@Test
	@DisplayName("GET /api/crm/requests/{id} - Not Found Case (404)")
	void whenGetRequestById_givenInvalidId_shouldReturnNotFound() throws Exception {
		Long invalidId = 99L;

		given(crmService.getServiceRequestById(invalidId)).willThrow(
				new ResponseStatusException(HttpStatus.NOT_FOUND, "ServiceRequest not found with id: " + invalidId));

		mockMvc.perform(get("/api/crm/requests/{id}", invalidId)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("GET /api/crm/requests - Success Case (200 OK)")
	void whenGetAllRequests_shouldReturnRequestList() throws Exception {
		ServiceRequestDto request1 = new ServiceRequestDto();
		request1.setId(1L);
		request1.setCustomerId("CUST100");
		ServiceRequestDto request2 = new ServiceRequestDto();
		request2.setId(2L);
		request2.setCustomerId("CUST200");
		List<ServiceRequestDto> allRequests = Arrays.asList(request1, request2);

		given(crmService.getAllServiceRequests()).willReturn(allRequests);

		mockMvc.perform(get("/api/crm/requests")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].id", is(1))).andExpect(jsonPath("$[1].id", is(2)));
	}

	@Test
	@DisplayName("PUT /api/crm/requests/{id}/status - Success Case (200 OK)")
	void whenPutRequestStatus_shouldUpdateRequest_shouldReturnOk() throws Exception {
		Long requestId = 1L;
		UpdateRequestStatusDto updateDto = new UpdateRequestStatusDto();
		updateDto.setStatus(RequestStatus.COMPLETED);

		ServiceRequestDto updatedOutputDto = new ServiceRequestDto();
		updatedOutputDto.setId(requestId);
		updatedOutputDto.setStatus(RequestStatus.COMPLETED);
		updatedOutputDto.setUpdatedAt(LocalDateTime.now());

		given(crmService.updateServiceRequestStatus(eq(requestId), any(UpdateRequestStatusDto.class)))
				.willReturn(updatedOutputDto);

		mockMvc.perform(put("/api/crm/requests/{id}/status", requestId).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(updateDto))).andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is(requestId.intValue())))
				.andExpect(jsonPath("$.status", is(RequestStatus.COMPLETED.toString())));
	}

	@Test
	@DisplayName("POST /api/crm/requests/{id}/forward - Success Case (200 OK)")
	void whenForwardRequest_shouldForward_shouldReturnOk() throws Exception {
		Long requestId = 1L;
		ServiceRequestDto forwardedDto = new ServiceRequestDto();
		forwardedDto.setId(requestId);
		forwardedDto.setStatus(RequestStatus.FORWARDED); // สถานะหลัง forward
		forwardedDto.setAssignedTo("BackOfficeQueue");

		given(crmService.forwardRequestToBackOffice(requestId)).willReturn(forwardedDto);

		mockMvc.perform(post("/api/crm/requests/{id}/forward", requestId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is(requestId.intValue())))
				.andExpect(jsonPath("$.status", is(RequestStatus.FORWARDED.toString())));
	}

}
