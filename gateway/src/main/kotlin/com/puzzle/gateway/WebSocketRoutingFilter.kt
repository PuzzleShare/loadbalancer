package com.puzzle.gateway

import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.GatewayFilterChain
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.net.URI

@Component
class WebSocketRoutingFilter(
    private val discoveryClient: DiscoveryClient,
) : GatewayFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: GatewayFilterChain,
    ): Mono<Void> {
        println("WebSocketRoutingFilter: Received request for URI: ${exchange.request.uri.path}")

        val path = exchange.request.uri.path // 예: /ws/room123
        val segments = path.split("/")
        if (segments.size < 3) {
            println("WebSocketRoutingFilter: Invalid WebSocket path: $path")
            return Mono.error(IllegalArgumentException("Invalid WebSocket path"))
        }
        val roomId = segments[2]
        println("WebSocketRoutingFilter: Extracted roomId: $roomId")

        // 게임 서버 인스턴스 조회
        val instances: List<ServiceInstance> = discoveryClient.getInstances("game-server")
        println("WebSocketRoutingFilter: Retrieved ${instances.size} game-server instances")
        if (instances.isEmpty()) {
            println("WebSocketRoutingFilter: No game server instances available")
            return Mono.error(IllegalStateException("No game server instances available"))
        }

        // RoomId를 해싱하여 인스턴스 선택
        val hash = Math.abs(roomId.hashCode())
        val index = hash % instances.size
        val targetInstance = instances[index]
        println("WebSocketRoutingFilter: Selected game-server instance: ${targetInstance.host}:${targetInstance.port}")

        // WebSocket URI 생성
        val targetUri = URI("ws://${targetInstance.host}:${targetInstance.port}/ws/$roomId")
        println("WebSocketRoutingFilter: Routing to WebSocket URI: $targetUri")

        // 요청 URI 수정
        val modifiedExchange =
            exchange
                .mutate()
                .request(
                    exchange.request
                        .mutate()
                        .uri(targetUri)
                        .build(),
                ).build()

        println("WebSocketRoutingFilter: Modified exchange with new URI")

        return chain.filter(modifiedExchange)
    }
}
