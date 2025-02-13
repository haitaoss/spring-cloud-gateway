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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.core.style.ToStringCreator;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * TODO: change to RouteLocator? use java dsl
 *
 * @author Spencer Gibb
 */
public class DiscoveryClientRouteDefinitionLocator implements RouteDefinitionLocator {

	private static final Log log = LogFactory.getLog(DiscoveryClientRouteDefinitionLocator.class);

	private final DiscoveryLocatorProperties properties;

	private final String routeIdPrefix;

	private final SimpleEvaluationContext evalCtxt;

	private Flux<List<ServiceInstance>> serviceInstances;

	public DiscoveryClientRouteDefinitionLocator(ReactiveDiscoveryClient discoveryClient,
			DiscoveryLocatorProperties properties) {
		this(discoveryClient.getClass().getSimpleName(), properties);
		serviceInstances = discoveryClient.getServices()
				.flatMap(service -> discoveryClient.getInstances(service).collectList());
	}

	private DiscoveryClientRouteDefinitionLocator(String discoveryClientName, DiscoveryLocatorProperties properties) {
		this.properties = properties;
		// 配置了前缀 就使用
		if (StringUtils.hasText(properties.getRouteIdPrefix())) {
			routeIdPrefix = properties.getRouteIdPrefix();
		}
		else {
			//没配置就使用类名做前缀
			routeIdPrefix = discoveryClientName + "_";
		}
		evalCtxt = SimpleEvaluationContext.forReadOnlyDataBinding().withInstanceMethods().build();
	}

	@Override
	public Flux<RouteDefinition> getRouteDefinitions() {

		SpelExpressionParser parser = new SpelExpressionParser();
		// include 表达式
		Expression includeExpr = parser.parseExpression(properties.getIncludeExpression());
		// url 表达式
		Expression urlExpr = parser.parseExpression(properties.getUrlExpression());

		Predicate<ServiceInstance> includePredicate;
		// 没配置 include ，那么 includePredicate 直接是true
		if (properties.getIncludeExpression() == null || "true".equalsIgnoreCase(properties.getIncludeExpression())) {
			includePredicate = instance -> true;
		}
		else {
			includePredicate = instance -> {
				// 进行 spel 的解析。根对象是 ServiceInstance
				Boolean include = includeExpr.getValue(evalCtxt, instance, Boolean.class);
				if (include == null) {
					return false;
				}
				return include;
			};
		}

		// 遍历 DiscoveryClient 拿到的 List<ServiceInstance>
		return serviceInstances.filter(instances -> !instances.isEmpty())
				// 铺平
				.flatMap(Flux::fromIterable)
				// 使用配置的 includeExpr 过滤
				.filter(includePredicate)
				// 按照 ServiceId 收集成 Map
				.collectMap(ServiceInstance::getServiceId)
				// remove duplicates
				.flatMapMany(map -> Flux.fromIterable(map.values()))
				// 映射成 RouteDefinition，所以会出现 相同的 routeId
				.map(instance -> {
					// 会使用 urlExpr 进行 spel 的解析 得到路由的 uri
					RouteDefinition routeDefinition = buildRouteDefinition(urlExpr, instance);

					final ServiceInstance instanceForEval = new DelegatingServiceInstance(instance, properties);

					// 根据 properties.getPredicates() 生成 RouteDefinition 的 Predicate
					for (PredicateDefinition original : this.properties.getPredicates()) {
						PredicateDefinition predicate = new PredicateDefinition();
						predicate.setName(original.getName());
						for (Map.Entry<String, String> entry : original.getArgs().entrySet()) {
							// 进行 spel 的解析，根对象是 ServiceInstance
							String value = getValueFromExpr(evalCtxt, parser, instanceForEval, entry);
							predicate.addArg(entry.getKey(), value);
						}
						routeDefinition.getPredicates().add(predicate);
					}

					// 根据 properties.getFilters() 生成 RouteDefinition 的 Filter
					for (FilterDefinition original : this.properties.getFilters()) {
						FilterDefinition filter = new FilterDefinition();
						filter.setName(original.getName());
						for (Map.Entry<String, String> entry : original.getArgs().entrySet()) {
							String value = getValueFromExpr(evalCtxt, parser, instanceForEval, entry);
							filter.addArg(entry.getKey(), value);
						}
						routeDefinition.getFilters().add(filter);
					}

					return routeDefinition;
				});
	}

	protected RouteDefinition buildRouteDefinition(Expression urlExpr, ServiceInstance serviceInstance) {
		String serviceId = serviceInstance.getServiceId();
		RouteDefinition routeDefinition = new RouteDefinition();
		// routeId 就是 serviceId
		routeDefinition.setId(this.routeIdPrefix + serviceId);
		// url 进行 spel 的解析
		String uri = urlExpr.getValue(this.evalCtxt, serviceInstance, String.class);
		routeDefinition.setUri(URI.create(uri));
		// add instance metadata
		routeDefinition.setMetadata(new LinkedHashMap<>(serviceInstance.getMetadata()));
		return routeDefinition;
	}

	String getValueFromExpr(SimpleEvaluationContext evalCtxt, SpelExpressionParser parser, ServiceInstance instance,
			Map.Entry<String, String> entry) {
		try {
			Expression valueExpr = parser.parseExpression(entry.getValue());
			return valueExpr.getValue(evalCtxt, instance, String.class);
		}
		catch (ParseException | EvaluationException e) {
			if (log.isDebugEnabled()) {
				log.debug("Unable to parse " + entry.getValue(), e);
			}
			throw e;
		}
	}

	private static class DelegatingServiceInstance implements ServiceInstance {

		final ServiceInstance delegate;

		private final DiscoveryLocatorProperties properties;

		private DelegatingServiceInstance(ServiceInstance delegate, DiscoveryLocatorProperties properties) {
			this.delegate = delegate;
			this.properties = properties;
		}

		@Override
		public String getServiceId() {
			if (properties.isLowerCaseServiceId()) {
				return delegate.getServiceId().toLowerCase();
			}
			return delegate.getServiceId();
		}

		@Override
		public String getHost() {
			return delegate.getHost();
		}

		@Override
		public int getPort() {
			return delegate.getPort();
		}

		@Override
		public boolean isSecure() {
			return delegate.isSecure();
		}

		@Override
		public URI getUri() {
			return delegate.getUri();
		}

		@Override
		public Map<String, String> getMetadata() {
			return delegate.getMetadata();
		}

		@Override
		public String getScheme() {
			return delegate.getScheme();
		}

		@Override
		public String toString() {
			return new ToStringCreator(this).append("delegate", delegate).append("properties", properties).toString();
		}

	}

}
