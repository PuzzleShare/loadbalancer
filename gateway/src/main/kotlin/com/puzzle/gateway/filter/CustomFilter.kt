package com.puzzle.gateway.filter
import com.puzzle.gateway.RoomRequest
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.loadbalancer.Response
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.cloud.gateway.support.ServiceUnavailableException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import java.net.URI

@Component
class CustomFilter(
    private val loadBalancer: ReactiveLoadBalancer<ServiceInstance>,
) : AbstractGatewayFilterFactory<CustomFilter.Config>(Config::class.java) {
    override fun apply(config: Config): GatewayFilter {
        return GatewayFilter { exchange, chain ->
            val path = exchange.request.uri.path // 예: /ws/roomId -> 9001:/ws /ws

            val roomId: String? = extractRoomId(path)

            println("Extracted Room ID: $roomId")

            // roomId가 default인 경우 기본 라우팅
            if (roomId.isNullOrBlank() || roomId == "default") {
                println("RoomId is default, skipping load balancing")
                return@GatewayFilter chain.filter(exchange)
            }

            // RoomRequest 생성
            val roomRequest = RoomRequest(roomId)

            // 로드밸런서를 통해 ServiceInstance 선택
            val targetInstanceMono: Mono<ServiceInstance> =
                loadBalancer
                    .choose(roomRequest)
                    .toMono()
                    .flatMap { response: Response<ServiceInstance> ->
                        if (response.hasServer()) {
                            Mono.just(response.server)
                        } else {
                            Mono.empty<ServiceInstance>()
                        }
                    }.switchIfEmpty(
                        Mono.error(ServiceUnavailableException("No available service instances")),
                    )

            return@GatewayFilter targetInstanceMono
                .flatMap { targetInstance ->
                    val targetUri = URI("ws://${targetInstance.host}:${targetInstance.port}/ws")
                    println("Target URI: $targetUri")

                    val modifiedExchange =
                        exchange
                            .mutate()
                            .request(
                                exchange.request
                                    .mutate()
                                    .uri(targetUri)
                                    .build(),
                            ).build()

                    chain.filter(modifiedExchange)
                }.onErrorResume {
                    exchange.response.statusCode = HttpStatus.SERVICE_UNAVAILABLE
                    exchange.response.setComplete()
                }
        }
    }

    private fun extractRoomId(path: String): String? {
        val segments: List<String> = path.split("/")
        return if (segments.size >= 3) segments[2] else null
    }

    class Config {
        var name: String = "com.puzzle.gateway.filter.CustomFilter"
    }
}
