package com.puzzle.gateway

import org.springframework.cloud.client.loadbalancer.Request
import org.springframework.web.server.ServerWebExchange

class RoomRequest(
    val roomId: String,
) : Request<ServerWebExchange>
