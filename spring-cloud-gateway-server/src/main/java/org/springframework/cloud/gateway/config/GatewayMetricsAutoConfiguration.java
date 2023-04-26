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

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayMetricsFilter;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionMetrics;
import org.springframework.cloud.gateway.support.tagsprovider.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.DispatcherHandler;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = GatewayProperties.PREFIX + ".enabled", matchIfMissing = true)
@EnableConfigurationProperties(GatewayMetricsProperties.class)
@AutoConfigureBefore(HttpHandlerAutoConfiguration.class)
@AutoConfigureAfter({ MetricsAutoConfiguration.class, CompositeMeterRegistryAutoConfiguration.class })
@ConditionalOnClass({ DispatcherHandler.class, MeterRegistry.class, MetricsAutoConfiguration.class })
public class GatewayMetricsAutoConfiguration {

	/**
	 * 会从 ServerWebExchange 中得到 请求的Method、响应的状态码等
	 * @return
	 */
	@Bean
	public GatewayHttpTagsProvider gatewayHttpTagsProvider() {
		return new GatewayHttpTagsProvider();
	}

	/**
	 * 会从 ServerWebExchange 中得到 匹配的路由地址
	 * @return
	 */
	@Bean
	@ConditionalOnProperty(name = GatewayProperties.PREFIX + ".metrics.tags.path.enabled")
	public GatewayPathTagsProvider gatewayPathTagsProvider() {
		return new GatewayPathTagsProvider();
	}

	/**
	 * 会从 ServerWebExchange 中得到 routId、route uri
	 * @return
	 */
	@Bean
	public GatewayRouteTagsProvider gatewayRouteTagsProvider() {
		return new GatewayRouteTagsProvider();
	}

	/**
	 * 将 GatewayMetricsProperties 的信息映射成 Tags
	 * @param properties
	 * @return
	 */
	@Bean
	public PropertiesTagsProvider propertiesTagsProvider(GatewayMetricsProperties properties) {
		return new PropertiesTagsProvider(properties.getTags());
	}

	/**
	 * GatewayMetricsFilter 实现 GlobalFilter 接口，
	 * 将 List<GatewayTagsProvider> 返回的信息记录到 MeterRegistry 中
	 * @param meterRegistry
	 * @param tagsProviders
	 * @param properties
	 * @return
	 */
	@Bean
	@ConditionalOnBean(MeterRegistry.class)
	@ConditionalOnProperty(name = GatewayProperties.PREFIX + ".metrics.enabled", matchIfMissing = true)
	// don't use @ConditionalOnEnabledGlobalFilter as the above property may
	// encompass more than just the filter
	public GatewayMetricsFilter gatewayMetricFilter(MeterRegistry meterRegistry,
			List<GatewayTagsProvider> tagsProviders, GatewayMetricsProperties properties) {
		return new GatewayMetricsFilter(meterRegistry, tagsProviders, properties.getPrefix());
	}

	/**
	 * RouteDefinitionMetrics 实现 ApplicationListener<RefreshRoutesEvent> 接口，
	 * 收到事件的逻辑是 RouteDefinitionLocator.getRouteDefinitions().count() 记录到 MeterRegistry 中
	 * @param meterRegistry
	 * @param routeDefinitionLocator
	 * @param properties
	 * @return
	 */
	@Bean
	@ConditionalOnBean(MeterRegistry.class)
	@ConditionalOnProperty(name = GatewayProperties.PREFIX + ".metrics.enabled", matchIfMissing = true)
	public RouteDefinitionMetrics routeDefinitionMetrics(MeterRegistry meterRegistry,
			RouteDefinitionLocator routeDefinitionLocator, GatewayMetricsProperties properties) {
		return new RouteDefinitionMetrics(meterRegistry, routeDefinitionLocator, properties.getPrefix());
	}

}
