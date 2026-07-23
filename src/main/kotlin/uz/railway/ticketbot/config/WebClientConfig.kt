package uz.railway.ticketbot.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.util.concurrent.TimeUnit

@Configuration
class WebClientConfig {

    @Bean
    fun railwayWebClient(properties: RailwayProperties): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.connectTimeoutMs.toInt())
            .doOnConnected { connection ->
                connection.addHandlerLast(
                    ReadTimeoutHandler(properties.readTimeoutMs, TimeUnit.MILLISECONDS)
                )
            }

        return WebClient.builder()
            .baseUrl(properties.baseUrl)
            .clientConnector(org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
            .build()
    }
}
