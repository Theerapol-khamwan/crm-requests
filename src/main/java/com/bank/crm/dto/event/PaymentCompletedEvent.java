package com.bank.crm.dto.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {
	
	 	@NotBlank(message = "Event ID cannot be blank")
	    private String eventId;

	    @NotNull(message = "Service Request ID cannot be null")
	    private Long serviceRequestId;

	    @NotBlank(message = "Payment transaction reference cannot be blank")
	    private String paymentTransactionRef;

	    @NotNull(message = "Payment amount cannot be null")
	    private BigDecimal amountPaid;

	    @NotNull(message = "Payment timestamp cannot be null")
	    private OffsetDateTime paymentTimestamp;
}
