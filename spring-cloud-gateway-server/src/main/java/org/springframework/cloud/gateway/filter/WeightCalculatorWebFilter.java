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

package org.springframework.cloud.gateway.filter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.gateway.handler.predicate.WeightRoutePredicateFactory;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.event.PredicateArgsEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.event.WeightDefinedEvent;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.cloud.gateway.support.WeightConfig;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.style.ToStringCreator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.WEIGHT_ATTR;

/**
 * @author Spencer Gibb
 * @author Alexey Nakidkin
 */
public class WeightCalculatorWebFilter implements WebFilter, Ordered, SmartApplicationListener {

	/**
	 * Order of Weight Calculator Web filter.
	 */
	public static final int WEIGHT_CALC_FILTER_ORDER = 10001;

	private static final Log log = LogFactory.getLog(WeightCalculatorWebFilter.class);

	private final ObjectProvider<RouteLocator> routeLocator;

	private final ConfigurationService configurationService;

	private Random random = null;

	private int order = WEIGHT_CALC_FILTER_ORDER;

	private Map<String, GroupWeightConfig> groupWeights = new ConcurrentHashMap<>();

	private final AtomicBoolean routeLocatorInitialized = new AtomicBoolean();

	public WeightCalculatorWebFilter(ObjectProvider<RouteLocator> routeLocator,
			ConfigurationService configurationService) {
		this.routeLocator = routeLocator;
		this.configurationService = configurationService;
	}

	/* for testing */
	static Map<String, String> getWeights(ServerWebExchange exchange) {
		Map<String, String> weights = exchange.getAttribute(WEIGHT_ATTR);

		if (weights == null) {
			weights = new ConcurrentHashMap<>();
			exchange.getAttributes().put(WEIGHT_ATTR, weights);
		}
		return weights;
	}

	@Override
	public int getOrder() {
		return order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	public void setRandom(Random random) {
		this.random = random;
	}

	@Override
	public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
		// from config file
		return PredicateArgsEvent.class.isAssignableFrom(eventType) ||
		// from java dsl
				WeightDefinedEvent.class.isAssignableFrom(eventType) ||
				// force initialization
				RefreshRoutesEvent.class.isAssignableFrom(eventType);
	}

	@Override
	public boolean supportsSourceType(Class<?> sourceType) {
		return true;
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof PredicateArgsEvent) {
			handle((PredicateArgsEvent) event);
		}
		/**
		 * WeightRoutePredicateFactory 生成 Predicate 时会发布 WeightDefinedEvent 事件
		 * 		{@link WeightRoutePredicateFactory#beforeApply(WeightConfig)}
		 * */
		else if (event instanceof WeightDefinedEvent) {
			// 将配置的 route 分组、权重 信息 简单的处理，然后存起来
			addWeightConfig(((WeightDefinedEvent) event).getWeightConfig());
		}
		else if (event instanceof RefreshRoutesEvent && routeLocator != null) {
			// forces initialization
			if (routeLocatorInitialized.compareAndSet(false, true)) {
				// on first time, block so that app fails to start if there are errors in
				// routes
				// see gh-1574
				routeLocator.ifAvailable(locator -> locator.getRoutes().blockLast());
			}
			else {
				// this preserves previous behaviour on refresh, this could likely go away
				routeLocator.ifAvailable(locator -> locator.getRoutes().subscribe());
			}
		}

	}

	public void handle(PredicateArgsEvent event) {
		Map<String, Object> args = event.getArgs();

		if (args.isEmpty() || !hasRelevantKey(args)) {
			return;
		}

		WeightConfig config = new WeightConfig(event.getRouteId());

		this.configurationService.with(config).name(WeightConfig.CONFIG_PREFIX).normalizedProperties(args).bind();

		addWeightConfig(config);
	}

	private boolean hasRelevantKey(Map<String, Object> args) {
		return args.keySet().stream().anyMatch(key -> key.startsWith(WeightConfig.CONFIG_PREFIX + "."));
	}

	/* for testing */ void addWeightConfig(WeightConfig weightConfig) {
		String group = weightConfig.getGroup();
		GroupWeightConfig config;

		/**
		 * groupWeights 已经记录了当前分组的信息，说明不同的 route 使用了同一个分组
		 * */
		// only create new GroupWeightConfig rather than modify
		// and put at end of calculations. This avoids concurency problems
		// later during filter execution.
		if (groupWeights.containsKey(group)) {
			// 拷贝
			config = new GroupWeightConfig(groupWeights.get(group));
		}
		else {
			config = new GroupWeightConfig(group);
		}

		// 将 route 的权重值，记录到 config.weights 中
		config.weights.put(weightConfig.getRouteId(), weightConfig.getWeight());

		// recalculate

		// 总权重
		// normalize weights
		int weightsSum = 0;

		for (Integer weight : config.weights.values()) {
			weightsSum += weight;
		}

		final AtomicInteger index = new AtomicInteger(0);
		for (Map.Entry<String, Integer> entry : config.weights.entrySet()) {
			String routeId = entry.getKey();
			Integer weight = entry.getValue();
			// 占总权重的比例
			Double nomalizedWeight = weight / (double) weightsSum;

			// 将权重比例 记录起来
			config.normalizedWeights.put(routeId, nomalizedWeight);

			// 范围索引
			// recalculate rangeIndexes
			config.rangeIndexes.put(index.getAndIncrement(), routeId);
		}

		// TODO: calculate ranges
		config.ranges.clear();

		config.ranges.add(0.0);

		List<Double> values = new ArrayList<>(config.normalizedWeights.values());
		for (int i = 0; i < values.size(); i++) {
			Double currentWeight = values.get(i);
			Double previousRange = config.ranges.get(i);
			Double range = previousRange + currentWeight;
			/**
			 * 记录每个权重的递增值。
			 *
			 * 比如 normalizedWeights 中有两个权重：80,20
			 * 那么 ranges 记录的是 [0.0 , 80 , 100]
			 * 此时随机数 65 想知道使用那个 routeId，那就使用左闭右开原则判断 65 在 ranges 的那个索引范围，
			 * 在通过索引值从 rangeIndexes 获取得到 routeId
			 *
			 * 代码在这里 {@link WeightCalculatorWebFilter#filter(ServerWebExchange, WebFilterChain)}
			 * */
			config.ranges.add(range);
		}

		if (log.isTraceEnabled()) {
			log.trace("Recalculated group weight config " + config);
		}
		// 更新
		// only update after all calculations
		groupWeights.put(group, config);
	}

	/* for testing */ Map<String, GroupWeightConfig> getGroupWeights() {
		return groupWeights;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		// 从 exchange 中获取，获取不到就初始化一个Map并设置到 exchange 中
		Map<String, String> weights = getWeights(exchange);

		/**
		 * 遍历配置的 分组权重，随机生成一个权重值，该权重匹配了 routeID 配置的权重
		 * 就记录起来 weights.put(group,routeId)
		 * */
		for (String group : groupWeights.keySet()) {
			GroupWeightConfig config = groupWeights.get(group);

			if (config == null) {
				if (log.isDebugEnabled()) {
					log.debug("No GroupWeightConfig found for group: " + group);
				}
				continue; // nothing we can do, but this is odd
			}

			/**
			 * 生成随机数，作为权重。随机数的值不会大于1，所以肯定会有命中的 routeID
			 * */
			/*
			 * Usually, multiple threads accessing the same random object will have some
			 * performance problems, so we can use ThreadLocalRandom by default
			 */
			Random useRandom = this.random;
			useRandom = useRandom == null ? ThreadLocalRandom.current() : useRandom;
			double r = useRandom.nextDouble();

			/**
			 * 这是通过
			 * {@link WeightCalculatorWebFilter#addWeightConfig(WeightConfig)}
			 * */
			List<Double> ranges = config.ranges;

			if (log.isTraceEnabled()) {
				log.trace("Weight for group: " + group + ", ranges: " + ranges + ", r: " + r);
			}

			for (int i = 0; i < ranges.size() - 1; i++) {
				// 左闭右开原则，在区间内就使用 索引从 rangeIndexes 得到 routeId
				if (r >= ranges.get(i) && r < ranges.get(i + 1)) {
					String routeId = config.rangeIndexes.get(i);
					// 记录到weights，其实也就是存到 exchange 中
					weights.put(group, routeId);
					break;
				}
			}
		}

		if (log.isTraceEnabled()) {
			log.trace("Weights attr: " + weights);
		}

		// 放行
		return chain.filter(exchange);
	}

	/* for testing */ static class GroupWeightConfig {

		String group;

		/**
		 * < RouteID, 权重 >
		 */
		LinkedHashMap<String, Integer> weights = new LinkedHashMap<>();

		/**
		 * < RouteID, 占总权重的比例值 >
		 */
		LinkedHashMap<String, Double> normalizedWeights = new LinkedHashMap<>();

		/**
		 * < 索引 , RouteID >
		 */
		LinkedHashMap<Integer, String> rangeIndexes = new LinkedHashMap<>();

		/**
		 * 权重的叠加列表。[0.0 , 2 , 3 , 5 ]
		 * 比如权重值为 1，那么它是在区间 [0.0,2) 中，所以他的索引是 0 , 就能从 rangeIndexes 中获取索引 0 对应的 routeID
		 */
		List<Double> ranges = new ArrayList<>();

		GroupWeightConfig(String group) {
			this.group = group;
		}

		GroupWeightConfig(GroupWeightConfig other) {
			this.group = other.group;
			this.weights = new LinkedHashMap<>(other.weights);
			this.normalizedWeights = new LinkedHashMap<>(other.normalizedWeights);
			this.rangeIndexes = new LinkedHashMap<>(other.rangeIndexes);
		}

		@Override
		public String toString() {
			return new ToStringCreator(this).append("group", group).append("weights", weights)
					.append("normalizedWeights", normalizedWeights).append("rangeIndexes", rangeIndexes).toString();
		}

	}

}
