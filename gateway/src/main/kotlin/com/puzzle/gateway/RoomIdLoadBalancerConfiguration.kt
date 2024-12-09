package com.puzzle.gateway

import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@LoadBalancerClient(name = "websocket-service", configuration = [RoomIdLoadBalancerConfiguration::class])
class RoomIdLoadBalancerConfiguration {
    @Bean
    fun roomIdLoadBalancer(supplier: ServiceInstanceListSupplier): ReactorServiceInstanceLoadBalancer = RoomIdLoadBalancer(supplier)
}
