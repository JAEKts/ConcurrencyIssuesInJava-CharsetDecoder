# Concurrency Issues In Java - CharsetDecoder
This is a basic SpringBoot API that includes an intentionally vulnerable CharsetDecoder API. This is for testing purposes, and to showcase the errors/issues this issue can cause.

## Quick Start
#### 1. Start the App:
```bash
mvn spring-boot:run  # Or run via your IDE
```

#### 2. Trigger the Vulnerability:

```bash
for i in {1..10000}; do curl "http://localhost:8080/logs/decode?input=Test$i" & done
```
* Runs 10,000 requests in parallel (background jobs).

#### 3. Expected Results:

Application logs: Will show `IllegalStateException` or `MalformedInputException`.
Some responses: May return corrupted strings or HTTP 500 errors.
No OS impact: Your laptop will keep running (just the app may crash).

## Examples of issue manifestation:

#### Application Logs:
```
java.lang.IllegalStateException: Current state = RESET, new state = FLUSHED
```
#### HTTP Response:
```
HTTP/1.1 500 
Content-Type: application/json
Date: Mon, 11 Aug 2025 23:01:08 GMT
Connection: close
Content-Length: 184

{
  "timestamp": "2025-08-11T22:34:13.637642661Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Unexpected error.",
  "path": "/logs/decode",
  "exception": "java.lang.RuntimeException"
}
```
