package com.logging;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/logs")
public class LogController {

    @GetMapping(value = "/decode", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DecodeResponse> decode(@RequestParam("input") String input) {

        if (!StringUtils.hasText(input)) {
            throw new IllegalArgumentException("Query param 'input' must not be blank.");
        }

        // Do the work — if LogDecoder throws anything unexpected, let it propagate (500)
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        String decoded = LogDecoder.decodeInput(bytes); // ensure LogDecoder is thread-safe

        return ResponseEntity
                .ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new DecodeResponse(decoded, decoded.length()));
    }

    public record DecodeResponse(String decoded, int length) {}
}
