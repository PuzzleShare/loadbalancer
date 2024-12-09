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
            val path = exchange.request.uri.path // 예: /ws/room123
            val roomId: String? = extractRoomId(path)
            println("Request Path: $path")
            println("Extracted Room ID: $roomId") // 추출된 Room ID 출력
            if (roomId.isNullOrBlank()) {
                // roomId가 없으면 기본 라우팅을 따름
                return@GatewayFilter chain.filter(exchange)
            }

            // Create a RoomRequest with roomId
            val roomRequest = RoomRequest(roomId)
            println("RoomRequest created: $roomRequest") // RoomRequest 디버깅
            // Choose a ServiceInstance based on roomId
            val targetInstanceMono: Mono<ServiceInstance> =
                loadBalancer
                    .choose(roomRequest)
                    .toMono()
                    .flatMap { response: Response<ServiceInstance> ->
                        if (response.hasServer()) {
                            println("ServiceInstance found: ${response.server}")
                            Mono.just(response.server)
                        } else {
                            println("No ServiceInstance found.") // 없을 경우 디버깅
                            Mono.empty<ServiceInstance>()
                        }
                    }.switchIfEmpty(
                        Mono.error(ServiceUnavailableException("No available service instances")),
                    )

            targetInstanceMono
                .flatMap { targetInstance ->
                    // 선택된 인스턴스를 기반으로 URI 수정
                    val targetUri = URI("ws://${targetInstance.host}:${targetInstance.port}/ws")
                    println("Target URI: $targetUri") // 타겟 URI 디버깅
                    // 요청의 URI를 백엔드 서버 URI로 변경
                    val modifiedExchange =
                        exchange
                            .mutate()
                            .request(
                                exchange.request
                                    .mutate()
                                    .uri(targetUri)
                                    .build(),
                            ).build()

                    // 수정된 Exchange를 사용하여 필터 체인 실행
                    chain.filter(modifiedExchange)
                }.onErrorResume {
                    println("Error during filter execution") // 에러 디버깅
                    exchange.response.statusCode = HttpStatus.SERVICE_UNAVAILABLE
                    exchange.response.setComplete()
                }
        }
    }

    // roomId 추출 로직 (경로 패턴에 따라 조정)
    private fun extractRoomId(path: String): String? {
        // 예: /ws/{roomId}/...
        val segments: List<String> = path.split("/")
        return if (segments.size >= 3) segments[2] else null
    }

    class Config {
        var name: String = "com.puzzle.gateway.filter.CustomFilter"
    }
}
