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

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.cloud.gateway.actuate.AbstractGatewayControllerEndpoint;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.util.ObjectUtils;

import static java.util.Collections.synchronizedMap;

/**
 * @author Spencer Gibb
 */
public class InMemoryRouteDefinitionRepository implements RouteDefinitionRepository {

	// 线程安全的
	private final Map<String, RouteDefinition> routes = synchronizedMap(new LinkedHashMap<String, RouteDefinition>());

	@Override
	public Mono<Void> save(Mono<RouteDefinition> route) {
		/**
		 * Gateway Endpoint 会依赖 RouteDefinitionRepository 类型的bean 记录通过 Endpoint 动态添加的 RouteDefinition
		 *
		 * 源码在这里
		 * 		{@link AbstractGatewayControllerEndpoint#save(String, RouteDefinition)}
		 * */
		return route.flatMap(r -> {
			if (ObjectUtils.isEmpty(r.getId())) {
				return Mono.error(new IllegalArgumentException("id may not be empty"));
			}
			// 存到缓存中
			routes.put(r.getId(), r);
			return Mono.empty();
		});
	}

	@Override
	public Mono<Void> delete(Mono<String> routeId) {
		return routeId.flatMap(id -> {
			if (routes.containsKey(id)) {
				// 从缓存中移除
				routes.remove(id);
				return Mono.empty();
			}
			return Mono.defer(() -> Mono.error(new NotFoundException("RouteDefinition not found: " + routeId)));
		});
	}

	@Override
	public Flux<RouteDefinition> getRouteDefinitions() {
		// 返回缓存中的值
		Map<String, RouteDefinition> routesSafeCopy = new LinkedHashMap<>(routes);
		return Flux.fromIterable(routesSafeCopy.values());
	}

}
