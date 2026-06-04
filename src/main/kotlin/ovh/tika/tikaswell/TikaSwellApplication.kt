package ovh.tika.tikaswell

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class TikaSwellApplication

fun main(args: Array<String>) {
	runApplication<TikaSwellApplication>(*args)
}
