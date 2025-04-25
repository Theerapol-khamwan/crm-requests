package com.bank.crm.dto.event;

import java.util.Map;
import com.bank.crm.entity.ActionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TriggerRequestEvent {

	private String sourceService; // ชื่อ Service ที่เรียกมา (Optional)

	@NotNull(message = "Action cannot be null")
	private ActionStatus action; // การดำเนินการที่ต้องการให้ทำ

	@NotNull(message = "Service Request ID cannot be null")
	private Long relatedId; // *** ID ของ ServiceRequest ที่เกี่ยวข้อง ***

	private Map<String, Object> data;
}
