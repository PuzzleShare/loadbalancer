package com.puzzle.gateway
import org.springframework.cloud.client.DefaultServiceInstance
import org.springframework.cloud.client.ServiceInstance
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Flux

@Configuration
class LoadBalancerConfig {
    @Bean
    fun staticServiceInstanceListSupplier(): ServiceInstanceListSupplier {
        return object : ServiceInstanceListSupplier {
            override fun getServiceId(): String = "websocket-service"

            override fun get(): Flux<List<ServiceInstance>> {
                val instances =
                    listOf(
                        DefaultServiceInstance(
                            "websocket-service-1",
                            "websocket-service",
                            "localhost",
                            9001,
                            false,
                        ),
                        DefaultServiceInstance(
                            "websocket-service-2",
                            "websocket-service",
                            "localhost",
                            9002,
                            false,
                        ),
                    )
                return Flux.just(instances)
            }
        }
    }
}
