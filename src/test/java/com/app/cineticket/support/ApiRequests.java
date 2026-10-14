package com.app.cineticket.support;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Requisições autenticadas por JWT nos testes de integração. */
public final class ApiRequests {

    private ApiRequests() {
    }

    /** Adiciona o Bearer token e, se houver, o corpo JSON. */
    public static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder request, String token, String body) {
        request.header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request;
    }
}
