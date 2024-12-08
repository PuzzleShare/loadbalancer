package com.puzzle.gateway

import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Component
class GlobalFilter(
    private val discoveryClient: DiscoveryClient,
) : AbstractGatewayFilterFactory<GlobalFilter.Config>(Config::class.java) {
    // 인메모리 매핑 (roomId -> serverId)
    private val roomServerMap: ConcurrentHashMap<String, Int> = ConcurrentHashMap()

    // 라운드 로빈을 위한 카운터
    private val counter: AtomicInteger = AtomicInteger(0)

    // 게임 서버 URI 목록
    private val backendUris: List<String> =
        listOf(
            "ws://localhost:9001",
            "ws://localhost:9002",
        )

    override fun apply(config: Config): GatewayFilter {
        return GatewayFilter { exchange, chain ->

            println("WebSocketRoutingFilter: Received request for URI: ${exchange.request.uri.path}")
            val path = exchange.request.uri.path // 예: /ws/room123
            val roomId: String? = extractRoomId(path)
            println("WebSocketRoutingFilter: Extracted roomId: $roomId")
            if (roomId.isNullOrBlank()) {
                // roomId가 없으면 기본 서버으로 라우팅하거나 요청 거부
                return@GatewayFilter chain.filter(exchange)
            }
            println("-------roomId $roomId")
            // 게임 서버 인스턴스 조회
            val instances: List<ServiceInstance> = discoveryClient.getInstances("websocket-service")
            println("WebSocketRoutingFilter: Retrieved ${instances.size} game-server instances")
            // roomId에 해당하는 serverId 조회

            var index: Int? = roomServerMap[roomId]

            if (index == null) {
                // serverId가 없으면 새로운 serverId 할당
                val hash = Math.abs(roomId.hashCode())
                index = hash % instances.size
                roomServerMap[roomId] = index
            }
            val targetInstance = instances[index]

            val targetUri = URI("ws://${targetInstance.host}:${targetInstance.port}/ws")
            println("------${targetUri.host}")
            // serverId에 따른 백엔드 서버 URI 결정
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
            // 요청의 URI를 백엔드 서버 URI로 변경
            chain.filter(modifiedExchange)
//
//            // 새로운 URI 생성
//            val newUri: URI =
//                UriComponentsBuilder
//                    .fromUri(URI(targetUri))
//                    .path("/") // roomId 제외한 경로 설정
//                    .build(true)
//                    .toUri()
//            println("-------new ${newUri.toURL()}")
//            // 새로운 URI로 요청 교체
//            exchange.attributes[ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR] = newUri
        }
    }

    // roomId 추출 로직 (경로 패턴에 따라 조정)
    private fun extractRoomId(path: String): String? {
        // 예: /ws/{roomId}/...
        val segments: List<String> = path.split("/")
        return if (segments.size >= 3) segments[2] else null
    }

    // serverId 할당 로직 (라운드 로빈 방식)
    private fun assignServerId(): String {
        val index: Int = counter.getAndIncrement() % backendUris.size
        return "server${index + 1}" // server1, server2
    }

    // serverId에 따른 백엔드 서버 URI 결정
    private fun getBackendUri(serverId: String): String? =
        when (serverId) {
            "server1" -> backendUris[0] // ws://localhost:8081
            "server2" -> backendUris[1] // ws://localhost:8082
            else -> null
        }

    class Config {
        var name: String = "GlobalFilter"
    }
}
