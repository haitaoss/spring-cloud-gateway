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

package org.springframework.cloud.gateway.handler.predicate;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import io.netty.handler.ipfilter.IpFilterRuleType;
import io.netty.handler.ipfilter.IpSubnetFilterRule;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ServerWebExchange;

import static org.springframework.cloud.gateway.support.ShortcutConfigurable.ShortcutType.GATHER_LIST;

/**
 * @author Spencer Gibb
 */
public class RemoteAddrRoutePredicateFactory
		extends AbstractRoutePredicateFactory<RemoteAddrRoutePredicateFactory.Config> {

	private static final Log log = LogFactory.getLog(RemoteAddrRoutePredicateFactory.class);

	public RemoteAddrRoutePredicateFactory() {
		super(Config.class);
	}

	@Override
	public ShortcutType shortcutType() {
		return GATHER_LIST;
	}

	@Override
	public List<String> shortcutFieldOrder() {
		return Collections.singletonList("sources");
	}

	@NotNull
	private List<IpSubnetFilterRule> convert(List<String> values) {
		List<IpSubnetFilterRule> sources = new ArrayList<>();
		for (String arg : values) {
			addSource(sources, arg);
		}
		return sources;
	}

	@Override
	public Predicate<ServerWebExchange> apply(Config config) {
		// 将我们配置的 IP 信息转成 sources
		List<IpSubnetFilterRule> sources = convert(config.sources);

		return new GatewayPredicate() {
			@Override
			public boolean test(ServerWebExchange exchange) {
				/**
				 * 使用 remoteAddressResolver 解析 exchange 得到 remoteAddress。
				 * 默认是直接从 request 中获取 remoteAddress 的值。这种方式不太灵活
				 * 比如多个服务间调用，我们要想匹配其中某一个节点的 ip，那该怎么办？？？
				 *
				 * 所以有一个 XForwardedRemoteAddrRoutePredicateFactory，它其实是对 RemoteAddrRoutePredicateFactory 的增强，
				 * 主要是修改 config.remoteAddressResolver 成 XForwardedRemoteAddressResolver。
				 * XForwardedRemoteAddressResolver 它是从请求头 X-Forwarded-For 中获取 remoteAddress 的值
				 * 		{@link XForwardedRemoteAddressResolver#resolve(ServerWebExchange)}
				 * */
				InetSocketAddress remoteAddress = config.remoteAddressResolver.resolve(exchange);
				if (remoteAddress != null && remoteAddress.getAddress() != null) {
					String hostAddress = remoteAddress.getAddress().getHostAddress();
					String host = exchange.getRequest().getURI().getHost();

					if (log.isDebugEnabled() && !hostAddress.equals(host)) {
						log.debug("Remote addresses didn't match " + hostAddress + " != " + host);
					}

					for (IpSubnetFilterRule source : sources) {
						// remoteAddress 与定义的 source 匹配 就返回 true
						if (source.matches(remoteAddress)) {
							return true;
						}
					}
				}

				return false;
			}

			@Override
			public Object getConfig() {
				return config;
			}

			@Override
			public String toString() {
				return String.format("RemoteAddrs: %s", config.getSources());
			}
		};
	}

	private void addSource(List<IpSubnetFilterRule> sources, String source) {
		if (!source.contains("/")) { // no netmask, add default
			source = source + "/32";
		}

		String[] ipAddressCidrPrefix = source.split("/", 2);
		String ipAddress = ipAddressCidrPrefix[0];
		int cidrPrefix = Integer.parseInt(ipAddressCidrPrefix[1]);

		sources.add(new IpSubnetFilterRule(ipAddress, cidrPrefix, IpFilterRuleType.ACCEPT));
	}

	@Validated
	public static class Config {

		@NotEmpty
		private List<String> sources = new ArrayList<>();

		@NotNull
		private RemoteAddressResolver remoteAddressResolver = new RemoteAddressResolver() {
		};

		public List<String> getSources() {
			return sources;
		}

		public Config setSources(List<String> sources) {
			this.sources = sources;
			return this;
		}

		public Config setSources(String... sources) {
			this.sources = Arrays.asList(sources);
			return this;
		}

		public Config setRemoteAddressResolver(RemoteAddressResolver remoteAddressResolver) {
			this.remoteAddressResolver = remoteAddressResolver;
			return this;
		}

	}

}
