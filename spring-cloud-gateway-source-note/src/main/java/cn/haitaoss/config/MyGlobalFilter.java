package cn.haitaoss.config;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
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
public class MyGlobalFilter {
    //    @Component
    public static class RouteIdGlobalFilter implements GlobalFilter, Ordered {
        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            Map<String, String> map = new HashMap<>(1);
            map.put("routeId",
                    exchange.getAttributeOrDefault(ServerWebExchangeUtils.GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR,
                            "empty"
                    )
            );
            ServerWebExchangeUtils.putUriTemplateVariables(exchange, map);
            return chain.filter(exchange)
                    .then();
        }

        @Override
        public int getOrder() {
            return Integer.MIN_VALUE;
        }
    }

    // @Bean
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

    // @Bean
    public RouteLocator remoteAddress(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("id", predicateSpec -> predicateSpec
                        .remoteAddr(XForwardedRemoteAddressResolver.trustAll(), "")
                        .and()
                        .path("")
                        .and()
                        .uri("")).build();
    }

    // @Bean
    public GlobalFilter customGlobalPostFilter() {
        return (exchange, chain) -> chain.filter(exchange)
                .then(Mono.just(exchange))
                .map(serverWebExchange -> {
                    //adds header to response
                    serverWebExchange.getResponse()
                            .getHeaders()
                            .set("CUSTOM-RESPONSE-HEADER", HttpStatus.OK.equals(serverWebExchange.getResponse()
                                    .getStatusCode()) ? "It worked" : "It did not work");
                    return serverWebExchange;
                })
                .then();
    }

    @Bean
    public AbstractGatewayFilterFactory RouteIdGatewayFilterFactory() {
        return new AbstractGatewayFilterFactory<Object>() {

            @Override
            public String name() {
                return "RouteId";
            }

            @Override
            public GatewayFilter apply(Object config) {

                GatewayFilter gatewayFilter = new GatewayFilter() {
                    @Override
                    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                        Map<String, String> map = new HashMap<>(1);
                        map.put("routeId", exchange.getAttributeOrDefault(
                                ServerWebExchangeUtils.GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR, "empty"));
                        ServerWebExchangeUtils.putUriTemplateVariables(exchange, map);
                        return chain.filter(exchange);
                    }
                };
                return new OrderedGatewayFilter(gatewayFilter, -1);
            }
        };
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
}
