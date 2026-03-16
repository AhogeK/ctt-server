package com.ahogek.cttserver.probe;

import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.InternalServerErrorException;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.response.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/probe")
@Profile({"local", "test"})
@Validated
public class ProbeController {

    public record EchoRequest(@NotBlank String name, @Min(1) int age) {}

    public record ValidRequest(@NotBlank @Size(max = 10) String value) {}

    @GetMapping("/ok")
    public ResponseEntity<ApiResponse<Map<String, String>>> ok() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "alive")));
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<Void>> validate(@Valid @RequestBody EchoRequest req) {
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/biz-error")
    public ResponseEntity<Void> bizError() {
        throw new NotFoundException(ErrorCode.COMMON_002);
    }

    @GetMapping("/sys-error")
    public ResponseEntity<Void> sysError() {
        throw new InternalServerErrorException("intentional failure");
    }

    @GetMapping("/missing-param")
    public ResponseEntity<ApiResponse<Void>> missingParam(@RequestParam String param) {
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/illegal-arg")
    public ResponseEntity<ApiResponse<Void>> illegalArg(
            @RequestParam(required = false) String value) {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/constraint-violation")
    public ResponseEntity<ApiResponse<Void>> constraintViolation(
            @RequestParam @Pattern(regexp = "^[a-z]+$") String value) {
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
