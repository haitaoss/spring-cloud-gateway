package cn.haitaoss.config;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-18 16:51
 * 以下示例分别显示了如何设置全局前置和后置过滤器：
 */
@Component
public class MyGlobalFilter {
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
    public GlobalFilter customGlobalFilter() {
        return (exchange, chain) -> exchange.getPrincipal()
                // .map(Principal::getName)
                .map(i -> "")
                .defaultIfEmpty("Default User")
                .map(userName -> {
                    //adds header to proxied request
                    exchange.getRequest().mutate().header("CUSTOM-REQUEST-HEADER", userName).build();
                    return exchange;
                })
                .flatMap(chain::filter);
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


}
