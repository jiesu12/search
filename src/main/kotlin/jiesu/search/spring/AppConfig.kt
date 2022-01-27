package jiesu.search.spring

import com.fasterxml.jackson.databind.ObjectMapper
import jiesu.service.PublicKeyUpdater
import jiesu.service.TokenAuthenticationFilter
import jiesu.service.model.PublicKeyHolder
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import java.io.File

@Configuration
@EnableScheduling
class AppConfig(val discoveryClient: DiscoveryClient) {
    @Bean
    fun indexDir(@Value("\${search.index.dir}") dir: String) = File(dir)

    @Bean
    fun publicKeyHolder(): PublicKeyHolder =
            PublicKeyHolder(null)

    @Bean
    fun tokenAuthenticationFilter(objectMapper: ObjectMapper): TokenAuthenticationFilter =
            TokenAuthenticationFilter(objectMapper, publicKeyHolder())

    @Bean
    fun publicKeyUpdater(): PublicKeyUpdater =
            PublicKeyUpdater(discoveryClient,  publicKeyHolder())

    @Scheduled(fixedRate = 30000)
    fun refreshPublicKey() =
            publicKeyUpdater().refreshPublicKey()
}
