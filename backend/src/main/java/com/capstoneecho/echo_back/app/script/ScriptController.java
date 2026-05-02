package com.capstoneecho.echo_back.app.script;

import com.capstoneecho.echo_back.app.common.ApiResponse;
import com.capstoneecho.echo_back.app.script.dto.ScriptDetailResponse;
import com.capstoneecho.echo_back.app.script.dto.ScriptSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/scripts")
public class ScriptController {

    private final ScriptService scriptService;

    public ScriptController(ScriptService scriptService) {
        this.scriptService = scriptService;
    }

    @GetMapping("/recommended/today")
    public ApiResponse<List<ScriptSummaryResponse>> recommendedToday() {
        return ApiResponse.ok(scriptService.getRecommendedToday());
    }

    @GetMapping("/{scriptId}")
    public ApiResponse<ScriptDetailResponse> detail(@PathVariable Long scriptId) {
        return ApiResponse.ok(scriptService.getDetail(scriptId));
    }
}
