package com.bank.crm.controller;

import static org.mockito.Mockito.doNothing;

import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientException;

import com.bank.crm.dto.event.TriggerRequestEvent;
import com.bank.crm.entity.ActionStatus;
import com.bank.crm.service.CrmService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityNotFoundException;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.eq;

@WebMvcTest(CrmController.class)
@DisplayName("CRM Controller - Trigger Endpoints Tests")
public class CrmControllerTestTriggerMicroservice {

	@MockitoBean
	private CrmService crmService;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Nested
	@DisplayName("POST /trigger/receive Endpoint")
	class ReceiveTriggerTests {

		private TriggerRequestEvent validTriggerEvent;
		private String expectedSuccessMessage;

		@BeforeEach
		void setUpReceiveTrigger() {
			validTriggerEvent = new TriggerRequestEvent("BackOfficeService", ActionStatus.UPDATE_STATUS, 201L,
					Collections.singletonMap("newStatus", "IN_PROGRESS"));
			expectedSuccessMessage = "Trigger received and processing started for action: "
					+ validTriggerEvent.getAction() + " on ServiceRequest ID: " + validTriggerEvent.getRelatedId();
		}

		@Test
		@DisplayName("POST /api/crm/requests/trigger/receive - Success Case (202 Accepted)")
		void receiveTrigger_whenSuccess_shouldReturnAccepted() throws Exception {

			doNothing().when(crmService).processIncomingTrigger(any(TriggerRequestEvent.class));

			mockMvc.perform(post("/api/crm/requests/trigger/receive").contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(validTriggerEvent))).andExpect(status().isAccepted())
					.andExpect(content().string(expectedSuccessMessage));

			verify(crmService, times(1)).processIncomingTrigger(any(TriggerRequestEvent.class));
		}

		@Test
		@DisplayName("POST /api/crm/requests/trigger/receive - Not Found Case (404)")
		void receiveTrigger_whenServiceThrowsNotFound_shouldReturnNotFoundWithMessage() throws Exception {

			String errorMessage = "ServiceRequest not found with id: " + validTriggerEvent.getRelatedId();
			doThrow(new EntityNotFoundException(errorMessage)).when(crmService)
					.processIncomingTrigger(any(TriggerRequestEvent.class));

			mockMvc.perform(post("/api/crm/requests/trigger/receive").contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(validTriggerEvent))).andExpect(status().isNotFound())
					.andExpect(content().string(errorMessage));

			verify(crmService, times(1)).processIncomingTrigger(any(TriggerRequestEvent.class));
		}

		@Test
		@DisplayName("POST /api/crm/requests/trigger/receive - Internal Server Error Case (500)")
		void receiveTrigger_whenServiceThrowsUnexpectedError_shouldReturnInternalServerErrorWithMessage()
				throws Exception {
			
			String errorMessage = "Unexpected error during save operation";
			doThrow(new RuntimeException(errorMessage)).when(crmService)
					.processIncomingTrigger(any(TriggerRequestEvent.class));

			mockMvc.perform(post("/api/crm/requests/trigger/receive").contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(validTriggerEvent)))
					.andExpect(status().isInternalServerError())
					.andExpect(content().string("Error processing trigger: " + errorMessage));

			verify(crmService, times(1)).processIncomingTrigger(any(TriggerRequestEvent.class));
		}
	}

	@Nested
	@DisplayName("POST /trigger/send/{requestId} Endpoint")
	class SendTriggerTests {

		private Long validRequestId = 301L;
		private ActionStatus validAction = ActionStatus.NOTIFY_STATUS_UPDATE;
		private String expectedSuccessMessage;

		@BeforeEach
		void setUpSendTrigger() {
			expectedSuccessMessage = "Trigger sent successfully for ServiceRequest ID: " + validRequestId
					+ " with action: " + validAction;
		}

		@Test
		@DisplayName("POST /api/crm/requests/trigger/send/{requestId} - Success Case (200 OK)")
		void sendTrigger_whenSuccess_shouldReturnOk() throws Exception {
			
			doNothing().when(crmService).triggerOtherMicroservice(eq(validRequestId), eq(validAction));

			
			mockMvc.perform(post("/api/crm/requests/trigger/send/{requestId}", validRequestId).param("action",
					validAction.name())) // ใช้ .name() เพื่อส่งเป็น String
					.andExpect(status().isOk()).andExpect(content().string(expectedSuccessMessage));

			verify(crmService, times(1)).triggerOtherMicroservice(eq(validRequestId), eq(validAction));
		}

		@Test
		@DisplayName("POST /api/crm/requests/trigger/send/{requestId} - Not Found Case (404)")
		void sendTrigger_whenServiceThrowsNotFound_shouldReturnNotFoundWithMessage() throws Exception {
			String errorMessage = "ServiceRequest not found with id: " + validRequestId;
			doThrow(new EntityNotFoundException(errorMessage)).when(crmService)
					.triggerOtherMicroservice(eq(validRequestId), eq(validAction));

			mockMvc.perform(post("/api/crm/requests/trigger/send/{requestId}", validRequestId).param("action",
					validAction.name())).andExpect(status().isNotFound()).andExpect(content().string(errorMessage));

			verify(crmService, times(1)).triggerOtherMicroservice(eq(validRequestId), eq(validAction));
		}

		@Test
		@DisplayName("POST /api/crm/requests/trigger/send/{requestId} - Internal Server Error Case (500)")
		void sendTrigger_whenServiceThrowsUnexpectedError_shouldReturnInternalServerErrorWithMessage()
				throws Exception {

			String underlyingError = "I/O error on POST request for \"http://.../api/external/actions\": Connection refused";
			String serviceErrorMessage = "Failed to trigger other microservice for request ID: " + validRequestId;
	
			doThrow(new RuntimeException(serviceErrorMessage, new RestClientException(underlyingError)))
					.when(crmService).triggerOtherMicroservice(eq(validRequestId), eq(validAction));

			mockMvc.perform(post("/api/crm/requests/trigger/send/{requestId}", validRequestId).param("action",
					validAction.name())).andExpect(status().isInternalServerError())
					.andExpect(content().string("Failed to send trigger: " + serviceErrorMessage));

			verify(crmService, times(1)).triggerOtherMicroservice(eq(validRequestId), eq(validAction));
		}
	}

}
