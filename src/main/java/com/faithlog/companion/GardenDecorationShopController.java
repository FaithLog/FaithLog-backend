package com.faithlog.companion;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companions/me/garden/decorations")
public class GardenDecorationShopController {
    private final GardenDecorationShopService shop;

    public GardenDecorationShopController(GardenDecorationShopService shop) {
        this.shop = shop;
    }

    @GetMapping
    public ApiResponse<GardenDecorationShopService.Shop> get(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(shop.get(user.userId()));
    }

    @PostMapping("/{id}/purchase")
    public ApiResponse<GardenDecorationShopService.Shop> buy(
        @AuthenticationPrincipal AuthenticatedUser user, @PathVariable String id, @RequestBody Purchase request
    ) {
        return ApiResponse.success(shop.buy(user.userId(), id, request.expectedPrice()));
    }

    public record Purchase(int expectedPrice) {}
}
