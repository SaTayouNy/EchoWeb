package org.example.echo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.example.echo.exception.ApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class JsonRequestSizeLimitFilter extends OncePerRequestFilter {
    private static final String PAYLOAD_TOO_LARGE_CODE = "PAYLOAD_TOO_LARGE";
    private static final String PAYLOAD_TOO_LARGE_MESSAGE = "Request body is too large";

    private final long authMaxBytes;
    private final long reviewAndAnswerMaxBytes;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final RequestMatcher authMatcher = new AntPathRequestMatcher("/v1/auth/**");
    private final RequestMatcher reviewAndAnswerMatcher = new OrRequestMatcher(
        new AntPathRequestMatcher("/v1/reviews/**"),
        new AntPathRequestMatcher("/v1/answers/**"),
        new AntPathRequestMatcher("/v1/reviews/*/answers"),
        new AntPathRequestMatcher("/v1/me/**"),
        new AntPathRequestMatcher("/v2/me/**"),
        new AntPathRequestMatcher("/v2/reviews/**"),
        new AntPathRequestMatcher("/v2/admin/**")
    );

    public JsonRequestSizeLimitFilter(
        @Value("${app.http.max-auth-json-bytes:65536}") long authMaxBytes,
        @Value("${app.http.max-review-json-bytes:262144}") long reviewAndAnswerMaxBytes,
        @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver
    ) {
        this.authMaxBytes = authMaxBytes;
        this.reviewAndAnswerMaxBytes = reviewAndAnswerMaxBytes;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (!isJsonMutation(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        long limit = resolveLimit(request);
        long contentLength = request.getContentLengthLong();
        if (limit <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        if (contentLength > limit) {
            resolvePayloadTooLarge(request, response);
            return;
        }

        try {
            filterChain.doFilter(new SizeLimitedRequestWrapper(request, limit), response);
        } catch (ApiException ex) {
            if (!PAYLOAD_TOO_LARGE_CODE.equals(ex.getCode())) {
                throw ex;
            }
            resolvePayloadTooLarge(request, response);
        }
    }

    private boolean isJsonMutation(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"POST".equalsIgnoreCase(method) && !"PUT".equalsIgnoreCase(method) && !"PATCH".equalsIgnoreCase(method)) {
            return false;
        }

        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).contains("application/json");
    }

    private long resolveLimit(HttpServletRequest request) {
        if (authMatcher.matches(request)) {
            return authMaxBytes;
        }
        if (reviewAndAnswerMatcher.matches(request)) {
            return reviewAndAnswerMaxBytes;
        }
        return -1L;
    }

    private void resolvePayloadTooLarge(HttpServletRequest request, HttpServletResponse response) {
        handlerExceptionResolver.resolveException(
            request,
            response,
            null,
            payloadTooLarge()
        );
    }

    private static ApiException payloadTooLarge() {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, PAYLOAD_TOO_LARGE_CODE, PAYLOAD_TOO_LARGE_MESSAGE);
    }

    private static final class SizeLimitedRequestWrapper extends HttpServletRequestWrapper {
        private final long maxBytes;
        private ServletInputStream inputStream;
        private BufferedReader reader;

        private SizeLimitedRequestWrapper(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (inputStream == null) {
                inputStream = new LimitedServletInputStream(super.getInputStream(), maxBytes);
            }
            return inputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (reader == null) {
                Charset charset = resolveCharset(getCharacterEncoding());
                reader = new BufferedReader(new InputStreamReader(getInputStream(), charset));
            }
            return reader;
        }

        private Charset resolveCharset(String encoding) {
            if (encoding == null || encoding.isBlank()) {
                return StandardCharsets.UTF_8;
            }
            return Charset.forName(encoding);
        }
    }

    private static final class LimitedServletInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long maxBytes;
        private long bytesRead;

        private LimitedServletInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int read = delegate.read();
            if (read != -1) {
                recordBytes(1);
            }
            return read;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            if (read > 0) {
                recordBytes(read);
            }
            return read;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        private void recordBytes(long additionalBytes) {
            bytesRead += additionalBytes;
            if (bytesRead > maxBytes) {
                throw payloadTooLarge();
            }
        }
    }
}
