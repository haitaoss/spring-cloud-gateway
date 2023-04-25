package cn.haitaoss.config;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractNameValueGatewayFilterFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-18 16:51
 * 以下示例分别显示了如何设置全局前置和后置过滤器：
 */
@Component
public class MyGlobalFilter {
    @Component
    public static class RouteIdGlobalFilter implements GlobalFilter, Ordered {
        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            Map<String, String> map = new HashMap<>(1);
            map.put("routeId", exchange.getAttributeOrDefault(
                    ServerWebExchangeUtils.GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR,
                    "empty"));
            ServerWebExchangeUtils.putUriTemplateVariables(exchange, map);
            return chain.filter(exchange).then();
        }

        @Override
        public int getOrder() {
            return Integer.MIN_VALUE;
        }
    }

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
                                .rewritePath("/consumingServiceEndpoint", "/backingServiceEndpoint")
                        )
                        // 路由的目标地址
                        .uri("lb://backing-service:8088")
                ).build();
    }


    @Bean
    public GlobalFilter customGlobalPostFilter() {
        return (exchange, chain) -> chain.filter(exchange)
                .then(Mono.just(exchange))
                .map(serverWebExchange -> {
                    //adds header to response
                    serverWebExchange.getResponse().getHeaders().set("CUSTOM-RESPONSE-HEADER",
                            HttpStatus.OK.equals(serverWebExchange.getResponse().getStatusCode()) ? "It worked" : "It did not work");
                    return serverWebExchange;
                })
                .then();
    }

    // @Bean
    public AbstractNameValueGatewayFilterFactory RouteIdGatewayFilterFactory() {
        return new AbstractNameValueGatewayFilterFactory() {
            @Override
            public String name() {
                return "RouteId";
            }

            @Override
            public GatewayFilter apply(NameValueConfig config) {
                return new GatewayFilter() {
                    @Override
                    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                        Map<String, String> map = new HashMap<>(1);
                        map.put("routeId", exchange.getAttributeOrDefault(
                                ServerWebExchangeUtils.GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR,
                                "empty"));
                        ServerWebExchangeUtils.putUriTemplateVariables(exchange, map);
                        return chain.filter(exchange);
                    }
                };
            }
        };
    }
}
