package jiesu.search.spring

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File

@Configuration
class AppConfig {
    @Bean
    fun indexDir(@Value("\${search.index.dir}") dir: String) = File(dir)
}