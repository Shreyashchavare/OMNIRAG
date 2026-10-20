package com.omragul.notification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Response for bulk update operations such as mark-all-read.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BatchUpdateResponseDto {

    private long updatedCount;

    private String message;
}
