package com.monibag.commons.logger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@ConfigurationProperties("log")
@Getter
@Setter
@NoArgsConstructor
@Data
@Configuration("logConfig")
public class LogConfig {

	private String secureAttribute;
}
