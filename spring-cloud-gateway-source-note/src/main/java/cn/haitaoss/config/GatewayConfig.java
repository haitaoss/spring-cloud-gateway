package cn.haitaoss.config;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.support.HasRouteId;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-18 16:51
 * 以下示例分别显示了如何设置全局前置和后置过滤器：
 */
@Component
public class GatewayConfig {

    /**
     * 使用 RouteLocatorBuilder 通过编码的方式生成 RouteLocator
     * @param builder
     * @return
     */
    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("circuitbreaker_route", predicateSpec -> predicateSpec
                        // predicate 匹配的 path
                        .path("/consumingServiceEndpoint")
                        .filters(gatewayFilterSpec -> gatewayFilterSpec
                                // circuitBreaker WebFilter
                                //.circuitBreaker(config -> config.setName("myCircuitBreaker").setFallbackUri("forward:/inCaseOfFailureUseThis"))
                                // rewritePath WebFilter
                                .rewritePath("/consumingServiceEndpoint", "/backingServiceEndpoint"))
                        // 路由的目标地址
                        .uri("lb://backing-service:8088"))
                .build();
    }

    @Component
    public static class RouteIdGatewayFilterFactory extends AbstractGatewayFilterFactory<HasRouteIdConfig> {
        @Override
        public Class<HasRouteIdConfig> getConfigClass() {
            return HasRouteIdConfig.class;
        }

        @Override
        public String name() {
            return "RouteId";
        }

        @Override
        public GatewayFilter apply(HasRouteIdConfig config) {
            return (exchange, chain) -> {
                Map<String, String> map = new HashMap<>(1);
                map.put("routeId", config.getRouteId());
                ServerWebExchangeUtils.putUriTemplateVariables(exchange, map);
                return chain.filter(exchange);
            };
        }
    }

    @Component
    public static class HaitaoRoutePredicateFactory implements RoutePredicateFactory<Config> {
        @Override
        public Predicate<ServerWebExchange> apply(Config config) {
            return exchange -> true;
        }
    }

    @Component
    public static class LogGatewayFilterFactory implements GatewayFilterFactory<Config> {

        @Override
        public GatewayFilter apply(Config config) {
            System.out.println("LogGatewayFilterFactory...");
            return (exchange, chain) -> chain.filter(exchange);
        }
    }

    @Component
    public static class LogQueryParamsGlobalFilter implements GlobalFilter {
        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            System.out.println(exchange.getRequest().getQueryParams());
            return chain.filter(exchange);
        }
    }

    public static class Config {
    }

    public static class HasRouteIdConfig implements HasRouteId {
        private String routeId;
        @Override
        public void setRouteId(String routeId) {
            this.routeId = routeId;
        }

        @Override
        public String getRouteId() {
            return routeId;
        }
    }
}
