# 资料

[Spring Cloud 官网文档](https://docs.spring.io/spring-cloud/docs/2021.0.5/reference/html/)

[Spring Cloud Gateway 官网文档](https://docs.spring.io/spring-cloud-gateway/docs/3.1.5/reference/html/)

[示例代码](../spring-cloud-gateway-source-note)

# Spring Cloud Gateway 介绍



| ID              | HTTP Method | Description                                                  |
| :-------------- | :---------- | :----------------------------------------------------------- |
| `globalfilters` | GET         | Displays the list of global filters applied to the routes. 显示应用于路由的全局过滤器列表。 |
| `routefilters`  | GET         | Displays the list of `GatewayFilter` factories applied to a particular route. 显示应用于特定路由的 `GatewayFilter` 工厂列表。 |
| `refresh`       | POST        | Clears the routes cache. 清除路由缓存。                      |
| `routes`        | GET         | Displays the list of routes defined in the gateway. 显示网关中定义的路由列表。 |
| `routes/{id}`   | GET         | Displays information about a particular route. 显示有关特定路线的信息。 |
| `routes/{id}`   | POST        | Adds a new route to the gateway. 添加到网关的新路由。        |
| `routes/{id}`   | DELETE      | Removes an existing route from the gateway. 从网关中删除现有路由。 |