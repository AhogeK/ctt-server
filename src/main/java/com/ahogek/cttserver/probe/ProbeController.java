package com.ahogek.cttserver.probe;

import com.ahogek.cttserver.common.exception.ErrorCode;
import com.ahogek.cttserver.common.exception.InternalServerErrorException;
import com.ahogek.cttserver.common.exception.NotFoundException;
import com.ahogek.cttserver.common.response.RestApiResponse;

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

/**
 * Health check and readiness probe endpoints for local and test environments.
 *
 * <p>This controller provides endpoints for Kubernetes health checks and local testing. Not active
 * in production environments (restricted by {@code @Profile} annotation).
 *
 * @author Auto-generated
 * @since 0.1.0
 */
@RestController
@RequestMapping("/probe")
@Profile({"local", "test"})
@Validated
public class ProbeController {

    public record EchoRequest(@NotBlank String name, @Min(1) int age) {}

    public record ValidRequest(@NotBlank @Size(max = 10) String value) {}

    @GetMapping("/ok")
    public ResponseEntity<RestApiResponse<Map<String, String>>> ok() {
        return ResponseEntity.ok(RestApiResponse.ok(Map.of("status", "alive")));
    }

    @PostMapping("/validate")
    public ResponseEntity<RestApiResponse<Void>> validate(@Valid @RequestBody EchoRequest req) {
        return ResponseEntity.ok(RestApiResponse.ok());
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
    public ResponseEntity<RestApiResponse<Void>> missingParam(@RequestParam String param) {
        return ResponseEntity.ok(RestApiResponse.ok());
    }

    @GetMapping("/illegal-arg")
    public ResponseEntity<RestApiResponse<Void>> illegalArg(
            @RequestParam(required = false) String value) {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        return ResponseEntity.ok(RestApiResponse.ok());
    }

    @GetMapping("/constraint-violation")
    public ResponseEntity<RestApiResponse<Void>> constraintViolation(
            @RequestParam @Pattern(regexp = "^[a-z]+$") String value) {
        return ResponseEntity.ok(RestApiResponse.ok());
    }
}
