package com.capstoneecho.echo_back.app.stats;

import com.capstoneecho.echo_back.app.common.ApiResponse;
import com.capstoneecho.echo_back.app.jwt.CurrentUser;
import com.capstoneecho.echo_back.app.jwt.JwtPrincipal;
import com.capstoneecho.echo_back.app.stats.dto.StatsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/me")
    public ApiResponse<StatsResponse> me(
            @CurrentUser JwtPrincipal principal,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "month", required = false) Integer month
    ) {
        return ApiResponse.ok(statsService.getMe(principal.userId(), year, month));
    }
}
