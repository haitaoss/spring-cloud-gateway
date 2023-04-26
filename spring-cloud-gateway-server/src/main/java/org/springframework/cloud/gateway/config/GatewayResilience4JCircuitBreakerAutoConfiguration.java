/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JAutoConfiguration;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.gateway.config.conditional.ConditionalOnEnabledFilter;
import org.springframework.cloud.gateway.filter.factory.FallbackHeadersGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerResilience4JFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.DispatcherHandler;

/**
 * @author Ryan Baxter
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.cloud.gateway.enabled", matchIfMissing = true)
@AutoConfigureAfter({ ReactiveResilience4JAutoConfiguration.class })
@ConditionalOnClass({ DispatcherHandler.class, ReactiveResilience4JAutoConfiguration.class,
		ReactiveCircuitBreakerFactory.class, ReactiveResilience4JCircuitBreakerFactory.class })
public class GatewayResilience4JCircuitBreakerAutoConfiguration {

	/**
	 * SpringCloudCircuitBreakerResilience4JFilterFactory 实现 GatewayFilterFactory 接口，
	 * 其核心逻辑是使用 ReactiveCircuitBreaker 来执行业务逻辑，当 出现异常 或者 路由请求返回的状态码是期望值 就
	 * 使用 dispatcherHandler 来执行 fallbackUrl，并且会往 ServerWebExchange 设置一个key记录异常对象。
	 *
	 * @param reactiveCircuitBreakerFactory
	 * @param dispatcherHandler
	 * @return
	 */
	@Bean
	@ConditionalOnBean(ReactiveResilience4JCircuitBreakerFactory.class)
	@ConditionalOnEnabledFilter
	public SpringCloudCircuitBreakerResilience4JFilterFactory springCloudCircuitBreakerResilience4JFilterFactory(
			ReactiveResilience4JCircuitBreakerFactory reactiveCircuitBreakerFactory,
			ObjectProvider<DispatcherHandler> dispatcherHandler) {
		return new SpringCloudCircuitBreakerResilience4JFilterFactory(reactiveCircuitBreakerFactory, dispatcherHandler);
	}

	/**
	 * FallbackHeadersGatewayFilterFactory 实现 GatewayFilterFactory 接口，
	 * 其核心逻辑：如果请求是 fallbackUrl 执行的（通过异常key判断），那就设置一些请求头
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnEnabledFilter
	public FallbackHeadersGatewayFilterFactory fallbackHeadersGatewayFilterFactory() {
		return new FallbackHeadersGatewayFilterFactory();
	}

}
