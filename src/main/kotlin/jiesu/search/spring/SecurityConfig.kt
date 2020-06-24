package jiesu.search.spring

import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import jiesu.search.service.PublicKeyHolder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.web.filter.GenericFilterBean
import java.io.IOException
import javax.servlet.FilterChain
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest

/**
 * Put multiple classes here so that it is easy to copy/paste to other projects.
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
class SecurityConfig(val tokenAuthenticationFilter: TokenAuthenticationFilter,
                     @Value("\${fileservice.security.enabled:true}") val secured: Boolean) : WebSecurityConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {
        http.csrf().disable()
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        if (secured) {
            http.authorizeRequests().antMatchers("/api/**").authenticated().antMatchers("/**").permitAll()
        }
        http.addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
    }
}

@Service
class TokenAuthenticationFilter(val objectMapper: ObjectMapper, val publicKeyHolder: PublicKeyHolder) : GenericFilterBean() {

    companion object {
        val log: Logger = LoggerFactory.getLogger(TokenAuthenticationFilter::class.java)
        const val TOKEN_NAME = "fstoken"
    }

    override fun doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) {
        val request = req as HttpServletRequest
        val user: User? = getTokenFromHeader(request)
        if (user != null) {
            SecurityContextHolder.getContext().authentication =
                    PreAuthenticatedAuthenticationToken(user, null, emptyList())
        } else {
            val htmlLink: HtmlLink? = getTokenFromRequestParams(request)
            if (htmlLink != null) {
                SecurityContextHolder.getContext().authentication =
                        PreAuthenticatedAuthenticationToken(htmlLink, null, emptyList())
            }
        }
        chain.doFilter(req, res)
    }

    private fun getTokenFromHeader(request: HttpServletRequest): User? {
        val token = request.getHeader(TOKEN_NAME)
        if (token != null) {
            try {
                return parseToken(token, TokenPurpose.LOGIN, User::class.java)
            } catch (e: Exception) {
                log.debug("Invalid token in header, reason - {}", e.toString())
            }
        }
        return null
    }

    /**
     * For download link or html5 audio/video tag, we can't use custom http header to provide token.
     * So we use a short life token for such purpose. This token can be provided as URL GET
     * parameter.
     */
    private fun getTokenFromRequestParams(request: HttpServletRequest): HtmlLink? {
        val token = request.getParameter(TOKEN_NAME)
        if (token != null) {
            try {
                return parseToken(token, TokenPurpose.HTML_LINK, HtmlLink::class.java)
            } catch (e: Exception) {
                log.debug("Invalid token in URL param, reason - {}", e.toString())
            }
        }
        return null
    }

    fun <T> parseToken(token: String, tokenPurpose: TokenPurpose, clazz: Class<T>): T {
        return try {
            val body: Claims = Jwts.parserBuilder()
                    .setSigningKey(publicKeyHolder.publicKey)
                    .build()
                    .parseClaimsJws(token)
                    .body
            if (tokenPurpose.name != body.audience) {
                throw AuthException("Wrong type of token provided.")
            }
            objectMapper.readValue(body.subject, clazz)
        } catch (e: ExpiredJwtException) {
            throw AuthException("Token has expired", e)
        } catch (e: IOException) {
            throw AuthException("Invalid token", e)
        } catch (e: AuthException) {
            throw e
        } catch (e: RuntimeException) {
            throw AuthException("Invalid token", e)
        }
    }
}

enum class TokenPurpose {
    LOGIN, HTML_LINK
}

class AuthException : RuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}

data class User(val username: String)

data class HtmlLink(val name: String, val fspath: String)