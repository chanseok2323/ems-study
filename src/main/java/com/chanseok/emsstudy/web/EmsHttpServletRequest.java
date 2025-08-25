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

    /** 기본 프로토콜 (HTTP/1.1) */
    public static final String DEFAULT_PROTOCOL = "HTTP/1.1";

    /** 기본 스킴 (http/https) */
    public static final String DEFAULT_SCHEME = "http";

    /** 기본 서버/내부 이름 */
    public static final String DEFAULT_SERVER = "localhost";

    /** 기본 서버/원격 포트 */
    public static final int DEFAULT_PORT = 80;

    /** 기본 원격 주소 */
    public static final String DEFAULT_REMOTE_ADDR = "127.0.0.1";

    /** 기본 원격 호스트 */
    public static final String DEFAULT_REMOTE_HOST = "localhost";

    /** GMT 타임존 */
    private static final TimeZone GMT = TimeZone.getTimeZone("GMT");

    /** HTTP 날짜 포맷들 */
    private static final String[] DATE_FORMATS = {
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd-MMM-yy HH:mm:ss zzz",
            "EEE MMM dd HH:mm:ss yyyy"
    };

    /** 서블릿 환경 */
    private final ServletContext servletContext;

    /** 요청 속성 맵 */
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    /** 쿼리/폼 파라미터 맵 (불변으로 반환됨) */
    private final Map<String, String[]> parameters = new LinkedHashMap<>();

    /** 헤더 맵 (대소문자 구분 없음, 다중 값 지원) */
    private final Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    /** 선호 로케일 목록 */
    private final Deque<Locale> locales = new ArrayDeque<>();

    /** HTTP 메서드 (예: "GET", "POST") */
    private String method;

    /** 프로토콜 (예: "HTTP/1.1") */
    private String protocol = DEFAULT_PROTOCOL;

    /** 스킴 (http/https) */
    private String scheme = DEFAULT_SCHEME;

    /** 서버 이름 (Host 헤더가 없을 때 getServerName()에 영향) */
    private String serverName = DEFAULT_SERVER;

    /** 서버 포트 (Host 헤더가 없을 때 getServerPort()에 영향) */
    private int serverPort = DEFAULT_PORT;

    /** 외부 주소 (X-Forwarded-For 헤더가 있으면 거기서 파싱, 없으면 DEFAULT_REMOTE_ADDR) */
    private String remoteAddr = DEFAULT_REMOTE_ADDR;

    /** 외부 호스트 (X-Forwarded-Host 헤더가 있으면 거기서 파싱, 없으면 DEFAULT_REMOTE_HOST) */
    private String remoteHost = DEFAULT_REMOTE_HOST;

    /** 외부 포트 (X-Forwarded-Port 헤더가 있으면 거기서 파싱, 없으면 DEFAULT_PORT) */
    private int remotePort = DEFAULT_PORT;

    /** 내부 이름 (Host 헤더가 없을 때 getServerName()에 영향) */
    private String localName = DEFAULT_SERVER;

    /** 내부 주소 (Host 헤더가 없을 때 getServerName()에 영향) */
    private String localAddr = DEFAULT_REMOTE_ADDR;

    /** 내부 포트 (Host 헤더가 없을 때 getServerPort()에 영향) */
    private int localPort = DEFAULT_PORT;

    /** 보안 요청 여부 (https면 true) */
    private boolean secure = false;

    /** 비동기 지원 여부 (미지원) */
    private boolean asyncSupported = false;

    /** 비동기 관련 상태 (미지원) */
    private boolean asyncStarted = false;

    /** 비동기 컨텍스트 (미지원) */
    private DispatcherType dispatcherType = DispatcherType.REQUEST;

    /** 컨텍스트 패스 (예: "/ems", 없으면 빈 문자열) */
    private String contextPath = "";

    /** 서블릿 패스 (예: "/process") */
    private String servletPath = "";

    /** 요청 URI (예: "/ems/process") */
    private String requestURI = "";

    /** 쿼리 스트링 (없으면 null) */
    private String queryString;

    /** 요청 바디 정보 */
    private String contentType;

    /** 문자 인코딩 */
    private String characterEncoding;

    /** 요청 바디 */
    private byte[] content;

    /** InputStream/Reader 캐시 */
    private ServletInputStream inputStream;

    /** Reader */
    private BufferedReader reader;

    /** 쿠키 */
    private Cookie[] cookies;

    /** 인증 타입 */
    private String authType;

    /** 원격 사용자 이름 */
    private String remoteUser;

    /** 사용자 프린시펄 */
    private Principal userPrincipal;

    /** 사용자 역할 */
    private final Set<String> userRoles = new HashSet<>();

    /** 세션 ID 요청값 (없으면 null) */
    private String requestedSessionId;

    /** 세션 컨텍스트 미사용 */
    private boolean requestedSessionIdValid = true;

    /** 쿠키에서 세션 ID를 가져왔는지 여부 */
    private boolean requestedSessionIdFromCookie = true;

    /** URL에서 세션 ID를 가져왔는지 여부 */
    private boolean requestedSessionIdFromURL = false;

    /** 단순 세션 구현 */
    private HttpSession session;

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

    /**
     * HTTP 메서드 설정
     * @param method
     */
    public void setMethod(String method) {
        this.method = method;
    }

    /**
     * 컨텍스트 패스 설정
     * @param contextPath
     */
    public void setContextPath(String contextPath) {
        this.contextPath = contextPath == null ? "" : contextPath;
    }

    /**
     * 서블릿 패스 설정
     * @param servletPath
     */
    public void setServletPath(String servletPath) {
        this.servletPath = servletPath == null ? "" : servletPath;
    }

    /**
     * 쿼리 스트링 설정
     * @param qs
     */
    public void setQueryString(String qs) {
        this.queryString = qs;
    }

    /**
     * 요청 URI 설정
     * @param requestURI
     */
    public void setRequestURI(String requestURI) {
        this.requestURI = requestURI;
    }

    /**
     * 서버 이름 설정
     * @param name
     */
    public void setServerName(String name) {
        this.serverName = name;
    }

    /**
     * 서버 포트 설정
     * @param port
     */
    public void setServerPort(int port) {
        this.serverPort = port;
    }

    /**
     * 보안 여부 설정 (https면 true)
     * @param scheme
     */
    public void setScheme(String scheme) {
        this.scheme = scheme;
        this.secure = "https".equalsIgnoreCase(scheme);
    }

    /**
     * 외부 주소 설정
     * @param addr
     */
    public void setRemoteAddr(String addr) {
        this.remoteAddr = addr;
    }

    /**
     * 외부 호스트 설정
     * @param host
     */
    public void setRemoteHost(String host) {
        this.remoteHost = host;
    }

    /**
     * 외부 포트 설정
     * @param port
     */
    public void setRemotePort(int port) {
        this.remotePort = port;
    }

    /**
     * 내부 이름 설정 (Host 헤더가 없을 때 getServerName()에 영향)
     * @param name
     */
    public void setLocalName(String name) {
        this.localName = name;
    }

    /**
     * 내부 주소 설정 (Host 헤더가 없을 때 getServerName()에 영향)
     * @param addr
     */
    public void setLocalAddr(String addr) {
        this.localAddr = addr;
    }

    /**
     * 내부 포트 설정 (Host 헤더가 없을 때 getServerPort()에 영향)
     * @param port
     */
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

    /**
     * 기존 파라미터를 대체합니다.
     * @param name 파라미터 이름
     * @param value 새 값
     */
    public void setParameter(String name, String value) {
        Objects.requireNonNull(name, "param name");
        parameters.put(name, new String[]{value});
    }

    /**
     * 기존 파라미터를 대체합니다.
     * @param name 파라미터 이름
     * @param values 새 값들
     */
    public void setParameter(String name, String... values) {
        Objects.requireNonNull(name, "param name");
        parameters.put(name, values);
    }

    /**
     * 기존 파라미터에 값을 추가합니다.
     * @param name  파라미터 이름
     * @param values 추가할 값들
     */
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

    /**
     * authType
     * @return authType
     */
    @Override
    public String getAuthType() {
        return authType;
    }

    /**
     * authType 설정
     * @return authType
     */
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

    /**
     * 단일 헤더 값을 반환합니다. 다중 값이면 첫 번째 값.
     * @param name 헤더 이름
     * @return 헤더 값 (없으면 null)
     */
    @Override
    public String getHeader(String name) {
        List<String> list = headers.get(name);
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    /**
     * 단일 헤더가 다중 값이면 모두 Enumeration으로 반환합니다.
     * @param name 헤더 이름
     * @return 헤더 값 Enumeration (없으면 빈 Enumeration)
     */
    @Override
    public Enumeration<String> getHeaders(String name) {
        List<String> list = headers.get(name);
        return Collections.enumeration(list == null ? Collections.emptyList() : list);
    }

    /**
     * 모든 헤더 이름을 Enumeration으로 반환합니다.
     * @return 헤더 이름 Enumeration
     */
    @Override
    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(headers.keySet());
    }

    /**
     * Int 헤더 반환 (없으면 -1, 파싱 실패 시 NumberFormatException)
     * @param name 헤더 이름
     * @return 헤더 값 또는 -1
     */
    @Override
    public int getIntHeader(String name) {
        String v = getHeader(name);
        return (v == null) ? -1 : Integer.parseInt(v);
    }

    /**
     * HTTP 메서드 (예: "GET", "POST")
     * @return
     */
    @Override
    public String getMethod() {
        return method;
    }

    /**
     * 미사용
     * @return
     */
    @Override
    public String getPathInfo() {
        return null;
    }

    /**
     * 미사용
     * @return
     */
    @Override
    public String getPathTranslated() {
        return null;
    }      // 미사용

    /**
     * 컨텍스트 패스를 반환합니다(없으면 빈 문자열).
     * @return contextPath
     */
    @Override
    public String getContextPath() {
        return contextPath;
    }

    /**
     * 쿼리 스트링 반환 (없으면 null)
     * @return queryString
     */
    @Override
    public String getQueryString() {
        return queryString;
    }

    /**
     * 사용자 이름 반환 (없으면 null)
     * @return remoteUser
     */
    @Override
    public String getRemoteUser() {
        return remoteUser;
    }

    /**
     * 사용자 이름 설정
     * @param role 역할
     * @return remoteUser
     */
    @Override
    public boolean isUserInRole(String role) {
        return userRoles.contains(role);
    }

    /**
     * 사용자 역할 추가
     * @return userPrincipal
     */
    @Override
    public Principal getUserPrincipal() {
        return userPrincipal;
    }

    /**
     * 세션 ID (없으면 null)
     * @return session ID
     */
    @Override
    public String getRequestedSessionId() {
        return requestedSessionId;
    }

    /**
     * 요청 URI를 반환합니다.
     * @return requestURI
     */
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

    /**
     * 서블릿 패스를 반환합니다.
     * @return servletPath
     */
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

    /**
     * 세션을 반환합니다. 없으면 새로 생성합니다.
     * @return HttpSession
     */
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

    /**
     * Deprecated: HttpSessionContext 미사용
     * @return requestedSessionIdValid
     */
    @Override
    public boolean isRequestedSessionIdValid() {
        return requestedSessionIdValid;
    }

    /**
     * 쿠키에서 세션 ID를 가져왔는지 여부
     * @return true if the session ID came in as a cookie; false otherwise
     */
    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return requestedSessionIdFromCookie;
    }

    /**
     * URL에서 세션 ID를 가져왔는지 여부
     * @return true if the session ID came in as part of the request URL; false otherwise
     */
    @Override
    public boolean isRequestedSessionIdFromURL() {
        return requestedSessionIdFromURL;
    }

    /**
     * Deprecated: URL 대신 URL 대문자 사용
     * @return
     */
    @Deprecated
    @Override
    public boolean isRequestedSessionIdFromUrl() {
        return isRequestedSessionIdFromURL();
    }

    /**
     * 속성 조회
     * @param name 속성 이름
     * @return 속성 값(없으면 null)
     */
    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    /**
     * 모든 속성 이름을 Enumeration으로 반환합니다.
     * @return 속성 이름 Enumeration
     */
    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(attributes.keySet());
    }

    /**
     * 속성 설정 (null이면 제거)
     * @param name 속성 이름
     * @param o   속성 값 (null이면 제거)
     */
    @Override
    public void setAttribute(String name, Object o) {
        if (o == null) attributes.remove(name);
        else attributes.put(name, o);
    }

    /**
     * 속성 제거
     * @param name 속성 이름
     */
    @Override
    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    /**
     * characterEncoding
     * @return characterEncoding
     */
    @Override
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    /**
     * Content-Length (int)
     * @return contentLength
     */
    @Override
    public int getContentLength() {
        return content == null ? -1 : content.length;
    }

    /**
     * Content-Length (long)
     * @return contentLength
     */
    @Override
    public long getContentLengthLong() {
        return getContentLength();
    }

    /**
     * Content-Type
     * @return contentType
     */
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

    /**
     * 단일 파라미터 값을 반환합니다. 다중 값이면 첫 번째 값.
     * @param name 파라미터 이름
     * @return 파라미터 값(없으면 null)
     */
    @Override
    public String getParameter(String name) {
        String[] v = parameters.get(name);
        return (v == null || v.length == 0) ? null : v[0];
    }

    /**
     * 모든 파라미터 이름을 Enumeration으로 반환합니다.
     * @return 파라미터 이름 Enumeration
     */
    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(parameters.keySet());
    }

    /**
     * 다중 파라미터 값을 배열로 반환합니다.
     * @param name 파라미터 이름
     * @return 파라미터 값 배열(없으면 null)
     */
    @Override
    public String[] getParameterValues(String name) {
        return parameters.get(name);
    }

    /**
     * 파라미터 맵(불변)을 반환합니다.
     * @return 파라미터 맵
     */
    @Override
    public Map<String, String[]> getParameterMap() {
        return Collections.unmodifiableMap(parameters);
    }

    /**
     * protocol
     * @return protocol
     */
    @Override
    public String getProtocol() {
        return protocol;
    }

    /**
     * protocol 설정
     * @param protocol
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * scheme
     * @return scheme
     */
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

    /**
     * remoteAddr
     * @return remoteAddr
     */
    @Override
    public String getRemoteAddr() {
        return remoteAddr;
    }

    /**
     * remoteHost
     * @return remoteHost
     */
    @Override
    public String getRemoteHost() {
        return remoteHost;
    }

    /**
     * locales 우선순위에 따른 첫 번째 로케일 반환
     * @return 최우선 로케일
     */
    @Override
    public Locale getLocale() {
        return locales.peekFirst();
    }

    /**
     * locales 우선순위에 따른 Enumeration 반환
     * @return locales Enumeration
     */
    @Override
    public Enumeration<Locale> getLocales() {
        return Collections.enumeration(locales);
    }

    /**
     * 보안 연결 여부 (https 여부)
     * @return secure
     */
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
            /**
             * 미지원
             * @param request
             * @param response
             */
            @Override
            public void forward(ServletRequest request, ServletResponse response) {
                throw new UnsupportedOperationException();
            }

            /**
             * 미지원
             * @param request
             * @param response
             */
            @Override
            public void include(ServletRequest request, ServletResponse response) {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * 실제 경로를 반환합니다. (서블릿 컨테이너가 없으면 null)
     * @param path 경로
     * @return 실제 경로 또는 null
     */
    @Deprecated
    @Override
    public String getRealPath(String path) {
        return servletContext.getRealPath(path);
    }

    /**
     * remotePort
     * @return remotePort
     */
    @Override
    public int getRemotePort() {
        return remotePort;
    }

    /**
     * localName
     * @return localName
     */
    @Override
    public String getLocalName() {
        return localName;
    }

    /**
     * localAddr
     * @return localAddr
     */
    @Override
    public String getLocalAddr() {
        return localAddr;
    }

    /**
     * localPort
     * @return localPort
     */
    @Override
    public int getLocalPort() {
        return localPort;
    }

    /**
     * 서블릿 컨텍스트를 반환합니다.
     * @return 서블릿 컨텍스트
     */
    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    /**
     * 미지원.
     * @return
     */
    @Override
    public AsyncContext startAsync() {
        throw new IllegalStateException("Async not supported");
    }

    /**
     * 미지원.
     * @param servletRequest
     * @param servletResponse
     * @return
     */
    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) {
        throw new IllegalStateException("Async not supported");
    }

    /**
     * 미지원.
     * @return always false
     */
    @Override
    public boolean isAsyncStarted() {
        return asyncStarted;
    }

    /**
     * 미지원.
     * @return always false
     */
    @Override
    public boolean isAsyncSupported() {
        return asyncSupported;
    }

    /**
     * 미지원.
     * @return
     */
    @Override
    public AsyncContext getAsyncContext() {
        throw new IllegalStateException("Async not supported");
    }

    /**
     * 디스패처 타입을 반환합니다(기본 REQUEST).
     * @return 디스패처 타입
     */
    @Override
    public DispatcherType getDispatcherType() {
        return dispatcherType;
    }

    /**
     * 미지원.
     * @param response
     * @return
     */
    @Override
    public boolean authenticate(HttpServletResponse response) {
        throw new UnsupportedOperationException();
    }

    /**
     * 미지원.
     * @param username 사용자 이름
     * @param password 비밀번호
     */
    @Override
    public void login(String username, String password) {
        throw new UnsupportedOperationException();
    }

    /**
     * 로그아웃: 인증 정보와 사용자 정보를 모두 제거합니다.
     */
    @Override
    public void logout() {
        this.userPrincipal = null;
        this.remoteUser = null;
        this.authType = null;
    }

    /**
     * 미지원.
     * @return
     */
    @Override
    public Collection<Part> getParts() {
        return Collections.emptyList();
    }

    /**
     * 미지원.
     * @param name 파트 이름
     * @return
     */
    @Override
    public Part getPart(String name) {
        return null;
    }

    /**
     * 업그레이드 미지원.
     * @param handlerClass 업그레이드 핸들러 클래스
     * @return 업그레이드 핸들러 인스턴스
     * @param <T> 핸들러 타입
     */
    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) {
        throw new UnsupportedOperationException();
    }

    /**
     * 요청 바디를 바이트 배열로 반환합니다(없으면 null).
     * @return 요청 바디(없으면 null)
     */
    public byte[] getContentAsByteArray() {
        return this.content;
    }

    /**
     * 매우 단순한 HttpSession 구현체.
     */
    static class SimpleHttpSession implements HttpSession {
        /** 세션 ID (UUID) */
        private final String id = UUID.randomUUID().toString();

        /** 서블릿 컨텍스트 */
        private final ServletContext sc;

        /** 세션 속성 맵 */
        private final Map<String, Object> attrs = new HashMap<>();

        /** 세션 생성 시간(밀리초) */
        private final long creation = System.currentTimeMillis();

        /** 마지막 접근 시간(밀리초) */
        private long lastAccess = creation;

        /** 최대 비활성 시간(초 단위, 기본 30분) */
        private int maxInactive = 1800;

        /** 세션 무효화 여부 */
        private boolean invalid = false;

        /**
         * 생성자
         * @param sc
         */
        SimpleHttpSession(ServletContext sc) {
            this.sc = sc;
        }

        /**
         * 세션 ID를 새로 발급합니다.
         * @return 새 세션 ID
         */
        String changeSessionId() {
            return UUID.randomUUID().toString();
        }

        /**
         * 세션 생성 시간을 반환합니다(밀리초).
         * @return 세션 생성 시간(밀리초)
         */
        @Override
        public long getCreationTime() {
            return creation;
        }


        /**
         * 세션 ID를 반환합니다.
         * @return 세션 ID
         */
        @Override
        public String getId() {
            return id;
        }

        /**
         * 마지막 접근 시간을 반환합니다.
         * @return 마지막 접근 시간(밀리초)
         */
        @Override
        public long getLastAccessedTime() {
            return lastAccess;
        }

        /**
         * 서블릿 컨텍스트를 반환합니다.
         * @return 서블릿 컨텍스트
         */
        @Override
        public ServletContext getServletContext() {
            return sc;
        }

        /**
         * 세션의 최대 비활성 시간(초 단위)을 설정합니다.
         * @param interval 초 단위 최대 비활성 시간
         */
        @Override
        public void setMaxInactiveInterval(int interval) {
            this.maxInactive = interval;
        }

        /**
         * 세션의 최대 비활성 시간(초 단위)을 반환합니다.
         * @return 초 단위 최대 비활성 시간
         */
        @Override
        public int getMaxInactiveInterval() {
            return maxInactive;
        }

        /**
         * Deprecated.
         * @return UnsupportedOperationException
         */
        @Deprecated
        @Override
        public HttpSessionContext getSessionContext() {
            throw new UnsupportedOperationException();
        }

        /**
         * 값을 반환합니다.
         * @param name 속성명
         * @return 속성값
         */
        @Override
        public Object getAttribute(String name) {
            return attrs.get(name);
        }

        /**
         * 값을 반환합니다(Deprecated).
         * @param name 속성명
         * @return 속성값
         */
        @Deprecated
        @Override
        public Object getValue(String name) {
            return getAttribute(name);
        }

        /**
         * 속성명 열거자를 반환합니다.
         * @return 속성명 열거자
         */
        @Override
        public Enumeration<String> getAttributeNames() {
            return Collections.enumeration(attrs.keySet());
        }

        /**
         * 속성명 배열을 반환합니다(Deprecated).
         * @return 속성명 배열
         */
        @Deprecated
        @Override
        public String[] getValueNames() {
            return attrs.keySet().toArray(new String[0]);
        }

        /**
         * 값을 설정합니다.
         * @param name 속성명
         * @param value 속성값
         */
        @Override
        public void setAttribute(String name, Object value) {
            attrs.put(name, value);
        }

        /**
         * 값을 설정합니다(Deprecated).
         * @param name 속성명
         * @param value 속성값
         */
        @Deprecated
        @Override
        public void putValue(String name, Object value) {
            setAttribute(name, value);
        }

        /**
         * 값을 제거합니다.
         * @param name 속성명
         */
        @Override
        public void removeAttribute(String name) {
            attrs.remove(name);
        }

        /**
         * 값을 제거합니다(Deprecated).
         * @param name 속성명
         */
        @Deprecated
        @Override
        public void removeValue(String name) {
            removeAttribute(name);
        }

        /**
         * 세션을 무효화합니다.
         */
        @Override
        public void invalidate() {
            invalid = true;
            attrs.clear();
        }

        /**
         * 세션이 무효화되었는지 여부.
         * @return 무효화되었으면 true
         */
        @Override
        public boolean isNew() {
            return false;
        }
    }
}
