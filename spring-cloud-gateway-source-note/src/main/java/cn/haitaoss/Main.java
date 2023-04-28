package cn.haitaoss;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.cloud.gateway.config.PropertiesRouteDefinitionLocator;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.cloud.gateway.filter.WebsocketRoutingFilter;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRouteLocator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
         * 设置 spring.cloud.gateway.enabled=false 禁用 gateway 的功能
         *
         *
         * 验证 ReactiveLoadBalancerClientFilter          http://localhost:8080/lb/index
         * 验证 DiscoveryClientRouteDefinitionLocator     http://localhost:8080/s1/index
         * 验证 SpringCloudCircuitBreakerFilterFactory    http://localhost:8080/cb/index
         * 验证 WeightRoutePredicateFactory               http://localhost:8080/weight/index
         * */
        System.setProperty("spring.profiles.include", "discovery-client");
        ConfigurableApplicationContext context = SpringApplication.run(Main.class, args);
    }
}
