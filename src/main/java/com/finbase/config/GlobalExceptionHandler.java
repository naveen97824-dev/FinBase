package com.finbase.config;

import com.finbase.auth.AccountNotActiveException;
import com.finbase.auth.OtpRequestException;
import com.finbase.auth.OtpVerifyException;
import com.finbase.auth.RefreshTokenException;
import com.finbase.auth.RegistrationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors().forEach(error ->
				fieldErrors.put(error.getField(), error.getDefaultMessage()));

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("timestamp", Instant.now());
		body.put("status", HttpStatus.BAD_REQUEST.value());
		body.put("error", "Validation Failed");
		body.put("fields", fieldErrors);

		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(NoSuchElementException.class)
	public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("timestamp", Instant.now());
		body.put("status", HttpStatus.NOT_FOUND.value());
		body.put("error", "Not Found");
		body.put("message", ex.getMessage());

		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
	}

	@ExceptionHandler(OtpRequestException.class)
	public ResponseEntity<Map<String, Object>> handleOtpRequest(OtpRequestException ex) {
		HttpStatus status = switch (ex.getReason()) {
			case MOBILE_NOT_REGISTERED, MOBILE_ALREADY_REGISTERED -> HttpStatus.BAD_REQUEST;
			case ACCOUNT_LOCKED, RESEND_COOLDOWN_ACTIVE, MAX_RESENDS_EXCEEDED,
					HOURLY_REQUEST_LIMIT_EXCEEDED, DAILY_REQUEST_LIMIT_EXCEEDED,
					IP_RATE_LIMIT_EXCEEDED, SOFT_BLOCKED -> HttpStatus.TOO_MANY_REQUESTS;
		};
		return errorBody(status, ex.getReason().name(), ex.getMessage());
	}

	@ExceptionHandler(OtpVerifyException.class)
	public ResponseEntity<Map<String, Object>> handleOtpVerify(OtpVerifyException ex) {
		HttpStatus status = ex.getReason() == OtpVerifyException.Reason.ACCOUNT_LOCKED
				? HttpStatus.TOO_MANY_REQUESTS
				: HttpStatus.UNAUTHORIZED;
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("timestamp", Instant.now());
		body.put("status", status.value());
		body.put("error", ex.getReason().name());
		body.put("message", ex.getMessage());
		body.put("attemptsRemaining", ex.getAttemptsRemaining());
		return ResponseEntity.status(status).body(body);
	}

	@ExceptionHandler(AccountNotActiveException.class)
	public ResponseEntity<Map<String, Object>> handleAccountNotActive(AccountNotActiveException ex) {
		return errorBody(HttpStatus.FORBIDDEN, "Account Not Active", ex.getMessage());
	}

	@ExceptionHandler(RegistrationException.class)
	public ResponseEntity<Map<String, Object>> handleRegistration(RegistrationException ex) {
		return errorBody(HttpStatus.BAD_REQUEST, "Registration Failed", ex.getMessage());
	}

	@ExceptionHandler(RefreshTokenException.class)
	public ResponseEntity<Map<String, Object>> handleRefreshToken(RefreshTokenException ex) {
		return errorBody(HttpStatus.UNAUTHORIZED, "Invalid Refresh Token", ex.getMessage());
	}

	private ResponseEntity<Map<String, Object>> errorBody(HttpStatus status, String error, String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("timestamp", Instant.now());
		body.put("status", status.value());
		body.put("error", error);
		body.put("message", message);
		return ResponseEntity.status(status).body(body);
	}

}
