package com.example.demo;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.CircuitBreaker;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableRetry
public class SpringRetryCircuitBreakerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringRetryCircuitBreakerApplication.class, args);
	}

	@Bean
	public RetryTemplate retryTemplate() {
		RetryTemplate retryTemplate = new RetryTemplate();

		FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
		fixedBackOffPolicy.setBackOffPeriod(1000l);
		retryTemplate.setBackOffPolicy(fixedBackOffPolicy);

		SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
		retryPolicy.setMaxAttempts(3);
		retryTemplate.setRetryPolicy(retryPolicy);

		return retryTemplate;
	}
}

@RestController()
@RequestMapping("/client")
class RobustClientController {

	@Autowired
	private RobustService robustService;

	@GetMapping("/customer/name")
	public String customerName() throws RuntimeException {
		try {
			//return robustService.getExternalCustomerName();
			return robustService.resilientCustomerName();
		} catch (RuntimeException e) {
			throw new RuntimeException("ShakyExternalService is down");
		}

	}
}

@Service
class RobustService {

	private static Logger logger = LoggerFactory.getLogger(RobustService.class);
	
	@Autowired RetryTemplate retryTemplate;
	
	
	
	public String getExternalCustomerName() {
		ResponseEntity<String> exchange = new RestTemplate().exchange("http://localhost:8080/api/customer/name",
				HttpMethod.GET, null, new ParameterizedTypeReference<String>() {
				});
		return exchange.getBody();
	}
	
	@CircuitBreaker(maxAttempts=3,openTimeout=15000l, resetTimeout=30000l)
	public String resilientCustomerName() {
		return retryTemplate.execute(new RetryCallback<String, RuntimeException>() {
		    @Override
		    public String doWithRetry(RetryContext context) {
		    	logger.info(String.format("Retry count %d", context.getRetryCount()));
		    	return getExternalCustomerName();
				
		    }
		  });
	}
	
	@Recover
	public String fallback(Throwable e) {
		logger.info("returning name from fallback method");
		return "Mini";
	}
	
}

@RestController()
@RequestMapping("/api")
class ShakyExternalService {

	private static Logger logger = LoggerFactory.getLogger(ShakyExternalService.class);

	@GetMapping("/customer/name")
	public String customerName() {
		logger.info("called ShakyExternalService api/customer/name");
		Random randomGenerator = new Random();
		int randomInt = randomGenerator.nextInt(2);
		if (randomInt < 2) {

			throw new ShakyServiceException("Service is unavailable");
		}
		return "Mickey";

	}
}

@SuppressWarnings("serial")
class ShakyServiceException extends RuntimeException {
	
	public ShakyServiceException(String message) {
		super(message);
	}
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}