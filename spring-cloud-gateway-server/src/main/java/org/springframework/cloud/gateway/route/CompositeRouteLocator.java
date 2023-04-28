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

import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * @author Spencer Gibb
 */
public class CompositeRouteLocator implements RouteLocator {

    private final Flux<RouteLocator> delegates;

    public CompositeRouteLocator(Flux<RouteLocator> delegates) {
        /**
         * 通过依赖注入拿到容器中所有的 RouteLocator。
         *
         * 看 {@link GatewayAutoConfiguration#cachedCompositeRouteLocator(List)}
         * */
        this.delegates = delegates;
    }

    @Override
    public Flux<Route> getRoutes() {
        // 迭代 delegates 执行 RouteLocator::getRoutes ，然后把结果铺平成 Flux<Route>
        return this.delegates.flatMapSequential(RouteLocator::getRoutes);
    }

}
