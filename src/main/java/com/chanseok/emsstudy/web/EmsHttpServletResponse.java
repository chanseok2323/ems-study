package com.chanseok.emsstudy.web;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

/**
 * HttpServletResponse 구현체: 메모리 버퍼 사용, 단순 구현
 */
public class EmsHttpServletResponse implements HttpServletResponse {
    /**
     * 상태 코드 (기본 200)
     */
    private int status = SC_OK;

    /**
     * sendError 시 전달된 메시지(편의용; 서블릿 표준에는 getter 없음)
     */
    private String errorMessage;

    /**
     * 응답이 커밋되었는지 여부 (flushBuffer, sendError, sendRedirect 등으로 true)
     */
    private boolean committed = false;

    /**
     * 버퍼 크기 (의미는 제한적, 메모리 버퍼 사용)
     */
    private int bufferSize = 8192;

    /**
     * 응답 바디 버퍼
     */
    private final ByteArrayOutputStream body = new ByteArrayOutputStream(1024);

    /**
     * Writer/OutputStream 상호배타 보장용
     */
    private PrintWriter writer;
    private ServletOutputStream outputStream;

    /**
     * Content-Type 및 문자셋
     */
    private String contentType;
    private String characterEncoding = StandardCharsets.UTF_8.name(); // 기본 UTF-8 가정

    /**
     * 헤더(대소문자 무시, 다중값 지원)
     */
    private final Map<String, List<String>> headers =
            new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    /**
     * Locale (설정 시 Content-Language를 업데이트할 수 있음)
     */
    private Locale locale = Locale.getDefault();

    /**
     * 명시적 Content-Length (옵션)
     */
    private Long explicitContentLength;

    // ------------------------------ 편의 메서드 ------------------------------

    /**
     * 응답 바디를 바이트 배열로 반환
     */
    public byte[] getContentAsByteArray() {
        return body.toByteArray();
    }

    /**
     * 응답 바디를 문자열로 반환 (응답 charset 우선, 없으면 UTF-8)
     */
    public String getContentAsString() {
        Charset cs = (characterEncoding != null)
                ? Charset.forName(characterEncoding)
                : StandardCharsets.UTF_8;
        return new String(body.toByteArray(), cs);
    }

    /**
     * sendError 메시지 조회(스프링 Mock과 유사한 편의)
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public void addCookie(Cookie cookie) {
        if (cookie == null) return;
        // 간단히 Set-Cookie 헤더만 추가 (속성 직렬화는 단순화)
        StringBuilder sb = new StringBuilder();
        sb.append(cookie.getName()).append('=').append(cookie.getValue() == null ? "" : cookie.getValue());
        if (cookie.getPath() != null) sb.append("; Path=").append(cookie.getPath());
        if (cookie.getDomain() != null) sb.append("; Domain=").append(cookie.getDomain());
        if (cookie.getMaxAge() >= 0) sb.append("; Max-Age=").append(cookie.getMaxAge());
        if (cookie.getSecure()) sb.append("; Secure");
        if (cookie.isHttpOnly()) sb.append("; HttpOnly");
        addHeader("Set-Cookie", sb.toString());
    }

    @Override
    public boolean containsHeader(String name) {
        return headers.containsKey(name);
    }

    @Override
    public String encodeURL(String url) {
        return url;
    }              // 세션ID 인코딩 비지원

    @Override
    public String encodeRedirectURL(String url) {
        return url;
    }      // 세션ID 인코딩 비지원

    @Deprecated
    @Override
    public String encodeUrl(String url) {
        return encodeURL(url);
    }

    @Deprecated
    @Override
    public String encodeRedirectUrl(String url) {
        return encodeRedirectURL(url);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        checkCommitted();
        this.status = sc;
        this.errorMessage = msg;
        resetBuffer();
        this.committed = true;
    }

    @Override
    public void sendError(int sc) throws IOException {
        sendError(sc, null);
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        checkCommitted();
        setStatus(SC_FOUND);            // 302
        setHeader("Location", location);
        resetBuffer();
        this.committed = true;
    }

    @Override
    public void setDateHeader(String name, long date) {
        setHeader(name, toHttpDate(date));
    }

    @Override
    public void addDateHeader(String name, long date) {
        addHeader(name, toHttpDate(date));
    }

    private String toHttpDate(long epochMillis) {
        // RFC 1123
        SimpleDateFormat fmt = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("GMT"));
        return fmt.format(new Date(epochMillis));
    }

    @Override
    public void setHeader(String name, String value) {
        List<String> list = new ArrayList<>(1);
        list.add(value);
        headers.put(name, list);
        if ("Content-Type".equalsIgnoreCase(name)) {
            this.contentType = value;
            // charset 동기화
            int i = value.toLowerCase(Locale.ROOT).indexOf("charset=");
            if (i >= 0) {
                String enc = value.substring(i + "charset=".length()).trim();
                int semi = enc.indexOf(';');
                if (semi > 0) enc = enc.substring(0, semi);
                this.characterEncoding = enc;
            }
        }
        if ("Content-Length".equalsIgnoreCase(name)) {
            try {
                this.explicitContentLength = Long.parseLong(value);
            } catch (NumberFormatException ignore) {
            }
        }
    }

    @Override
    public void addHeader(String name, String value) {
        headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        if ("Content-Type".equalsIgnoreCase(name) && this.contentType == null) {
            this.contentType = value; // 첫 값만 반영
        }
    }

    @Override
    public void setIntHeader(String name, int value) {
        setHeader(name, String.valueOf(value));
    }

    @Override
    public void addIntHeader(String name, int value) {
        addHeader(name, String.valueOf(value));
    }

    @Override
    public void setStatus(int sc) {
        this.status = sc;
    }

    /**
     * @deprecated
     */
    @Deprecated
    @Override
    public void setStatus(int sc, String sm) {
        this.status = sc;
        this.errorMessage = sm;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public String getHeader(String name) {
        List<String> list = headers.get(name);
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    @Override
    public Collection<String> getHeaders(String name) {
        List<String> list = headers.get(name);
        return (list == null) ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    @Override
    public Collection<String> getHeaderNames() {
        return Collections.unmodifiableSet(headers.keySet());
    }

    @Override
    public String getCharacterEncoding() {
        return characterEncoding; // null 허용 가능하지만, 여기선 기본 UTF-8
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    /**
     * OutputStream을 제공합니다. getWriter()와 동시 사용 불가.
     */
    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw new IllegalStateException("getWriter() already called");
        }
        if (outputStream == null) {
            outputStream = new InternalServletOutputStream(body);
        }
        return outputStream;
    }

    /**
     * Writer를 제공합니다. getOutputStream()과 동시 사용 불가.
     * characterEncoding을 사용하여 OutputStreamWriter를 구성합니다.
     */
    @Override
    public PrintWriter getWriter() throws IOException {
        if (outputStream != null) {
            throw new IllegalStateException("getOutputStream() already called");
        }
        if (writer == null) {
            Charset cs = (characterEncoding != null)
                    ? Charset.forName(characterEncoding)
                    : StandardCharsets.UTF_8;
            writer = new PrintWriter(new OutputStreamWriter(body, cs), true);
        }
        return writer;
    }

    @Override
    public void setCharacterEncoding(String charset) {
        this.characterEncoding = charset;
        if (this.contentType != null && !this.contentType.toLowerCase(Locale.ROOT).contains("charset=")) {
            // Content-Type에 charset이 없다면 보조적으로 붙여준다.
            setHeader("Content-Type", this.contentType + ";charset=" + charset);
        }
    }

    @Override
    public void setContentLength(int len) {
        setIntHeader("Content-Length", len);
        this.explicitContentLength = (long) len;
    }

    @Override
    public void setContentLengthLong(long len) {
        setHeader("Content-Length", String.valueOf(len));
        this.explicitContentLength = len;
    }

    @Override
    public void setContentType(String type) {
        setHeader("Content-Type", type);
        this.contentType = type;
    }

    @Override
    public void setBufferSize(int size) {
        if (committed) return;
        this.bufferSize = size;
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * 버퍼를 플러시하고 committed로 전환
     */
    @Override
    public void flushBuffer() throws IOException {
        if (writer != null) writer.flush();
        if (outputStream != null) outputStream.flush();
        this.committed = true;
    }

    /**
     * committed 여부
     */
    @Override
    public boolean isCommitted() {
        return committed;
    }

    /**
     * 헤더/상태는 유지하되 바디 버퍼만 초기화.
     * committed 상태에선 resetBuffer 불가(컨테이너 규칙과 유사).
     */
    @Override
    public void resetBuffer() {
        if (committed) {
            throw new IllegalStateException("Cannot reset buffer after commit");
        }
        body.reset();
    }

    /**
     * 헤더/상태/바디 전부 초기화. committed 상태에선 불가.
     */
    @Override
    public void reset() {
        if (committed) {
            throw new IllegalStateException("Cannot reset after commit");
        }
        status = SC_OK;
        errorMessage = null;
        headers.clear();
        contentType = null;
        characterEncoding = StandardCharsets.UTF_8.name();
        explicitContentLength = null;
        body.reset();
        writer = null;
        outputStream = null;
    }

    @Override
    public void setLocale(Locale loc) {
        this.locale = loc;
        // 선택적으로 Content-Language를 갱신해줄 수 있음
        setHeader("Content-Language", loc.toLanguageTag());
    }

    @Override
    public Locale getLocale() {
        return locale;
    }

    // ------------------------------ 내부 유틸 ------------------------------

    private void checkCommitted() throws IOException {
        if (committed) throw new IOException("Response already committed");
    }

    /**
     * 내부 ServletOutputStream 구현: 메모리 바디에 기록
     */
    private static class InternalServletOutputStream extends ServletOutputStream {
        private final OutputStream delegate;

        InternalServletOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            throw new UnsupportedOperationException("Async not supported");
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
