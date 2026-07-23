package uz.railway.ticketbot

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
