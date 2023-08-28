package cn.haitaoss.demo;

import reactor.core.publisher.Mono;

public class Main {
	public static void main(String[] args) throws Exception {
		Mono<String> cache = Mono.defer(() -> {
			System.out.println("create ....");
			return Mono.just("hello world");
		})
				/*.cache(
						resp -> Duration.ofSeconds(10),
						throwable -> Duration.ZERO,
						() -> Duration.ZERO)*/;

		for (int i = 0; i < 10; i++) {
			System.out.println(cache.block());
		}
	}
}
