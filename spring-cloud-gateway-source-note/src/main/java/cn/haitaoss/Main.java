package cn.haitaoss;

import cn.haitaoss.config.MyRoutePredicateFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-18 09:36
 *
 */
@SpringBootApplication
public class Main {
    /**
     * 如果包含启动器，但不希望启用网关，请设置 spring.cloud.gateway.enabled=false 。
     * 这里没看懂是啥意思 https://docs.spring.io/spring-cloud-gateway/docs/3.1.5/reference/html/#modifying-the-way-remote-addresses-are-resolved
     *
     *
     * 权重路由谓词工厂 咋实现的？？？
     *      https://docs.spring.io/spring-cloud-gateway/docs/3.1.5/reference/html/#the-weight-route-predicate-factory
     *
     * */
    /**
     * Route Predicate Factories（路由谓词工厂）
     *      Spring Cloud Gateway 将路由作为 Spring WebFlux HandlerMapping 基础设施的一部分进行匹配。 Spring Cloud Gateway 包含许多内置的路由谓词工厂。所有这些谓词都匹配 HTTP 请求的不同属性。您可以将多个路由谓词工厂与逻辑 and 语句结合起来。
     *
     *      The After Route Predicate Factory
     *          After 路由谓词工厂采用一个参数， datetime （这是一个 java ZonedDateTime ）。此谓词匹配指定日期时间之后发生的请求。下面的例子配置了一个路由谓词：
     *
     *      The Before Route Predicate Factory
     *      The Between Route Predicate Factory
     *      The Cookie Route Predicate Factory
     *      The Header Route Predicate Factory
     *      The Host Route Predicate Factory
     *      The Method Route Predicate Factory
     *      The Path Route Predicate Factory
     *      The Query Route Predicate Factory
     *      The RemoteAddr Route Predicate Factory
     *          Modifying the Way Remote Addresses Are Resolved
     *          您可以通过设置自定义 RemoteAddressResolver 来自定义解析远程地址的方式。 Spring Cloud Gateway 附带一个基于 X-Forwarded-For 标头 XForwardedRemoteAddressResolver 的非默认远程地址解析器。
     *          RemoteAddressResolver resolver = XForwardedRemoteAddressResolver
     *          .maxTrustedIndex(1);
     *
     *     The Weight Route Predicate Factory（重点看看源码，这是咋实现的）
     *          Weight 路由谓词工厂有两个参数： group 和 weight （一个整数）。权重按组计算。以下示例配置权重路由谓词：
     *
     *     The XForwarded Remote Addr Route Predicate Factory
     *
     *
     *      - Path=/red/{segment},/blue/{segment} 会将占位符的值存到 request 域中
     *      Map<String, String> uriVariables = ServerWebExchangeUtils.getUriTemplateVariables(exchange);
     *      String segment = uriVariables.get("segment");
     * */
    /**
     * GatewayFilter Factories 网关过滤器工厂
     *      网关过滤器允许以某种方式修改传入的 HTTP 请求或传出的 HTTP 响应。路由过滤器的范围是特定路由。 Spring Cloud Gateway 包括许多内置的 GatewayFilter 工厂。
     *
     *      The AddRequestHeader GatewayFilter Factory
     *      The AddRequestParameter GatewayFilter Factory
     *      The AddResponseHeader GatewayFilter Factory
     *      The DedupeResponseHeader GatewayFilter Factory
     *
     *      Spring Cloud CircuitBreaker GatewayFilter Factory
     *          要启用 Spring Cloud CircuitBreaker 过滤器，您需要将 spring-cloud-starter-circuitbreaker-reactor-resilience4j 放在类路径中。以下示例配置了一个 Spring Cloud CircuitBreaker GatewayFilter
     *
     *          Spring Cloud CircuitBreaker 过滤器还可以接受可选的 fallbackUri 参数。目前，仅支持 forward: 计划的 URI。如果调用回退，请求将转发到与 URI 匹配的控制器。以下示例配置了这样的回退
     *
     *          信息。
     *
     *      Tripping The Circuit Breaker On Status Codes (根据状态代码使断路器跳闸)
     *
     *      The FallbackHeaders GatewayFilter Factory
     *      The MapRequestHeader GatewayFilter Factory
     *      The PrefixPath GatewayFilter Factory
     *      The PreserveHostHeader GatewayFilter Factory
     *      The RequestRateLimiter GatewayFilter Factory（功能复杂）
     *      The RedirectTo GatewayFilter Factory
     *      The RemoveRequestHeader GatewayFilter Factory
     *      RemoveResponseHeader GatewayFilter Factory
     *      The RemoveRequestParameter GatewayFilter Factory
     *      RequestHeaderSize GatewayFilter Factory
     *      The RewritePath GatewayFilter Factory
     *      RewriteLocationResponseHeader GatewayFilter Factory
     *      The RewriteResponseHeader GatewayFilter Factory
     *      The SaveSession GatewayFilter Factory
     *      The SecureHeaders GatewayFilter Factory（默认功能，就是会固定增加很多响应头）
     *          spring.cloud.gateway.filter.secure-headers.disable=x-frame-options,strict-transport-security
     *      The SetPath GatewayFilter Factory
     *      The SetRequestHeader GatewayFilter Factory（官方的文档描述是不是错了）
     *          此 GatewayFilter 替换（而不是添加）具有给定名称的所有标头。因此，如果下游服务器以 X-Request-Red:1234 响应，这将被替换为 X-Request-Red:Blue ，这是下游服务将收到的内容。
     *
     *      The SetResponseHeader GatewayFilter Factory
     *      The SetStatus GatewayFilter Factory
     *      The StripPrefix GatewayFilter Factory
     *      The Retry GatewayFilter Factory（功能复杂）
     *      The RequestSize GatewayFilter Factory
     *      The SetRequestHostHeader GatewayFilter Factory
     *      Modify a Request Body GatewayFilter Factory
     *          只能使用 Java DSL 配置此过滤器。
     *      Modify a Response Body GatewayFilter Factory
     *          只能使用 Java DSL 配置此过滤器。
     *      Token Relay GatewayFilter Factory
     *          令牌中继是 OAuth2 消费者充当客户端并将传入令牌转发到传出资源请求的地方。（其实就是经过身份验证的用户中提取访问令牌，并将其放入下游请求的请求标头中。）
     *
     *      The CacheRequestBody GatewayFilter Factory
     *          有些情况需要读取body。由于请求body流只能读取一次，所以我们需要缓存请求body。您可以使用 CacheRequestBody 过滤器在请求正文发送到下游并从 exchagne 属性获取正文之前缓存请求正文。
     *          CacheRequestBody 将提取请求主体并将其转换为主体类（例如 java.lang.String ，在前面的示例中定义）。然后将它放在 ServerWebExchange.getAttributes() 中，并使用 ServerWebExchangeUtils.CACHED_REQUEST_BODY_ATTR 中定义的键。
     *          此过滤器仅适用于 http 请求（包括 https）。
     *
     *      The JsonToGrpc GatewayFilter Factory
     *          将 JSON 负载转换为 gRPC 请求
     *
     *      Default Filters
     *          要添加过滤器并将其应用于所有路由，您可以使用 spring.cloud.gateway.default-filters 。此属性采用过滤器列表。以下清单定义了一组默认过滤器：
     *
     * */
    /**
     * Global Filters
     *  GlobalFilter 接口与 GatewayFilter 具有相同的签名(方法)。这些是有条件地应用于所有路由的特殊过滤器。
     *
     *  Combined Global Filter and GatewayFilter Ordering（结合全局过滤器和 GatewayFilter 排序）
     *      当请求与路由匹配时，过滤 Web 处理程序将 GlobalFilter 的所有实例和 GatewayFilter 的所有特定于路由的实例添加到过滤器链中。这个组合过滤器链是按照 org.springframework.core.Ordered 接口排序的，你可以通过实现 getOrder() 方法来设置。
     *      由于 Spring Cloud Gateway 区分过滤器逻辑执行的“前”和“后”阶段（请参阅工作原理），具有最高优先级的过滤器是“前”阶段中的第一个和“后”阶段中的最后一个 -阶段。
     *
     *  Forward Routing Filter(正向路由过滤器)
     *      ForwardRoutingFilter 在交换属性 ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR 中查找 URI。如果 URL 具有 forward 方案（例如 forward:///localendpoint ），它会使用 Spring DispatcherHandler 来处理请求。请求 URL 的路径部分被转发 URL 中的路径覆盖。未修改的原始 URL 附加到 ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR 属性中的列表。
     *
     *  The ReactiveLoadBalancerClientFilter
     *      ReactiveLoadBalancerClientFilter 在名为 ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR 的交换属性中查找 URI。如果 URL 具有 lb 方案（例如 lb://myservice ），它使用 Spring Cloud ReactorLoadBalancer 将名称（本例中的 myservice ）解析为实际主机和端口，并替换相同的 URI属性。未修改的原始 URL 附加到 ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR 属性中的列表。过滤器还会查看 ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR 属性以查看它是否等于 lb 。如果是这样，则适用相同的规则。以下清单配置了一个 ReactiveLoadBalancerClientFilter ：
     *      spring:
     *       cloud:
     *         gateway:
     *           routes:
     *           - id: myRoute
     *             uri: lb://service
     *             predicates:
     *             - Path=/service/**
     *      默认情况下，当 ReactorLoadBalancer 找不到服务实例时，将返回 503 。您可以通过设置 spring.cloud.gateway.loadbalancer.use404=true 将网关配置为返回 404
     *
     *  The Netty Routing Filter(Netty 路由锅炉器)
     *      如果位于 ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR 交换属性中的 URL 具有 http 或 https 方案，Netty 路由过滤器就会运行。它使用 Netty HttpClient 进行下游代理请求。响应放在 ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR 交换属性中，供以后的过滤器使用。 （还有一个实验性的 WebClientHttpRoutingFilter ，它执行相同的功能但不需要 Netty。）
     *
     *  The Netty Write Response Filter（Netty 写响应过滤器）
     *      如果 ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR 交换属性中有 Netty HttpClientResponse ，则 NettyWriteResponseFilter 运行。它在所有其他过滤器完成后运行，并将代理响应写回网关客户端响应。 （还有一个实验性的 WebClientWriteResponseFilter ，它执行相同的功能但不需要 Netty。）
     *
     *  The RouteToRequestUrl Filter
     *      如果 ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR 交换属性中有 Route 对象，则 RouteToRequestUrlFilter 运行。它基于请求 URI 创建一个新的 URI，但使用 Route 对象的 URI 属性进行了更新。新的 URI 放在 ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR 交换属性中。
     *      如果 URI 有方案前缀，例如 lb:ws://serviceid ， lb 方案将从 URI 中剥离并放置在 ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR 中，以便稍后在过滤器链中使用。
     *
     *  The Websocket Routing Filter
     *      如果位于 ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR 交换属性中的 URL 具有 ws 或 wss 方案，则 websocket 路由过滤器运行。它使用 Spring WebSocket 基础结构将 websocket 请求转发到下游。
     *      您可以通过在 URI 前加上 lb 来对 websocket 进行负载平衡，例如 lb:ws://serviceid 。
     *
     *  The Gateway Metrics Filter（网关指标过滤器）
     *      要启用网关指标，请将 spring-boot-starter-actuator 添加为项目依赖项。然后，默认情况下，只要属性 spring.cloud.gateway.metrics.enabled 未设置为 false ，网关指标筛选器就会运行。此过滤器添加一个名为 spring.cloud.gateway.requests 的计时器指标，其中包含以下标签：
     *
     *  Marking An Exchange As Routed（将交换标记为已路由）
     *      在网关路由了一个 ServerWebExchange 之后，它通过将 gatewayAlreadyRouted 添加到交换属性来将该交换标记为“已路由”。一旦请求被标记为已路由，其他路由过滤器将不会再次路由请求，实质上会跳过过滤器。
     *      您可以使用一些方便的方法将交换标记为路由或检查交换是否已路由。
     *          ServerWebExchangeUtils.isAlreadyRouted 接受一个 ServerWebExchange 对象并检查它是否已被“路由”。
     *          ServerWebExchangeUtils.setAlreadyRouted 接受一个 ServerWebExchange 对象并将其标记为“已路由”。
     *
     *
     * */
    /**
     * HttpHeadersFilters （HttpHeader过滤器）
     *      HttpHeadersFilters 在将请求发送到下游之前应用于请求，例如在 NettyRoutingFilter 中。
     *
     *      Forwarded Headers Filter（转发标头过滤器）
     *          Forwarded 标头过滤器创建一个 Forwarded 标头以发送到下游服务。它将当前请求的 Host 标头、方案和端口添加到任何现有的 Forwarded 标头。
     *
     *       RemoveHopByHop Headers Filter（RemoveHopByHop 标头过滤器）
     *          RemoveHopByHop Headers Filter 从转发的请求中删除标头。删除的默认标头列表来自 IETF 。
     *              默认会删除的头 Connection、Keep-Alive、Proxy-Authenticate、Proxy-Authorization、TE、Trailer、Transfer-Encoding、Upgrade
     *              要更改此设置，请将 spring.cloud.gateway.filter.remove-hop-by-hop.headers 属性设置为要删除的标头名称列表
     *
     *       XForwarded Headers Filter
     *          XForwarded 标头过滤器创建各种 X-Forwarded-* 标头以发送到下游服务。它使用当前请求的 Host 标头、方案、端口和路径来创建各种标头。
     *          可以通过以下布尔属性（默认为 true）控制创建单个标头：
     *
     *              spring.cloud.gateway.x-forwarded.for-enabled
     *              spring.cloud.gateway.x-forwarded.host-enabled
     *              spring.cloud.gateway.x-forwarded.port-enabled
     *              spring.cloud.gateway.x-forwarded.proto-enabled
     *              spring.cloud.gateway.x-forwarded.prefix-enabled
     *          附加多个标头可以由以下布尔属性控制（默认为 true）：
     *              spring.cloud.gateway.x-forwarded.for-append
     *              spring.cloud.gateway.x-forwarded.host-append
     *              spring.cloud.gateway.x-forwarded.port-append
     *              spring.cloud.gateway.x-forwarded.proto-append
     *              spring.cloud.gateway.x-forwarded.prefix-append
     * */
    /**
     * TLS and SSL
     *      网关可以通过遵循通常的 Spring 服务器配置来侦听 HTTPS 上的请求。以下示例显示了如何执行此操作：
     *          server:
     *           ssl:
     *             enabled: true
     *             key-alias: scg
     *             key-store-password: scg1234
     *             key-store: classpath:scg-keystore.p12
     *             key-store-type: PKCS12
     *      您可以将网关路由路由到 HTTP 和 HTTPS 后端。如果要路由到 HTTPS 后端，则可以使用以下配置将网关配置为信任所有下游证书：
     *          spring:
     *              cloud:
     *                gateway:
     *                  httpclient:
     *                    ssl:
     *                      useInsecureTrustManager: true
     *      使用不安全的信任管理器不适合生产。对于生产部署，您可以使用一组已知证书配置网关，这些证书可以通过以下配置信任：
     *          spring:
     *              cloud:
     *                gateway:
     *                  httpclient:
     *                    ssl:
     *                      trustedX509Certificates:
     *                      - cert1.pem
     *                      - cert2.pem
     *      如果未为 Spring Cloud Gateway 提供受信任的证书，则使用默认的信任库（您可以通过设置 javax.net.ssl.trustStore 系统属性来覆盖它）。
     *
     *  TLS Handshake (TLS 握手)
     *      网关维护一个用于路由到后端的客户端池。通过 HTTPS 通信时，客户端会发起 TLS 握手。许多超时与此握手相关联。您可以配置这些超时可以配置（默认显示）如下：
     *          spring:
     *              cloud:
     *                gateway:
     *                  httpclient:
     *                    ssl:
     *                      handshake-timeout-millis: 10000
     *                      close-notify-flush-timeout-millis: 3000
     *                      close-notify-read-timeout-millis: 0
     * */
    /**
     *  Configuration
     *      Spring Cloud Gateway 的配置由一组 RouteDefinitionLocator 实例驱动。以下清单显示了 RouteDefinitionLocator 接口的定义：
     *      默认情况下， PropertiesRouteDefinitionLocator 使用 Spring Boot 的 @ConfigurationProperties 机制加载属性。
     *      对于网关的某些用途，属性就足够了，但某些生产用例受益于从外部源（例如数据库）加载配置。未来的里程碑版本将具有基于 Spring 数据存储库的 RouteDefinitionLocator 实现，例如 Redis、MongoDB 和 Cassandra。
     *
     *  RouteDefinition Metrics 10.1.路由定义指标
     *      要启用 RouteDefinition 指标，请将 spring-boot-starter-actuator 添加为项目依赖项。然后，默认情况下，只要将属性 spring.cloud.gateway.metrics.enabled 设置为 true ，指标就可用。将添加名为 spring.cloud.gateway.routes.count 的仪表指标，其值为 RouteDefinitions 的数量。该指标将从 /actuator/metrics/spring.cloud.gateway.routes.count 获得。
     *
     *  Route Metadata Configuration(路由元数据配置)
     *      您可以使用元数据为每个路由配置额外的参数，如下所示：
     *          spring:
     *              cloud:
     *                gateway:
     *                  routes:
     *                  - id: route_with_metadata
     *                    uri: https://example.org
     *                    metadata:
     *                      optionName: "OptionValue"
     *                      compositeObject:
     *                        name: "value"
     *                      iAmNumber: 1
     *      然后通过这种方式获取元数据
     *          Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
     *          // get all metadata properties
     *          route.getMetadata();
     *          // get a single metadata property
     *          route.getMetadata(someKey);
     *
     *   Http timeouts configuration
     *      可以为所有路由配置 Http 超时（响应和连接），并为每个特定路由覆盖。
     *
     *      Global timeouts （全局超时）
     *          spring:
     *              cloud:
     *                gateway:
     *                  httpclient:
     *                    connect-timeout: 1000
     *                    response-timeout: 5s
     *
     *     Per-route timeouts （每路由超时）  配置每个路由超时
     *                - id: per_route_timeouts
     *                  uri: https://example.org
     *                  predicates:
     *                    - name: Path
     *                      args:
     *                        pattern: /delay/{timeout}
     *                  metadata:
     *                    response-timeout: 200
     *                    connect-timeout: 200
     *              注：也使用 Java DSL 的每个路由超时配置（官方有代码）
     *
     *      Fluent Java Routes API（流畅的 Java 路由 API）
     *          为了允许在 Java 中进行简单配置， RouteLocatorBuilder bean 包含一个流畅的 API。下面的清单显示了它是如何工作的：
     *
     *      The DiscoveryClient Route Definition Locator（DiscoveryClient 路由定义定位器）
     *          您可以将网关配置为基于使用 DiscoveryClient 兼容服务注册表注册的服务创建路由。
     *          要启用此功能，请设置 spring.cloud.gateway.discovery.locator.enabled=true 并确保 DiscoveryClient 实现（例如 Netflix Eureka、Consul 或 Zookeeper）在类路径上并已启用
     *
     *      Configuring Predicates and Filters For DiscoveryClient Routes（为 DiscoveryClient 路由配置谓词和过滤器）
     *          默认情况下，网关为使用 DiscoveryClient 创建的路由定义单个谓词和过滤器。
     *          默认谓词是使用模式 /serviceId/** 定义的路径谓词，其中 serviceId 是来自 DiscoveryClient 的服务 ID。
     *          默认过滤器是一个重写路径过滤器，带有正则表达式 /serviceId/?(?<remaining>.*) 和替换 /${remaining} 。这会在将请求发送到下游之前从路径中去除服务 ID。
     *          如果要自定义 DiscoveryClient 路由使用的谓词或过滤器，请设置 spring.cloud.gateway.discovery.locator.predicates[x] 和 spring.cloud.gateway.discovery.locator.filters[y] 。这样做时，如果您想保留该功能，则需要确保包括前面显示的默认谓词和过滤器。以下示例显示了它的样子：
     *          spring.cloud.gateway.discovery.locator.predicates[0].name: Path
     *          spring.cloud.gateway.discovery.locator.predicates[0].args[pattern]: "'/'+serviceId+'/**'"
     *          spring.cloud.gateway.discovery.locator.predicates[1].name: Host
     *          spring.cloud.gateway.discovery.locator.predicates[1].args[pattern]: "'**.foo.com'"
     *          spring.cloud.gateway.discovery.locator.filters[0].name: CircuitBreaker
     *          spring.cloud.gateway.discovery.locator.filters[0].args[name]: serviceId
     *          spring.cloud.gateway.discovery.locator.filters[1].name: RewritePath
     *          spring.cloud.gateway.discovery.locator.filters[1].args[regexp]: "'/' + serviceId + '/?(?<remaining>.*)'"
     *          spring.cloud.gateway.discovery.locator.filters[1].args[replacement]: "'/${remaining}'"
     * */
    /**
     * Reactor Netty Access Logs（Reactor Netty 访问日志）
     *  要启用 Reactor Netty 访问日志，请设置 -Dreactor.netty.http.server.accessLogEnabled=true 。
     *  注：它必须是 Java 系统属性，而不是 Spring Boot 属性。
     *
     *  您可以将日志系统配置为具有单独的访问日志文件。以下示例创建一个 Logback 配置：
     *
     * */
    /**
     * CORS Configuration （CORS配置）
     *      您可以配置网关以控制 CORS 行为。 “全局”CORS 配置是 URL 模式到 Spring Framework CorsConfiguration 的映射。以下示例配置 CORS：
     *      spring:
     *          cloud:
     *            gateway:
     *              globalcors:
     *                cors-configurations:
     *                  '[/**]':
     *                    allowedOrigins: "https://docs.spring.io"
     *                    allowedMethods:
     *                    - GET
     *      在前面的示例中，对于所有 GET 请求的路径，允许来自来自 docs.spring.io 的请求的 CORS 请求。
     *      要为某些网关路由谓词未处理的请求提供相同的 CORS 配置，请将 spring.cloud.gateway.globalcors.add-to-simple-url-handler-mapping 属性设置为 true 。当您尝试支持 CORS 预检请求并且您的路由谓词未评估为 true 因为 HTTP 方法是 options 时，这很有用。
     * */
    /**
     * Actuator API （执行器API）
     *      /gateway 执行器端点允许您监视 Spring Cloud Gateway 应用程序并与之交互。要进行远程访问，必须在应用程序属性中通过 HTTP 或 JMX 启用和公开端点。以下清单显示了如何执行此操作：
     *      management.endpoint.gateway.enabled=true # default value
     *      management.endpoints.web.exposure.include=gateway
     *
     *
     *      Verbose Actuator Format（详细执行器格式）
     *          Spring Cloud Gateway 添加了一种新的、更详细的格式。它为每个路由添加了更多详细信息，让您可以查看与每个路由关联的谓词和过滤器以及任何可用的配置。以下示例配置 /actuator/gateway/routes
     *          默认情况下启用此功能。要禁用它，请设置以下属性：spring.cloud.gateway.actuator.verbose.enabled=false
     *
     *      Retrieving Route Filters（检索路由过滤器）
     *          Global Filters （全局过滤器）
     *              要检索应用于所有路由的全局过滤器，请向 /actuator/gateway/globalfilters 发出 GET 请求。生成的响应类似于以下内容：、
     *              响应包含现有全局过滤器的详细信息。对于每个全局过滤器，都有过滤器对象的字符串表示（例如 org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter@77856cc5 ）和过滤器链中的相应顺序。}
     *          Route Filters （路由过滤器）
     *              要检索应用于路由的 GatewayFilter 工厂，请向 /actuator/gateway/routefilters 发出 GET 请求。生成的响应类似于以下内容：
     *              响应包含应用于任何特定路由的 GatewayFilter 工厂的详细信息。对于每个工厂，都有相应对象的字符串表示形式（例如， [SecureHeadersGatewayFilterFactory@fceab5d configClass = Object] ）。请注意， null 值是由于端点控制器的不完整实现造成的，因为它试图设置过滤器链中对象的顺序，这不适用于 GatewayFilter 工厂对象。
     *
     *      Refreshing the Route Cache（刷新路由缓存）
     *          要清除路由缓存，请向 /actuator/gateway/refresh 发出 POST 请求。该请求返回 200，但没有响应正文。
     *
     *      Retrieving the Routes Defined in the Gateway（检索网关中定义的路由）
     *          要检索网关中定义的路由，请向 /actuator/gateway/routes 发出 GET 请求。生成的响应类似于以下内容：
     *
     *
     *      Retrieving Information about a Particular Route（检索有关特定路线的信息）
     *          要检索有关单个路由的信息，请向 /actuator/gateway/routes/{id} 发出 GET 请求（例如， /actuator/gateway/routes/first_route ）。生成的响应类似于以下内容：
     *
     *
     *       Creating and Deleting a Particular Route（创建和删除特定路线）
     *          要创建路由，请使用指定路由字段的 JSON 正文向 /gateway/routes/{id_route_to_create} 发出 POST 请求（请参阅检索有关特定路由的信息）。
     *          要删除路由，请向 /gateway/routes/{id_route_to_delete} 发出 DELETE 请求。
     *
     *      Recap: The List of All endpoints（回顾：所有端点的列表）
     *          有一个表格
     *
     *      Sharing Routes between multiple Gateway instances（在多个网关实例之间共享路由）
     *          Spring Cloud Gateway 提供了两个 RouteDefinitionRepository 实现。第一个是 InMemoryRouteDefinitionRepository ，它只存在于一个网关实例的内存中。这种类型的存储库不适合跨多个网关实例填充路由。
     *          为了跨 Spring Cloud Gateway 实例集群共享路由，可以使用 RedisRouteDefinitionRepository 。要启用这种存储库，必须将以下属性设置为 true： spring.cloud.gateway.redis-route-definition-repository.enabled 与 RedisRateLimiter Filter Factory 类似，它需要使用 spring-boot-starter-data-redis-reactive Spring Boot starter。
     *
     *
     * */
    /**
     * Troubleshooting （故障排除）
     *  Log Levels （日志级别）
     *      以下记录器可能包含 DEBUG 和 TRACE 级别的有价值的故障排除信息：
     *          org.springframework.cloud.gateway
     *          org.springframework.http.server.reactive
     *          org.springframework.web.reactive
     *          org.springframework.boot.autoconfigure.web
     *          reactor.netty
     *          redisratelimiter
     *
     *  Wiretap
     *      Reactor Netty HttpClient 和 HttpServer 可以启用窃听。当与将 reactor.netty 日志级别设置为 DEBUG 或 TRACE 结合使用时，它可以记录信息，例如通过线路发送和接收的标头和正文。要启用窃听，请分别为 HttpServer 和 HttpClient 设置 spring.cloud.gateway.httpserver.wiretap=true 或 spring.cloud.gateway.httpclient.wiretap=true 。
     *
     *
     * */
    /**
     * Developer Guide （开发者指南）
     *  这些是编写网关的一些自定义组件的基本指南。
     *
     *  Writing Custom Route Predicate Factories（编写自定义路由谓词工厂）
     *      为了编写路由谓词，您需要将 RoutePredicateFactory 实现为一个 bean。有一个名为 AbstractRoutePredicateFactory 的抽象类，您可以对其进行扩展。
     *      {@link MyRoutePredicateFactory}
     *
     *  Writing Custom GatewayFilter Factories(编写自定义 GatewayFilter 工厂)
     *      要编写 GatewayFilter ，您必须将 GatewayFilterFactory 实现为一个 bean。您可以扩展名为 AbstractGatewayFilterFactory 的抽象类。以下示例显示了如何执行此操作：
     *
     * Naming Custom Filters And References In Configuration(在配置中命名自定义过滤器和引用)
     *      自定义过滤器类名称应以 GatewayFilterFactory 结尾。
     *      例如，要在配置文件中引用名为 Something 的过滤器，该过滤器必须位于名为 SomethingGatewayFilterFactory 的类中
     *      可以创建一个没有 GatewayFilterFactory 后缀命名的网关过滤器，例如 class AnotherThing 。这个过滤器可以在配置文件中被引用为 AnotherThing 。这不是受支持的命名约定，在未来的版本中可能会删除此语法。请更新过滤器名称以符合要求。
     *
     * Writing Custom Global Filters(编写自定义全局过滤器)
     *      要编写自定义全局过滤器，您必须将 GlobalFilter 接口实现为 bean。这会将过滤器应用于所有请求。
     * */
    /**
     * Building a Simple Gateway by Using Spring MVC or Webflux(使用 Spring MVC 或 Webflux 构建一个简单的网关)
     *  Spring Cloud Gateway 提供了一个名为 ProxyExchange 的实用程序对象。您可以在常规 Spring Web 处理程序中将其用作方法参数。它通过镜像 HTTP 谓词的方法支持基本的下游 HTTP 交换。使用 MVC，它还支持通过 forward() 方法转发到本地处理程序。要使用 ProxyExchange ，请在类路径中包含正确的模块（ spring-cloud-gateway-mvc 或 spring-cloud-gateway-webflux ）。
     *  以下 MVC 示例将对 /test 下游的请求代理到远程服务器：
     *      @RestController
     * @SpringBootApplication
     * public class GatewaySampleApplication {
     *
     *     @Value("${remote.home}")
     *     private URI home;
     *
     *     @GetMapping("/test")
     *     public ResponseEntity<?> proxy(ProxyExchange<byte[]> proxy) throws Exception {
     *         return proxy.uri(home.toString() + "/image/png").get();
     *     }
     *
     * }
     *  ProxyExchange 上的便捷方法使处理程序方法能够发现和增强传入请求的 URI 路径。例如，您可能想要提取路径的尾随元素以将它们传递到下游：
     *      @GetMapping("/proxy/path/**")
     * public ResponseEntity<?> proxyPath(ProxyExchange<byte[]> proxy) throws Exception {
     *   String path = proxy.path("/proxy/path/");
     *   return proxy.uri(home.toString() + "/foos/" + path).get();
     * }
     * */
    /**
     * Configuration properties 19. 配置属性
     * 要查看所有 Spring Cloud Gateway 相关配置属性的列表，请参阅附录。
     *  https://docs.spring.io/spring-cloud-gateway/docs/3.1.5/reference/html/appendix.html
     * */
    /**
     * 您应该为您可能想要这样做的任何路由配置此过滤器 spring.cloud.gateway.default-filters
     * */

    @Bean
    public GlobalFilter customFilter() {
        return new CustomGlobalFilter();
    }

    @Slf4j
    public static class CustomGlobalFilter implements GlobalFilter, Ordered {

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            log.info("custom global filter");
            return chain.filter(exchange);
        }

        @Override
        public int getOrder() {
            return -1;
        }
    }
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
