package com.faithlog.companion;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companions/me/garden/care-reminders")
public class GardenCareReminderController {
    private final GardenCareReminderService reminders;

    public GardenCareReminderController(GardenCareReminderService reminders) {
        this.reminders = reminders;
    }

    @GetMapping
    public ApiResponse<Preference> get(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(new Preference(reminders.enabled(user.userId())));
    }

    @PutMapping
    public ApiResponse<Preference> update(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody Preference request) {
        return ApiResponse.success(new Preference(reminders.setEnabled(user.userId(), request.enabled())));
    }

    public record Preference(@NotNull Boolean enabled) {}
}
