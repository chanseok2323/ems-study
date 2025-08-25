package com.chanseok.emsstudy.web;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * SimpleServletInputStream
 * 일반 {@link InputStream}을 서블릿 API에서 요구하는 {@link ServletInputStream}로 감싸기 위한 경량 어댑터.
 */
public class SimpleServletInputStream extends ServletInputStream {
    /** 실제 데이터를 읽을 대상 스트림 */
    private final InputStream delegate;

    /** EOF에 도달했는지 여부( read() 가 -1을 반환한 이후 true ) */
    private boolean finished = false;

    /**
     * @param delegate 감쌀 대상 InputStream (null 금지)
     */
    public SimpleServletInputStream(InputStream delegate) {
        this.delegate = delegate;
    }

    /**
     * 한 바이트를 읽는다. EOF(-1)면 finished 플래그를 true로 전환한다.
     */
    @Override
    public int read() throws IOException {
        int r = delegate.read();
        if (r == -1) {
            finished = true;  // 더 이상 읽을 데이터가 없음을 표시
        }
        return r;
    }

    /**
     * 스트림이 모두 소진되었는지 여부.
     * - {@link #read()}가 한 번이라도 -1을 반환하면 true.
     */
    @Override
    public boolean isFinished() {
        return finished;
    }

    /**
     * 현재 구현은 동기 블로킹이므로 항상 true를 반환.
     * - 논블로킹/Async I/O를 사용한다면 실제 준비 상태를 반영해야 함.
     */
    @Override
    public boolean isReady() {
        return true;
    }

    /**
     * 논블로킹/Async I/O 미지원.
     * - 필요 시 논블로킹 처리로 확장하고, 내부 버퍼/콜백 호출 로직을 구현해야 한다.
     */
    @Override
    public void setReadListener(ReadListener readListener) {
        // non-async impl: not supported
        throw new UnsupportedOperationException("ReadListener is not supported in this implementation.");
    }
}
