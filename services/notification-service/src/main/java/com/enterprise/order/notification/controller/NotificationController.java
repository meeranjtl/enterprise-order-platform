package com.enterprise.order.notification.controller;

import com.enterprise.order.notification.dto.NotificationDTO;
import com.enterprise.order.notification.service.NotificationService;
import com.enterprise.order.shared.dto.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/{id}")
    @Operation(summary = "Get a notification by id")
    public BaseResponse<NotificationDTO> get(@PathVariable Long id) {
        return BaseResponse.success(notificationService.get(id), "Notification retrieved successfully");
    }

    @GetMapping(params = "orderId")
    @Operation(summary = "List notifications for an order")
    public BaseResponse<List<NotificationDTO>> getByOrderId(@RequestParam Long orderId) {
        return BaseResponse.success(notificationService.getByOrderId(orderId), "Notifications retrieved successfully");
    }
}
