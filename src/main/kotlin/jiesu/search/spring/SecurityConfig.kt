package jiesu.search.spring

import jiesu.service.TokenAuthenticationFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
class SecurityConfig(val tokenAuthenticationFilter: TokenAuthenticationFilter,
                     @Value("\${searchservice.security.enabled:true}") val secured: Boolean) : WebSecurityConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {
        http.csrf().disable()
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        if (secured) {
            http.authorizeRequests().antMatchers("/api/**").authenticated().antMatchers("/**").permitAll()
        }
        http.addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
    }
}
