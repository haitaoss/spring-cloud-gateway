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

package org.springframework.cloud.gateway.handler.predicate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.event.WeightDefinedEvent;
import org.springframework.cloud.gateway.filter.WeightCalculatorWebFilter;
import org.springframework.cloud.gateway.support.WeightConfig;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_PREDICATE_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.WEIGHT_ATTR;

/**
 * @author Spencer Gibb
 */
// TODO: make this a generic Choose out of group predicate?
public class WeightRoutePredicateFactory extends AbstractRoutePredicateFactory<WeightConfig>
		implements ApplicationEventPublisherAware {

	/**
	 * Weight config group key.
	 */
	public static final String GROUP_KEY = WeightConfig.CONFIG_PREFIX + ".group";

	/**
	 * Weight config weight key.
	 */
	public static final String WEIGHT_KEY = WeightConfig.CONFIG_PREFIX + ".weight";

	private static final Log log = LogFactory.getLog(WeightRoutePredicateFactory.class);

	private ApplicationEventPublisher publisher;

	public WeightRoutePredicateFactory() {
		super(WeightConfig.class);
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		this.publisher = publisher;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Arrays.asList(GROUP_KEY, WEIGHT_KEY);
	}

	@Override
	public String shortcutFieldPrefix() {
		return WeightConfig.CONFIG_PREFIX;
	}

	@Override
	public void beforeApply(WeightConfig config) {
		/**
		 * beforeApply 是模板方法， {@link RoutePredicateFactory#apply(Consumer)} 会执行
		 * */
		if (publisher != null) {
			/**
			 * 发布事件。
			 *
			 * {@link WeightCalculatorWebFilter#onApplicationEvent(ApplicationEvent)}
			 * */
			publisher.publishEvent(new WeightDefinedEvent(this, config));
		}
	}

	@Override
	public Predicate<ServerWebExchange> apply(WeightConfig config) {
		// 返回 GatewayPredicate 实例
		return new GatewayPredicate() {
			@Override
			public boolean test(ServerWebExchange exchange) {
				/**
				 * 获取属性，获取不到就设置默认值。这个属性记录的是 <group,routeId> 也就是每个分组根据权重计算得到的 routeId
				 *
				 * 每个请求都会重新计算，在这里设置的
				 * 		{@link WeightCalculatorWebFilter#filter(ServerWebExchange, WebFilterChain)}
				 * */
				Map<String, String> weights = exchange.getAttributeOrDefault(WEIGHT_ATTR, Collections.emptyMap());

				// 拿到 routeId
				String routeId = exchange.getAttribute(GATEWAY_PREDICATE_ROUTE_ATTR);

				// 拿到 组名
				// all calculations and comparison against random num happened in
				// WeightCalculatorWebFilter
				String group = config.getGroup();
				// 包含
				if (weights.containsKey(group)) {
					// 获取
					String chosenRoute = weights.get(group);
					if (log.isTraceEnabled()) {
						log.trace("in group weight: " + group + ", current route: " + routeId + ", chosen route: "
								+ chosenRoute);
					}

					// 一样才放行。从而体现 路由是根据权重来的
					return routeId.equals(chosenRoute);
				}
				else if (log.isTraceEnabled()) {
					log.trace("no weights found for group: " + group + ", current route: " + routeId);
				}

				return false;
			}

			@Override
			public Object getConfig() {
				return config;
			}

			@Override
			public String toString() {
				return String.format("Weight: %s %s", config.getGroup(), config.getWeight());
			}
		};
	}

}
