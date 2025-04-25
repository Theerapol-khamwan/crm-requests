package com.bank.crm.controller;

import java.math.BigDecimal;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.bank.crm.dto.ServiceRequestDto;
import com.bank.crm.dto.event.PaymentCompletedEvent;
import com.bank.crm.entity.RequestStatus;
import com.bank.crm.service.CrmService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityNotFoundException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@WebMvcTest(CrmController.class)
@DisplayName("CRM Controller - Payment Completed Trigger Endpoint Tests")
public class CrmControllerTestPaymentTrigger {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CrmService crmService;

	@Autowired
	private ObjectMapper objectMapper;

	private PaymentCompletedEvent validPaymentEvent;
	private ServiceRequestDto expectedServiceRequestDto;

	@BeforeEach
	void setUp() {
		validPaymentEvent = new PaymentCompletedEvent("evt-payment-12345", // eventId
				101L, // serviceRequestId
				"PAYREF789012", // paymentTransactionRef
				new BigDecimal("1500.75"), // amountPaid
				OffsetDateTime.now() // paymentTimestamp
		);

		// เตรียมข้อมูล DTO ที่คาดว่าจะได้รับกลับมาในกรณี Success
		expectedServiceRequestDto = new ServiceRequestDto();
		expectedServiceRequestDto.setId(101L);
		expectedServiceRequestDto.setCustomerId("CUST-XYZ");
		expectedServiceRequestDto.setRequestType("CC_PAYMENT");
		expectedServiceRequestDto.setStatus(RequestStatus.COMPLETED); // สถานะที่คาดหวังหลัง process
		expectedServiceRequestDto.setCreatedAt(LocalDateTime.now().minusDays(1));
		expectedServiceRequestDto.setUpdatedAt(LocalDateTime.now());
	}

	@Test
	@DisplayName("POST /trigger/payment-completed - Success Case (200 OK)")
	void handlePaymentCompletedTrigger_whenSuccess_shouldReturnOkAndDto() throws Exception {

		given(crmService.processPaymentCompletedTrigger(any(PaymentCompletedEvent.class)))
				.willReturn(expectedServiceRequestDto);

	
		mockMvc.perform(post("/api/crm/requests/trigger/payment-completed") 
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(validPaymentEvent)))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id", is(101)))
				.andExpect(jsonPath("$.status", is(RequestStatus.COMPLETED.toString())))
				.andExpect(jsonPath("$.customerId", is("CUST-XYZ")));


		verify(crmService, times(1)).processPaymentCompletedTrigger(any(PaymentCompletedEvent.class));
	}

	@Test
	@DisplayName("POST /trigger/payment-completed - Not Found Case (404)")
	void handlePaymentCompletedTrigger_whenServiceThrowsNotFound_shouldReturnNotFound() throws Exception {
	
		given(crmService.processPaymentCompletedTrigger(any(PaymentCompletedEvent.class)))
				.willThrow(new EntityNotFoundException("Service Request ไม่พบ"));

		mockMvc.perform(post("/api/crm/requests/trigger/payment-completed").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(validPaymentEvent))).andExpect(status().isNotFound());
		
		verify(crmService, times(1)).processPaymentCompletedTrigger(any(PaymentCompletedEvent.class));
	}

}
