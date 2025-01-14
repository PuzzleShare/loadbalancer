package com.puzzle.gateway

import org.springframework.cloud.client.loadbalancer.Request

class RoomRequest(
    val roomId: String,
) : Request<String> {
    override fun getContext(): String = roomId
}
