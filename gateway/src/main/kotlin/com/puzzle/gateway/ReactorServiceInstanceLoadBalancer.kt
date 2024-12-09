package com.puzzle.gateway

import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.client.loadbalancer.DefaultResponse
import org.springframework.cloud.client.loadbalancer.Request
import org.springframework.cloud.client.loadbalancer.Response
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

class RoomIdLoadBalancer(
    private val supplier: ServiceInstanceListSupplier,
) : ReactorServiceInstanceLoadBalancer {
    private val roomServerMap = ConcurrentHashMap<String, ServiceInstance>()

    override fun choose(request: Request<*>?): Mono<Response<ServiceInstance>>? {
//        request: Request<ServerWebExchange>
        // roomId 추출 (예: 쿼리 파라미터나 헤더에서)
        val exchange =
            request as? Request<ServerWebExchange>
                ?: return Mono.error(IllegalArgumentException("ServerWebExchange not found in request attributes"))

        val roomId =
            request.context as? String
                ?: "default"

        roomServerMap[roomId]?.let {
            println("Returning cached ServiceInstance for roomId: $roomId")
            return Mono.just(DefaultResponse(it))
        }

        // 저장된 인스턴스가 없는 경우 hash 기반으로 서버 선택
        return supplier
            .get()
            .next()
            .map { instances ->
                if (instances.isEmpty()) {
                    throw IllegalStateException("No instances available for service")
                }
                // 해시 기반 서버 선택
                val index = Math.abs(roomId.hashCode()) % instances.size
                val selectedInstance = instances[index]

                // 선택된 서버를 저장
                roomServerMap[roomId] = selectedInstance
                println("Selected and cached ServiceInstance for roomId: $roomId -> ${selectedInstance.host}:${selectedInstance.port}")
                DefaultResponse(selectedInstance)
            }
    }
}
