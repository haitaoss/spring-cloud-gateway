package cn.haitaoss;

import cn.haitaoss.config.MyRoutePredicateFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.cloud.gateway.config.PropertiesRouteDefinitionLocator;
import org.springframework.cloud.gateway.config.conditional.OnEnabledComponent;
import org.springframework.cloud.gateway.config.conditional.OnEnabledGlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.cloud.gateway.filter.WebsocketRoutingFilter;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRouteLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-18 09:36
 *
 */
@SpringBootApplication
@RestController
@Slf4j
public class Main {
    /**
     * 您应该为您可能想要这样做的任何路由配置此过滤器 spring.cloud.gateway.default-filters
     * */

    @Autowired
    private ApplicationContext applicationContext;

    @RequestMapping("index")
    public Object index(
            ServerWebExchange exchange,
            @RequestParam(value = "routeId", required = false) String routeId) {
        HashMap<Object, Object> map = new HashMap<>();
        map.put("routeId", routeId);
        map.put("请求的地址", exchange.getRequest().getURI().toString());
        return map;
    }

    @RequestMapping("cb_error")
    public ResponseEntity<Object> cb_error() {
        log.warn("测试断路器路由...");
        return ResponseEntity.status(500).build();
    }

    public static void main(String[] args) {

        /**
         * 验证 ReactiveLoadBalancerClientFilter          http://localhost:8080/lb/index
         * 验证 DiscoveryClientRouteDefinitionLocator     http://localhost:8080/s1/index
         * 验证 SpringCloudCircuitBreakerFilterFactory    http://localhost:8080/cb/index
         * */
        List<String> includes = new ArrayList<>();
        includes.add("discovery-client");
        System.setProperty("spring.profiles.include",
                String.join(",", includes)
        );
        ConfigurableApplicationContext context = SpringApplication.run(Main.class, args);
    }
    /**
     * 设置 spring.cloud.gateway.enabled=false 禁用 gateway 的功能
     * */

    /**
     * NettyRoutingFilter
     * */
    /**
     *
     *      {@link NettyRoutingFilter#filter(ServerWebExchange, GatewayFilterChain)} 这是最后一个执行的 GlobalFilter 其作用是发送发送请求的逻辑。执行 http、https 请求
     *      {@link WebsocketRoutingFilter#filter(ServerWebExchange, GatewayFilterChain)} 这是执行 ws 请求
     *             这两个都依赖 List<HttpHeadersFilter> 用来设置 头
     *      通过这两个类 实现了GateWay的功能 RoutePredicateHandlerMapping -> FilteringWebHandler
     *      是由 SimpleHandlerAdapter 来适配 RoutePredicateHandlerMapping
     *      RoutePredicateHandlerMapping 依赖 RouteLocator 得到 Route。
     *          {@link PropertiesRouteDefinitionLocator#getRouteDefinitions()} 得到通过配置文件定义的 route
     *          {@link RouteDefinitionRouteLocator#getRoutes()}
     *              1. 通过 RoutePredicateFactory 创建出 AsyncPredicate。创建的时候会依赖 ConfigurationService 进行 类型转换、属性校验、发布事件
     *              2. 通过 GatewayFilterFactory 创建出 GatewayFilter。创建的时候会依赖 ConfigurationService 进行 类型转换、属性校验、发布事件
     *              3. 构造出 Route
     *                  Route.async(routeDefinition).asyncPredicate(predicate).replaceFilters(gatewayFilters).build();
     *
     *
     *
     * GateWay 是基于 Spring-WebFlux 实现的，通过扩展 RoutePredicateHandlerMapping 将请求委托给 我们配置的 路由规则
     *
     * 配置的 route 规则会映射成 Route 实例：
     *      1. 通过 RoutePredicateFactory 创建出 AsyncPredicate。创建的时候会依赖 ConfigurationService 进行 类型转换、属性校验、发布事件
     *      2. 通过 GatewayFilterFactory 创建出 GatewayFilter。创建的时候会依赖 ConfigurationService 进行 类型转换、属性校验、发布事件
     *      3. 依赖上面的结果构造出 Route
     *          Route.async(routeDefinition).asyncPredicate(predicate).replaceFilters(gatewayFilters).build();
     *
     * 客户端发送的请求 -> HttpHandler -> RoutePredicateHandlerMapping -> FilteringWebHandler -> Route
     *
     *  应用 Route 的 predicate 满足，之后才使用这个
     *  通过 route.getPredicate().apply(serverWebExchange) 过滤出一个 route 然后
     *  将流程委托给 route.getFilters() 执行
     *  最终的请求发送其实是由 NettyWriteResponseFilter 这个 GlobalFilter 完成的
     *
     *
     * RouteDefinitionLocator（PropertiesRouteDefinitionLocator） -> RouteDefinitionRouteLocator -> ConfigurationService、RoutePredicateFactory、GatewayFilterFactory
     *
     *
     * 重点将一下 WeightRoutePredicateFactory、WeightCalculatorWebFilter 的实现逻辑
     * PathRoutePredicateFactory 会提取占位符，设置到 exchange 中
     * 整理类图：AbstractRoutePredicateFactory
     *
     * */
    /**
     * {@link RouteDefinitionRouteLocator#getRoutes()}
     * {@link RouteDefinitionRouteLocator#convertToRoute(RouteDefinition)}
     * */

}
