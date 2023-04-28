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

package org.springframework.cloud.gateway.route;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.event.FilterArgsEvent;
import org.springframework.cloud.gateway.event.PredicateArgsEvent;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.AsyncPredicate;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.cloud.gateway.support.HasRouteId;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * {@link RouteLocator} that loads routes from a {@link RouteDefinitionLocator}.
 *
 * @author Spencer Gibb
 */
public class RouteDefinitionRouteLocator implements RouteLocator {

	/**
	 * Default filters name.
	 */
	public static final String DEFAULT_FILTERS = "defaultFilters";

	protected final Log logger = LogFactory.getLog(getClass());

	private final RouteDefinitionLocator routeDefinitionLocator;

	private final ConfigurationService configurationService;

	private final Map<String, RoutePredicateFactory> predicates = new LinkedHashMap<>();

	private final Map<String, GatewayFilterFactory> gatewayFilterFactories = new HashMap<>();

	private final GatewayProperties gatewayProperties;

	/**
	 * 通过依赖注入得到
	 * @param routeDefinitionLocator
	 * @param predicates
	 * @param gatewayFilterFactories
	 * @param gatewayProperties
	 * @param configurationService
	 */
	public RouteDefinitionRouteLocator(RouteDefinitionLocator routeDefinitionLocator,
			List<RoutePredicateFactory> predicates, List<GatewayFilterFactory> gatewayFilterFactories,
			GatewayProperties gatewayProperties, ConfigurationService configurationService) {
		this.routeDefinitionLocator = routeDefinitionLocator;
		this.configurationService = configurationService;
		/**
		 * 将 List 转成 Map，key 是执行 {@link RoutePredicateFactory#name()} 得到的。
		 * 默认的逻辑是 类名去除 RoutePredicateFactory
		 * 比如 AddHeadRoutePredicateFactory 的key是 AddHead
		 * */
		initFactories(predicates);
		/**
		 *
		 * 逻辑同上 {@link GatewayFilterFactory#name()}
		 * 默认的逻辑是 类名去除 GatewayFilterFactory
		 * 比如 AddRequestHeaderGatewayFilterFactory 的key是 AddRequestHeader
		 * */
		gatewayFilterFactories.forEach(factory -> this.gatewayFilterFactories.put(factory.name(), factory));
		this.gatewayProperties = gatewayProperties;
	}

	private void initFactories(List<RoutePredicateFactory> predicates) {
		predicates.forEach(factory -> {
			String key = factory.name();
			if (this.predicates.containsKey(key)) {
				this.logger.warn("A RoutePredicateFactory named " + key + " already exists, class: "
						+ this.predicates.get(key) + ". It will be overwritten.");
			}
			this.predicates.put(key, factory);
			if (logger.isInfoEnabled()) {
				logger.info("Loaded RoutePredicateFactory [" + key + "]");
			}
		});
	}

	@Override
	public Flux<Route> getRoutes() {
		/**
		 * 通过 RouteDefinitionLocator 得到 RouteDefinition ，然后根据 RouteDefinition 转成 Route
		 * */
		Flux<Route> routes = this.routeDefinitionLocator.getRouteDefinitions().map(this::convertToRoute);

		if (!gatewayProperties.isFailOnRouteDefinitionError()) {
			// instead of letting error bubble up, continue
			routes = routes.onErrorContinue((error, obj) -> {
				if (logger.isWarnEnabled()) {
					logger.warn("RouteDefinition id " + ((RouteDefinition) obj).getId()
							+ " will be ignored. Definition has invalid configs, " + error.getMessage());
				}
			});
		}

		return routes.map(route -> {
			if (logger.isDebugEnabled()) {
				logger.debug("RouteDefinition matched: " + route.getId());
			}
			return route;
		});
	}

	private Route convertToRoute(RouteDefinition routeDefinition) {
		/**
		 * 会根据定义 predicates 的顺序，遍历处理。根据 predicate.getName() 找到 RoutePredicateFactory，
		 * 再使用 factory 生成 AsyncPredicate
		 * */
		AsyncPredicate<ServerWebExchange> predicate = combinePredicates(routeDefinition);
		/**
		 * 先处理通过属性定义的 默认Filter（spring.cloud.gateway.defaultFilters），再根据定义 filters 的顺序，遍历处理。根据 filter.getName() 找到 GatewayFilterFactory，
		 * 再使用 factory 生成 GatewayFilter
		 *
		 * 最后会根据 order 进行排序。
		 * */
		List<GatewayFilter> gatewayFilters = getFilters(routeDefinition);

		// 构造出 Route
		return Route.async(routeDefinition).asyncPredicate(predicate).replaceFilters(gatewayFilters).build();
	}

	@SuppressWarnings("unchecked")
	List<GatewayFilter> loadGatewayFilters(String id, List<FilterDefinition> filterDefinitions) {
		ArrayList<GatewayFilter> ordered = new ArrayList<>(filterDefinitions.size());
		for (int i = 0; i < filterDefinitions.size(); i++) {
			FilterDefinition definition = filterDefinitions.get(i);
			// 根据 definition.getName() 拿到 GatewayFilterFactory
			GatewayFilterFactory factory = this.gatewayFilterFactories.get(definition.getName());
			if (factory == null) {
				throw new IllegalArgumentException(
						"Unable to find GatewayFilterFactory with name " + definition.getName());
			}
			if (logger.isDebugEnabled()) {
				logger.debug("RouteDefinition " + id + " applying filter " + definition.getArgs() + " to "
						+ definition.getName());
			}

			/**
			 * 使用 configurationService 生成 configuration
			 *
			 * 和这个是类似的，看这里就知道了
			 * 		{@link RouteDefinitionRouteLocator#lookup(RouteDefinition, PredicateDefinition)}
			 * */
			// @formatter:off
			Object configuration = this.configurationService.with(factory)
					.name(definition.getName())
					.properties(definition.getArgs())
					.eventFunction((bound, properties) -> new FilterArgsEvent(
							// TODO: why explicit cast needed or java compile fails
							RouteDefinitionRouteLocator.this, id, (Map<String, Object>) properties))
					.bind();
			// @formatter:on

			// some filters require routeId
			// TODO: is there a better place to apply this?
			if (configuration instanceof HasRouteId) {
				HasRouteId hasRouteId = (HasRouteId) configuration;
				// 设置 routeId
				hasRouteId.setRouteId(id);
			}

			// factory 根据 configuration 生成 GatewayFilter
			GatewayFilter gatewayFilter = factory.apply(configuration);
			if (gatewayFilter instanceof Ordered) {
				ordered.add(gatewayFilter);
			}
			else {
				// 默认的 order 值 就是 定义 filter 的顺序
				ordered.add(new OrderedGatewayFilter(gatewayFilter, i + 1));
			}
		}

		return ordered;
	}

	private List<GatewayFilter> getFilters(RouteDefinition routeDefinition) {
		List<GatewayFilter> filters = new ArrayList<>();

		// TODO: support option to apply defaults after route specific filters?
		if (!this.gatewayProperties.getDefaultFilters().isEmpty()) {
			/**
			 * 先添加通过属性定义的默认Filter
			 * spring.cloud.gateway.defaultFilters=[f1,f2]
			 * */
			filters.addAll(loadGatewayFilters(routeDefinition.getId(),
					new ArrayList<>(this.gatewayProperties.getDefaultFilters())));
		}

		final List<FilterDefinition> definitionFilters = routeDefinition.getFilters();
		if (!CollectionUtils.isEmpty(definitionFilters)) {
			// 再添加 RouteDefinition 定义的 filter
			filters.addAll(loadGatewayFilters(routeDefinition.getId(), definitionFilters));
		}

		// 排序
		AnnotationAwareOrderComparator.sort(filters);
		return filters;
	}

	private AsyncPredicate<ServerWebExchange> combinePredicates(RouteDefinition routeDefinition) {
		List<PredicateDefinition> predicates = routeDefinition.getPredicates();
		// routeDefinition 没有定义 predicate ，就设置一个返回 ture 的 AsyncPredicate
		if (predicates == null || predicates.isEmpty()) {
			// this is a very rare case, but possible, just match all
			return AsyncPredicate.from(exchange -> true);
		}

		/**
		 * 获取 AsyncPredicate。
		 *
		 * 会根据 predicate.getName() 拿到 RoutePredicateFactory，执行 RoutePredicateFactory.apply(config) 生成 AsyncPredicate
		 * */
		AsyncPredicate<ServerWebExchange> predicate = lookup(routeDefinition, predicates.get(0));
		// 遍历剩下的 predicate
		for (PredicateDefinition andPredicate : predicates.subList(1, predicates.size())) {
			AsyncPredicate<ServerWebExchange> found = lookup(routeDefinition, andPredicate);
			/**
			 * and 的连接多个 predicate。返回的是这个类型 AndAsyncPredicate
			 *
			 * 其实就是不断的套娃。
			 * */
			predicate = predicate.and(found);
		}

		return predicate;
	}

	@SuppressWarnings("unchecked")
	private AsyncPredicate<ServerWebExchange> lookup(RouteDefinition route, PredicateDefinition predicate) {
		/**
		 * predicates 是根据 BeanFactory 中 RoutePredicateFactory 类型的 bean 生成的。
		 * 所以可以理解成是从 BeanFactory 中得到 RoutePredicateFactory。
		 * */
		RoutePredicateFactory<Object> factory = this.predicates.get(predicate.getName());
		if (factory == null) {
			throw new IllegalArgumentException("Unable to find RoutePredicateFactory with name " + predicate.getName());
		}
		if (logger.isDebugEnabled()) {
			logger.debug("RouteDefinition " + route.getId() + " applying " + predicate.getArgs() + " to "
					+ predicate.getName());
		}

		/**
		 * factory 实现 ShortcutConfigurable 接口，规定如何生成的 属性绑定的Map
		 * factory 实现 Configurable 接口，规定使用 config 是啥
		 *
		 * configurationService 会依赖 factory 生成 属性绑定的Map 得到 Config 的类型
		 * 然后使用 属性绑定的Map + ConversionsService + Validator 实例化 Config ，并且会对 Config 进行属性绑定和属性校验（JSR303）
		 * */
		// @formatter:off
		Object config = this.configurationService.with(factory)
				.name(predicate.getName())
				// 设置属性。会根据这个生成用于属性绑定的Map
				.properties(predicate.getArgs())
				// 定义事件。对 config 完成属性绑定完后，会发布这个事件
				.eventFunction((bound, properties) -> new PredicateArgsEvent(
						RouteDefinitionRouteLocator.this, route.getId(), properties))
				.bind();
		// @formatter:on

		// 根据 config 使用 factory 生成 AsyncPredicate
		return factory.applyAsync(config);
	}

}
