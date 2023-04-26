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

package org.springframework.cloud.gateway.discovery;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClientAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.DispatcherHandler;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory.REGEXP_KEY;
import static org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory.REPLACEMENT_KEY;
import static org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory.PATTERN_KEY;
import static org.springframework.cloud.gateway.support.NameUtils.normalizeFilterFactoryName;
import static org.springframework.cloud.gateway.support.NameUtils.normalizeRoutePredicateName;

/**
 * @author Spencer Gibb
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.cloud.gateway.enabled", matchIfMissing = true)
@AutoConfigureBefore(GatewayAutoConfiguration.class)
@AutoConfigureAfter(CompositeDiscoveryClientAutoConfiguration.class)
@ConditionalOnClass({ DispatcherHandler.class, CompositeDiscoveryClientAutoConfiguration.class })
@EnableConfigurationProperties
public class GatewayDiscoveryClientAutoConfiguration {

	/**
	 * 这是一个 PathRoutePredicateFactory，根据 serviceId 进行路由
	 * @return
	 */
	public static List<PredicateDefinition> initPredicates() {
		ArrayList<PredicateDefinition> definitions = new ArrayList<>();
		// TODO: add a predicate that matches the url at /serviceId?

		// add a predicate that matches the url at /serviceId/**
		PredicateDefinition predicate = new PredicateDefinition();
		predicate.setName(normalizeRoutePredicateName(PathRoutePredicateFactory.class));
		predicate.addArg(PATTERN_KEY, "'/'+serviceId+'/**'");
		definitions.add(predicate);
		return definitions;
	}

	/**
	 * 这是一个 RewritePathGatewayFilterFactory，移除 serviceId 路径前缀
	 * @return
	 */
	public static List<FilterDefinition> initFilters() {
		ArrayList<FilterDefinition> definitions = new ArrayList<>();

		// add a filter that removes /serviceId by default
		FilterDefinition filter = new FilterDefinition();
		filter.setName(normalizeFilterFactoryName(RewritePathGatewayFilterFactory.class));
		String regex = "'/' + serviceId + '/?(?<remaining>.*)'";
		String replacement = "'/${remaining}'";
		filter.addArg(REGEXP_KEY, regex);
		filter.addArg(REPLACEMENT_KEY, replacement);
		definitions.add(filter);

		return definitions;
	}

	/**
	 * DiscoveryLocatorProperties 类上标注了 @ConfigurationProperties("spring.cloud.gateway.discovery.locator")
	 * 也就是可以通过配置属性的方式设置属性值，下面的逻辑是设置默认值的意思。
	 * DiscoveryClientRouteDefinitionLocator 会使用这两个属性会作为生成 RouteDefinition 的 Predicate 和 Filter
	 * @return
	 */
	@Bean
	public DiscoveryLocatorProperties discoveryLocatorProperties() {
		DiscoveryLocatorProperties properties = new DiscoveryLocatorProperties();
		// 默认就设置 PathRoutePredicateFactory
		properties.setPredicates(initPredicates());
		// 默认就设置 RewritePathGatewayFilterFactory
		properties.setFilters(initFilters());
		return properties;
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnProperty(value = "spring.cloud.discovery.reactive.enabled", matchIfMissing = true)
	public static class ReactiveDiscoveryClientRouteDefinitionLocatorConfiguration {

		/**
		 * DiscoveryClientRouteDefinitionLocator 实现 RouteDefinitionLocator。
		 * 会根据 ReactiveDiscoveryClient.getServices() 返回的 Flux<ServiceInstance> 生成 Flux<RouteDefinition>
		 * 每个 RouteDefinition 是由 ServiceInstance + DiscoveryLocatorProperties 的内容 配置 路由Uri、Predicate、Filter
		 * 大部分属性值是通过解析 SPEL 表达式得到的，其中根对象是 ServiceInstance，所以说 编写的 SPEL 表达式可以引用 ServiceInstance 中的属性
		 *
		 * @param discoveryClient
		 * @param properties
		 * @return
		 */
		@Bean
		@ConditionalOnProperty(name = "spring.cloud.gateway.discovery.locator.enabled")
		public DiscoveryClientRouteDefinitionLocator discoveryClientRouteDefinitionLocator(
				ReactiveDiscoveryClient discoveryClient, DiscoveryLocatorProperties properties) {
			return new DiscoveryClientRouteDefinitionLocator(discoveryClient, properties);
		}

	}

}
