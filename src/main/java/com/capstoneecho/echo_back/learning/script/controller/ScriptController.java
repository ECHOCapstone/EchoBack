package com.capstoneecho.echo_back.learning.script.controller;

import com.capstoneecho.echo_back.global.common.ApiResponse;
import com.capstoneecho.echo_back.global.jwt.CurrentUser;
import com.capstoneecho.echo_back.global.jwt.JwtPrincipal;
import com.capstoneecho.echo_back.learning.script.dto.ScriptDetailResponse;
import com.capstoneecho.echo_back.learning.script.dto.ScriptSummaryResponse;
import com.capstoneecho.echo_back.learning.script.service.ScriptService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scripts")
public class ScriptController {

    private final ScriptService scriptService;

    public ScriptController(ScriptService scriptService) {
        this.scriptService = scriptService;
    }

    @GetMapping("/recommended/today")
    public ApiResponse<List<ScriptSummaryResponse>> recommendedToday(
            @CurrentUser JwtPrincipal principal) {
        return ApiResponse.success(scriptService.recommendToday(principal.userId()));
    }

    @GetMapping("/{scriptId}")
    public ApiResponse<ScriptDetailResponse> get(@PathVariable Long scriptId) {
        return ApiResponse.success(scriptService.getScript(scriptId));
    }
}
