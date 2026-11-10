package itmo.goto.web

import itmo.goto.generated.model.Event
import org.springframework.core.MethodParameter
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice
import java.net.URI

/**
 * Points the Location header of every created event at its address, which the generated controller cannot do.
 */
@RestControllerAdvice
class EventLocation : ResponseBodyAdvice<Event> {
    override fun supports(returnType: MethodParameter, converterType: Class<out HttpMessageConverter<*>>): Boolean =
        returnType.parameterType == Event::class.java &&
            returnType.method?.let { AnnotatedElementUtils.findMergedAnnotation(it, ResponseStatus::class.java) }
                ?.code == HttpStatus.CREATED

    override fun beforeBodyWrite(
        body: Event?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
    ): Event? = body?.also { response.headers.location = URI.create("/api/v1/events/${it.id}") }
}
