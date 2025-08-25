package com.chanseok.emsstudy.web;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionContext;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 매우 단순한 HttpServletRequest 구현체.
 * EMS 연동에 필요한 부분만 최소한으로 구현
 */
public class EmsHttpServletRequest implements HttpServletRequest {

    // ---- 기본값 (서블릿 컨텍스트/네트워크 정보가 없을 때 사용될 디폴트) ----
    public static final String DEFAULT_PROTOCOL = "HTTP/1.1";
    public static final String DEFAULT_SCHEME = "http";
    public static final String DEFAULT_SERVER = "localhost";
    public static final int DEFAULT_PORT = 80;
    public static final String DEFAULT_REMOTE_ADDR = "127.0.0.1";
    public static final String DEFAULT_REMOTE_HOST = "localhost";

    // 날짜 헤더 파싱용(HTTP 표준 패턴)
    private static final TimeZone GMT = TimeZone.getTimeZone("GMT");
    private static final String[] DATE_FORMATS = {
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd-MMM-yy HH:mm:ss zzz",
            "EEE MMM dd HH:mm:ss yyyy"
    };

    // ---- 요청 상태 보관 필드 ----
    private final ServletContext servletContext;              // 서블릿 환경(필수)
    private final Map<String, Object> attributes = new LinkedHashMap<>();     // request attribute
    private final Map<String, String[]> parameters = new LinkedHashMap<>();   // 쿼리/폼 파라미터
    private final Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); // 헤더(대소문자 구분 X)
    private final Deque<Locale> locales = new ArrayDeque<>(); // 로케일 우선순위(맨 앞이 우선)

    // 네트워크/프로토콜 정보
    private String method;
    private String protocol = DEFAULT_PROTOCOL;
    private String scheme = DEFAULT_SCHEME;
    private String serverName = DEFAULT_SERVER;
    private int serverPort = DEFAULT_PORT;

    private String remoteAddr = DEFAULT_REMOTE_ADDR;
    private String remoteHost = DEFAULT_REMOTE_HOST;
    private int remotePort = DEFAULT_PORT;

    private String localName = DEFAULT_SERVER;
    private String localAddr = DEFAULT_REMOTE_ADDR;
    private int localPort = DEFAULT_PORT;

    private boolean secure = false;
    private boolean asyncSupported = false; // 미지원
    private boolean asyncStarted = false;   // 미지원
    private DispatcherType dispatcherType = DispatcherType.REQUEST;

    // 경로 정보
    private String contextPath = "";
    private String servletPath = "";
    private String requestURI = "";
    private String queryString;

    // 본문/인코딩
    private String contentType;
    private String characterEncoding;
    private byte[] content;

    // 바디 접근 스트림/리더 (둘 중 하나만 사용 가능)
    private ServletInputStream inputStream;
    private BufferedReader reader;

    // 인증/세션 관련
    private Cookie[] cookies;
    private String authType;
    private String remoteUser;
    private Principal userPrincipal;
    private final Set<String> userRoles = new HashSet<>();

    private String requestedSessionId;
    private boolean requestedSessionIdValid = true;
    private boolean requestedSessionIdFromCookie = true;
    private boolean requestedSessionIdFromURL = false;

    private HttpSession session; // 필요 시 생성(매우 단순한 세션)

    // ---- 생성자 ----

    /**
     * 기본 생성자: method/requestURI는 빈 값으로 초기화합니다.
     */
    public EmsHttpServletRequest(ServletContext servletContext) {
        this(servletContext, "", "");
    }

    /**
     * 주요 생성자.
     *
     * @param servletContext 컨텍스트
     * @param method         HTTP 메서드 (예: "POST")
     * @param requestURI     요청 URI (예: "/ems/process")
     */
    public EmsHttpServletRequest(ServletContext servletContext,
                                 String method,
                                 String requestURI) {
        this.servletContext = Objects.requireNonNull(servletContext, "servletContext");
        this.method = method;
        this.requestURI = requestURI;
        this.locales.add(Locale.ENGLISH); // 디폴트 로케일
    }

    // ---- 설정/헬퍼 메서드 (빌더처럼 사용) ----

    /**
     * 요청 바디를 바이트로 설정합니다. 설정 시 InputStream/Reader 캐시는 초기화됩니다.
     */
    public void setContent(byte[] content) {
        this.content = content;
        this.inputStream = null;
        this.reader = null;
    }

    /**
     * 요청의 문자 인코딩을 설정합니다.
     * Content-Type에 charset=이 없으면 자동으로 붙입니다.
     */
    @Override
    public void setCharacterEncoding(String enc) {
        this.characterEncoding = enc;
        if (this.contentType != null && !this.contentType.toLowerCase(Locale.ROOT).contains("charset=")) {
            this.contentType = this.contentType + ";charset=" + enc;
            setHeader("Content-Type", this.contentType);
        }
    }

    /**
     * Content-Type 헤더를 설정하고, charset이 포함되어 있으면 characterEncoding도 동기화합니다.
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
        setHeader("Content-Type", contentType);
        if (contentType != null) {
            String lc = contentType.toLowerCase(Locale.ROOT);
            int idx = lc.indexOf("charset=");
            if (idx >= 0) {
                String enc = contentType.substring(idx + "charset=".length()).trim();
                int semi = enc.indexOf(';');
                if (semi > 0) enc = enc.substring(0, semi);
                this.characterEncoding = enc;
            }
        }
    }

    /**
     * 단일 헤더 세팅(기존 값 대체).
     */
    public void setHeader(String name, String value) {
        List<String> list = new ArrayList<>(1);
        list.add(value);
        headers.put(name, list);
    }

    /**
     * 다중 값 헤더 추가(append).
     */
    public void addHeader(String name, String value) {
        headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
    }

    // 경로/메서드/네트워크 설정자(DispatcherServlet 매핑 정확도에 중요)
    public void setMethod(String method) {
        this.method = method;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath == null ? "" : contextPath;
    }

    public void setServletPath(String servletPath) {
        this.servletPath = servletPath == null ? "" : servletPath;
    }

    public void setQueryString(String qs) {
        this.queryString = qs;
    }

    public void setRequestURI(String requestURI) {
        this.requestURI = requestURI;
    }

    public void setServerName(String name) {
        this.serverName = name;
    }

    public void setServerPort(int port) {
        this.serverPort = port;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
        this.secure = "https".equalsIgnoreCase(scheme);
    }

    public void setRemoteAddr(String addr) {
        this.remoteAddr = addr;
    }

    public void setRemoteHost(String host) {
        this.remoteHost = host;
    }

    public void setRemotePort(int port) {
        this.remotePort = port;
    }

    public void setLocalName(String name) {
        this.localName = name;
    }

    public void setLocalAddr(String addr) {
        this.localAddr = addr;
    }

    public void setLocalPort(int port) {
        this.localPort = port;
    }

    /**
     * 선호 로케일을 앞쪽에 추가하고, Accept-Language 헤더 스냅샷을 갱신합니다.
     */
    public void addPreferredLocale(Locale locale) {
        this.locales.addFirst(Objects.requireNonNull(locale));
        // Accept-Language 재구성
        StringBuilder al = new StringBuilder();
        Iterator<Locale> it = locales.descendingIterator();
        boolean first = true;
        while (it.hasNext()) {
            if (!first) al.append(", ");
            al.append(it.next().toLanguageTag());
            first = false;
        }
        setHeader("Accept-Language", al.toString());
    }

    /**
     * 쿠키 배열을 설정하고, Cookie 헤더 문자열도 갱신합니다.
     */
    public void setCookies(Cookie... cookies) {
        this.cookies = (cookies == null || cookies.length == 0) ? null : cookies;
        if (this.cookies == null) {
            headers.remove("Cookie");
        } else {
            String v = encodeCookies(this.cookies);
            setHeader("Cookie", v);
        }
    }

    /**
     * Cookie[] → "k=v; k2=v2" 형태로 직렬화
     */
    private static String encodeCookies(Cookie... cookies) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cookies.length; i++) {
            Cookie c = cookies[i];
            if (i > 0) sb.append("; ");
            sb.append(c.getName()).append('=').append(c.getValue() == null ? "" : c.getValue());
        }
        return sb.toString();
    }

    // 파라미터 세팅(폼/쿼리)
    public void setParameter(String name, String value) {
        Objects.requireNonNull(name, "param name");
        parameters.put(name, new String[]{value});
    }

    public void setParameter(String name, String... values) {
        Objects.requireNonNull(name, "param name");
        parameters.put(name, values);
    }

    public void addParameter(String name, String... values) {
        Objects.requireNonNull(name, "param name");
        String[] old = parameters.get(name);
        if (old == null) {
            parameters.put(name, values);
        } else {
            String[] merged = Arrays.copyOf(old, old.length + values.length);
            System.arraycopy(values, 0, merged, old.length, values.length);
            parameters.put(name, merged);
        }
    }

    // ---- HttpServletRequest 구현부 (핵심/자주 사용되는 메서드 위주) ----

    @Override
    public String getAuthType() {
        return authType;
    }

    @Override
    public Cookie[] getCookies() {
        return cookies;
    }

    /**
     * Date 헤더를 long(epoch millis)로 반환합니다. 파싱 실패 시 IllegalArgumentException.
     */
    @Override
    public long getDateHeader(String name) {
        String v = getHeader(name);
        if (v == null) return -1L;
        for (String p : DATE_FORMATS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(p, Locale.US);
                sdf.setTimeZone(GMT);
                return sdf.parse(v).getTime();
            } catch (ParseException ignore) { /* 다음 포맷 시도 */ }
        }
        throw new IllegalArgumentException("Cannot parse date header: " + v);
    }

    @Override
    public String getHeader(String name) {
        List<String> list = headers.get(name);
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        List<String> list = headers.get(name);
        return Collections.enumeration(list == null ? Collections.emptyList() : list);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(headers.keySet());
    }

    @Override
    public int getIntHeader(String name) {
        String v = getHeader(name);
        return (v == null) ? -1 : Integer.parseInt(v);
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public String getPathInfo() {
        return null;
    }            // 미사용(필요 시 확장)

    @Override
    public String getPathTranslated() {
        return null;
    }      // 미사용

    @Override
    public String getContextPath() {
        return contextPath;
    }

    @Override
    public String getQueryString() {
        return queryString;
    }

    @Override
    public String getRemoteUser() {
        return remoteUser;
    }

    @Override
    public boolean isUserInRole(String role) {
        return userRoles.contains(role);
    }

    @Override
    public Principal getUserPrincipal() {
        return userPrincipal;
    }

    @Override
    public String getRequestedSessionId() {
        return requestedSessionId;
    }

    @Override
    public String getRequestURI() {
        return requestURI;
    }

    /**
     * scheme://server[:port]/requestURI 형태의 URL을 조합해 반환합니다.
     */
    @Override
    public StringBuffer getRequestURL() {
        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://").append(getServerName());
        int port = getServerPort();
        if (port > 0 && (("http".equalsIgnoreCase(scheme) && port != 80) ||
                ("https".equalsIgnoreCase(scheme) && port != 443))) {
            sb.append(':').append(port);
        }
        if (requestURI != null) sb.append(requestURI);
        return new StringBuffer(sb.toString());
    }

    @Override
    public String getServletPath() {
        return servletPath;
    }

    /**
     * 세션을 반환합니다. 없으면 create=true일 때 SimpleHttpSession을 생성합니다.
     * (아주 단순한 세션 구현이므로 보안/클러스터링/타임아웃 등은 고려되지 않습니다)
     */
    @Override
    public HttpSession getSession(boolean create) {
        if (session == null && create) {
            session = new SimpleHttpSession(servletContext);
        }
        return session;
    }

    @Override
    public HttpSession getSession() {
        return getSession(true);
    }

    /**
     * 세션 ID를 새로 발급합니다(SimpleHttpSession 한정).
     */
    @Override
    public String changeSessionId() {
        HttpSession s = getSession(false);
        if (s == null) throw new IllegalStateException("No session");
        if (s instanceof SimpleHttpSession) return ((SimpleHttpSession) s).changeSessionId();
        return s.getId();
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        return requestedSessionIdValid;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return requestedSessionIdFromCookie;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return requestedSessionIdFromURL;
    }

    @Deprecated
    @Override
    public boolean isRequestedSessionIdFromUrl() {
        return isRequestedSessionIdFromURL();
    }

    // Attributes
    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(attributes.keySet());
    }

    @Override
    public void setAttribute(String name, Object o) {
        if (o == null) attributes.remove(name);
        else attributes.put(name, o);
    }

    @Override
    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    // Body/Encoding
    @Override
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    @Override
    public int getContentLength() {
        return content == null ? -1 : content.length;
    }

    @Override
    public long getContentLengthLong() {
        return getContentLength();
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    /**
     * 요청 바디를 읽기 위한 InputStream을 반환합니다.
     * 주의: 이 구현은 별도의 헬퍼 클래스 SimpleServletInputStream에 의존합니다.
     * (없다면 ServletInputStream을 상속한 래퍼를 직접 구현해 주세요)
     */
    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (inputStream != null) return inputStream;
        if (reader != null) throw new IllegalStateException("getReader() already called");
        InputStream src = (content == null) ? new ByteArrayInputStream(new byte[0])
                : new ByteArrayInputStream(content);

        // TODO: 프로젝트 내에 SimpleServletInputStream 구현이 있어야 합니다.
        //  - javax.servlet.ServletInputStream을 상속하고, read/isFinished/isReady/setReadListener 구현
        this.inputStream = new SimpleServletInputStream(src);
        return inputStream;
    }

    // Parameters
    @Override
    public String getParameter(String name) {
        String[] v = parameters.get(name);
        return (v == null || v.length == 0) ? null : v[0];
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(parameters.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        return parameters.get(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return Collections.unmodifiableMap(parameters);
    }

    // 프로토콜/호스트/포트
    @Override
    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    @Override
    public String getScheme() {
        return scheme;
    }

    /**
     * Host 헤더가 있으면 거기서 서버명을 파싱, 없으면 내부 serverName 반환.
     */
    @Override
    public String getServerName() {
        String host = getHeader("Host");
        if (host != null) {
            host = host.trim();
            if (host.startsWith("[")) { // IPv6
                int idx = host.indexOf(']');
                if (idx > -1) return host.substring(0, idx + 1);
            }
            int idx = host.indexOf(':');
            return (idx > 0) ? host.substring(0, idx) : host;
        }
        return serverName;
    }

    /**
     * Host 헤더에 포트가 있으면 파싱, 없으면 내부 serverPort 반환.
     */
    @Override
    public int getServerPort() {
        String host = getHeader("Host");
        if (host != null) {
            host = host.trim();
            int idx;
            if (host.startsWith("[")) {
                int close = host.indexOf(']');
                idx = host.indexOf(':', close);
            } else {
                idx = host.indexOf(':');
            }
            if (idx != -1) {
                return Integer.parseInt(host.substring(idx + 1));
            }
        }
        return serverPort;
    }

    /**
     * Reader로 바디를 읽습니다. getInputStream()과 동시 사용 불가.
     * 인코딩은 characterEncoding(없으면 ISO-8859-1) 적용.
     */
    @Override
    public BufferedReader getReader() throws IOException {
        if (reader != null) return reader;
        if (inputStream != null) throw new IllegalStateException("getInputStream() already called");
        Charset cs = (characterEncoding != null) ? Charset.forName(characterEncoding) : StandardCharsets.ISO_8859_1;
        InputStream src = (content == null) ? new ByteArrayInputStream(new byte[0])
                : new ByteArrayInputStream(content);
        this.reader = new BufferedReader(new InputStreamReader(src, cs));
        return reader;
    }

    // 원격/로컬 네트워크 정보
    @Override
    public String getRemoteAddr() {
        return remoteAddr;
    }

    @Override
    public String getRemoteHost() {
        return remoteHost;
    }

    @Override
    public Locale getLocale() {
        return locales.peekFirst();
    }

    @Override
    public Enumeration<Locale> getLocales() {
        return Collections.enumeration(locales);
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    /**
     * 경량 구현이므로 실제 forward/include는 미지원입니다.
     */
    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        return new RequestDispatcher() {
            @Override
            public void forward(ServletRequest request, ServletResponse response) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void include(ServletRequest request, ServletResponse response) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Deprecated
    @Override
    public String getRealPath(String path) {
        return servletContext.getRealPath(path);
    }

    @Override
    public int getRemotePort() {
        return remotePort;
    }

    @Override
    public String getLocalName() {
        return localName;
    }

    @Override
    public String getLocalAddr() {
        return localAddr;
    }

    @Override
    public int getLocalPort() {
        return localPort;
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    // ---- Async: 미지원 ----
    @Override
    public AsyncContext startAsync() {
        throw new IllegalStateException("Async not supported");
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) {
        throw new IllegalStateException("Async not supported");
    }

    @Override
    public boolean isAsyncStarted() {
        return asyncStarted;
    }

    @Override
    public boolean isAsyncSupported() {
        return asyncSupported;
    }

    @Override
    public AsyncContext getAsyncContext() {
        throw new IllegalStateException("Async not supported");
    }

    @Override
    public DispatcherType getDispatcherType() {
        return dispatcherType;
    }

    // ---- 보안 관련: 간소화/미지원 ----
    @Override
    public boolean authenticate(HttpServletResponse response) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void login(String username, String password) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void logout() {
        this.userPrincipal = null;
        this.remoteUser = null;
        this.authType = null;
    }

    // ---- 멀티파트/업그레이드: 미지원 ----
    @Override
    public Collection<Part> getParts() {
        return Collections.emptyList();
    }

    @Override
    public Part getPart(String name) {
        return null;
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) {
        throw new UnsupportedOperationException();
    }

    // 편의: 바디를 그대로 돌려받고 싶을 때 사용
    public byte[] getContentAsByteArray() {
        return this.content;
    }

    // ---- 매우 단순한 세션 구현 (운영용 세션 대체 X) ----
    static class SimpleHttpSession implements HttpSession {
        private final String id = UUID.randomUUID().toString();
        private final ServletContext sc;
        private final Map<String, Object> attrs = new HashMap<>();
        private final long creation = System.currentTimeMillis();
        private long lastAccess = creation;
        private int maxInactive = 1800;
        private boolean invalid = false;

        SimpleHttpSession(ServletContext sc) {
            this.sc = sc;
        }

        String changeSessionId() {
            return UUID.randomUUID().toString();
        }

        @Override
        public long getCreationTime() {
            return creation;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public long getLastAccessedTime() {
            return lastAccess;
        }

        @Override
        public ServletContext getServletContext() {
            return sc;
        }

        @Override
        public void setMaxInactiveInterval(int interval) {
            this.maxInactive = interval;
        }

        @Override
        public int getMaxInactiveInterval() {
            return maxInactive;
        }

        @Deprecated
        @Override
        public HttpSessionContext getSessionContext() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getAttribute(String name) {
            return attrs.get(name);
        }

        @Deprecated
        @Override
        public Object getValue(String name) {
            return getAttribute(name);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            return Collections.enumeration(attrs.keySet());
        }

        @Deprecated
        @Override
        public String[] getValueNames() {
            return attrs.keySet().toArray(new String[0]);
        }

        @Override
        public void setAttribute(String name, Object value) {
            attrs.put(name, value);
        }

        @Deprecated
        @Override
        public void putValue(String name, Object value) {
            setAttribute(name, value);
        }

        @Override
        public void removeAttribute(String name) {
            attrs.remove(name);
        }

        @Deprecated
        @Override
        public void removeValue(String name) {
            removeAttribute(name);
        }

        @Override
        public void invalidate() {
            invalid = true;
            attrs.clear();
        }

        @Override
        public boolean isNew() {
            return false;
        }
    }
}
