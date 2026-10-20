package com.omragul.notification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Response containing unread notifications count for a user.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UnreadCountResponseDto {

    private long unreadCount;
}
