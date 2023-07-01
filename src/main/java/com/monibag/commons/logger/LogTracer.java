package com.monibag.commons.logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.monibag.commons.logger.config.LogConfig;
import com.monibag.commons.logger.helper.RequestHeaderHelper;
import com.monibag.commons.logger.helper.formatter.TimestampFormatter;
import com.monibag.commons.logger.helper.variable.StringVariable;
import com.monibag.commons.logger.model.Log;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.stereotype.Component;
import javax.servlet.http.HttpServletRequest;
import javax.annotation.PostConstruct;
import java.util.*;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class LogTracer {

	private final LogConfig logConfig;
	private ObjectMapper objectMapper;
	private String traceID;
	private String methodName;

	private String uid;
	private List<String> secureAttributes;

	@PostConstruct
	public void init() {
		if (StringUtils.isNotBlank(logConfig.getSecureAttribute())) {
			this.secureAttributes = Arrays.asList(logConfig.getSecureAttribute().split(StringVariable.COMMA.value()));
		} else {
			this.secureAttributes = new ArrayList<>();
		}
		log.info("Secure Attributes {}", this.secureAttributes);
		this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	public void tracingID(String methodName) {
		this.traceID = TimestampFormatter.format(TimestampFormatter.yyyy_MM_dd_HH_mm_ss_SSSSSS,
				TimestampFormatter.currentSQLTimestamp());
		this.methodName = methodName.concat(" operation");
	}

	public void tracingID(HttpServletRequest request) {
		this.methodName = request.getMethod().concat(" operation");
		uid=getUid(request.getHeader(RequestHeaderHelper.AUTH_TOKEN));
		this.traceID = TimestampFormatter.format(TimestampFormatter.yyyy_MM_dd_HH_mm_ss_SSSSSS,
				TimestampFormatter.currentSQLTimestamp());
		this.methodName = methodName.concat(" operation");
	}

	public void tracingID(String methodName, String uid) {
		this.traceID = TimestampFormatter.format(TimestampFormatter.yyyy_MM_dd_HH_mm_ss_SSSSSS,
				TimestampFormatter.currentSQLTimestamp());
		this.uid = uid;
		this.methodName = methodName.concat(" operation");
	}

	@Pointcut("within(@org.springframework.web.bind.annotation.RestController *) || "
			+ " within(@org.springframework.stereotype.Service *) || "
			+ " within(@org.springframework.stereotype.Repository *)")
	private void appPointCut() {
	}

	@Around("appPointCut()")
	public Object applicationLogger(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
		try {
			log.info("Request for {} {}", methodName,
					jsonObject(Log.builder().traceID(traceID).requestID(uid)
							.className(proceedingJoinPoint.getTarget().getClass().getSimpleName()
									.concat(StringVariable.FULL_STOP.value())
									.concat(proceedingJoinPoint.getSignature().getName()).concat("()"))
							.request(securedObject(proceedingJoinPoint.getArgs())).build()));
		} catch (Exception e) {
			log.error("Request error for {} {}", methodName,
					jsonObject(Log.builder().traceID(traceID).requestID(uid).error(e.getLocalizedMessage()).build()));
		}

		Object object = proceedingJoinPoint.proceed();
		try {
			log.info("Response for {} {}", methodName,
					jsonObject(Log.builder().traceID(traceID).requestID(uid)
							.className(proceedingJoinPoint.getTarget().getClass().getSimpleName()
									.concat(StringVariable.FULL_STOP.value())
									.concat(proceedingJoinPoint.getSignature().getName()).concat("()"))
							.response(securedObject(object)).build()));

		} catch (Exception e) {
			log.error("Response error for {} {}", methodName,
					jsonObject(Log.builder().traceID(traceID).requestID(uid)
							.className(proceedingJoinPoint.getTarget().getClass().getSimpleName()
									.concat(StringVariable.FULL_STOP.value())
									.concat(proceedingJoinPoint.getSignature().getName()).concat("()"))
							.error(e.getLocalizedMessage()).build()));
		}
		return object;
	}

	@AfterThrowing(pointcut = "within(@org.springframework.web.bind.annotation.RestController *) || "
			+ " within(@org.springframework.stereotype.Service *) || "
			+ " within(@org.springframework.stereotype.Repository *)", throwing = "exception")
	public void logError(JoinPoint joinPoint, Exception exception) throws JsonProcessingException {
		try {
			log.error("Exception for {} {}", methodName, jsonObject(Log.builder().traceID(traceID).requestID(uid)
					.className(joinPoint.getTarget().getClass().getSimpleName().concat(StringVariable.FULL_STOP.value())
							.concat(joinPoint.getSignature().getName()).concat("()"))
					.error(exception.getLocalizedMessage()).build()));
		} catch (Exception e) {
			log.error("Exception error for {} {}", methodName, jsonObject(Log.builder().traceID(traceID).requestID(uid)
					.className(joinPoint.getTarget().getClass().getSimpleName().concat(StringVariable.FULL_STOP.value())
							.concat(joinPoint.getSignature().getName()).concat("()"))
					.error(e.getLocalizedMessage()).build()));
		}
	}

	private Object jsonObject(Object object) {
		try {
			return objectMapper.writeValueAsString(object);
		} catch (Exception e) {
			return object;
		}
	}

	private Object securedObject(Object object) {
		try {
			String jsonResponse = objectMapper.writeValueAsString(object);
			JsonNode parentJsonNode = objectMapper.readTree(jsonResponse);
			if (parentJsonNode.getNodeType().toString().equalsIgnoreCase(StringVariable.ARRAY.value())) {
				for (JsonNode childJsonNode : parentJsonNode) {
					Iterator<String> iterator = childJsonNode.fieldNames();
					iterator.forEachRemaining(key -> {
						if (childJsonNode.get(key).getNodeType().toString()
								.equalsIgnoreCase(StringVariable.ARRAY.value())) {
							readKeys(childJsonNode, childJsonNode.get(key));
						} else {
							securedKey(childJsonNode, key);
						}
					});
				}
			} else {
				Iterator<String> iterator = parentJsonNode.fieldNames();
				iterator.forEachRemaining(key -> {
					if (parentJsonNode.get(key).getNodeType().toString()
							.equalsIgnoreCase(StringVariable.ARRAY.value())) {
						readKeys(parentJsonNode, parentJsonNode.get(key));
					} else {
						securedKey(parentJsonNode, key);
					}
				});
			}
			return parentJsonNode;
		} catch (Exception e) {
			return object;
		}
	}

	public void securedKey(JsonNode parentJsonNode, String key) {
		if (secureAttributes.contains(key))
			((ObjectNode) parentJsonNode).put(key, "*********");
	}

	public void readKeys(JsonNode parentJsonNode, JsonNode childJsonNode) {
		for (JsonNode jsonNode : childJsonNode) {
			Iterator<String> iterator = jsonNode.fieldNames();
			iterator.forEachRemaining(key -> {
				if (jsonNode.get(key).getNodeType().toString().equalsIgnoreCase(StringVariable.ARRAY.value())) {
					readKeys(parentJsonNode, jsonNode.get(key));
				} else {
					securedKey(jsonNode, key);
				}
			});
		}
	}

	private String getUid(String jwtToken) {
		try {
			String[] payload = jwtToken.split("\\.");
			JSONParser parser = new JSONParser();
			Base64.Decoder decoder = Base64.getUrlDecoder();
			JSONObject json = (JSONObject) parser.parse(new String(decoder.decode(payload[1])));

			return json.get(RequestHeaderHelper.USER_ID).toString();
		} catch (Exception e) {
			return null;
		}

	}
}
