package jease.cms.web.filter.gzip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Predicate;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dynatrace.hash4j.hashing.Hashing;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;

public class GZIPResponseStream extends ServletOutputStream {
    private static final Logger LOGGER = LoggerFactory.getLogger(GZIPResponseStream.class);

    private final ByteArrayOutputStream baos;
    private final int baosBufSize;
    private boolean closed;
    private final HttpServletResponse response;
    private final ServletOutputStream output;
    private Predicate<String> stopGZIP;
    private String cacheKey;
    private final int gzipMaxCache;
    private final int gzipMinCacheFileSize;
    private final int gzipMaxCacheFileSize;

    public GZIPResponseStream(HttpServletResponse response, String cacheKey,
            int gzipMaxCache, int gzipMinCacheFileSize, int gzipMaxCacheFileSize) throws IOException {
        super();
        closed = false;
        this.response = response;
        this.output = response.getOutputStream();
        int sz = response.getBufferSize();
        this.baosBufSize = sz < 8192 ? 8192 : sz;
        LOGGER.info("baosBufSize: {} | response buffer size: {}", this.baosBufSize, sz);
        this.baos = new ByteArrayOutputStream(this.baosBufSize);
        this.cacheKey = cacheKey;
        this.gzipMaxCache = gzipMaxCache;
        this.gzipMinCacheFileSize = gzipMinCacheFileSize;
        this.gzipMaxCacheFileSize = gzipMaxCacheFileSize;
    }

    private static final int MEDIUM_OUT_BUF_SZ = 65536;
    private static final int LARGE_OUT_BUF_SZ = 262144;

    private static class CachedItem {
        public final int origLength;
        public final long origHash;
        public final byte[] zippedBytes;

        public CachedItem(int origLength, long origHash, byte[] zippedBytes) {
            this.origLength = origLength;
            this.origHash = origHash;
            this.zippedBytes = zippedBytes;
        }
    }

    private static Cache<String, CachedItem> cacheZipped;

    @Override
    public void close() throws IOException {
        if (closed) {
            throw new IOException("This output stream has already been closed");
        }

        byte[] bytes = baos.toByteArray();
        if (bytes.length > 1024) {
            Long bytesHash = null;
            final String contentType = response.getContentType();
            final String contentEncoding = response.getHeader("Content-Encoding");
            if (contentEncoding == null || !contentEncoding.contains("gzip")) {
                if (stopGZIP == null || !stopGZIP.test(contentType)) {
                    final boolean shouldCache = gzipMaxCache > 0 &&
                            bytes.length >= gzipMinCacheFileSize && bytes.length <= gzipMaxCacheFileSize;
                    boolean doneBytes = false;
                    if (shouldCache) {
                        if (cacheZipped == null) {
                            cacheZipped = Caffeine.newBuilder()
                                    .maximumWeight(gzipMaxCache)
                                    .weigher((String key, CachedItem item) -> item.zippedBytes.length)
                                    .build();
                        }
                        CachedItem i = cacheZipped.getIfPresent(cacheKey);
                        if (i != null) {
                            boolean invalid = i.origLength != bytes.length;
                            if (!invalid) {
                                bytesHash = Hashing.xxh3_64().hashBytesToLong(bytes);
                                invalid = bytesHash.longValue() != i.origHash;
                            }
                            if (invalid) {
                                i = null;
                                cacheZipped.invalidate(cacheKey);
                                LOGGER.info("cacheZipped invalidated: {}", cacheKey);
                            }
                        }
                        if (i != null) {
                            bytes = i.zippedBytes;
                            doneBytes = true;
                            LOGGER.info("cacheZipped provided: {}", cacheKey);
                        }
                    }
                    if (!doneBytes) {
                        int outBufSize = baosBufSize;
                        if (bytes.length > outBufSize) {
                            outBufSize = bytes.length <= MEDIUM_OUT_BUF_SZ ? MEDIUM_OUT_BUF_SZ : LARGE_OUT_BUF_SZ;
                        }
                        ByteArrayOutputStream newBaos = new ByteArrayOutputStream(outBufSize);
                        GZIPOutputStream gzipstream = new GZIPOutputStream(newBaos, outBufSize, false/*syncFlush*/);
                        gzipstream.write(bytes);
                        gzipstream.finish();
                        byte[] zippedBytes = newBaos.toByteArray();

                        if (shouldCache) {
                            if (bytesHash == null) bytesHash = Hashing.xxh3_64().hashBytesToLong(bytes);
                            cacheZipped.put(cacheKey, new CachedItem(bytes.length, bytesHash.longValue(), zippedBytes));
                            LOGGER.info("cacheZipped put: {}", cacheKey);
                            LOGGER.info("cacheZipped stats: {}", cacheZipped.stats().toString());
                        }
                        bytes = zippedBytes;
                    }
                    response.setHeader("Content-Length", Integer.toString(bytes.length));
                    response.setHeader("Content-Encoding", "gzip");
                }
            }
        }
        output.write(bytes);
        output.flush();
        output.close();
        closed = true;
    }

    @Override
    public void flush() throws IOException {
        if (closed) {
            throw new IOException("Cannot flush a closed output stream");
        }
        baos.flush();
    }

    @Override
    public void write(int b) throws IOException {
        if (closed) {
            throw new IOException("Cannot write to a closed output stream");
        }
        baos.write((byte) b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("Cannot write to a closed output stream");
        }
        baos.write(b, off, len);
    }

    public boolean closed() {
        return this.closed;
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
    }

    public Predicate<String> getStopGZIP() {
        return stopGZIP;
    }

    public void setStopGZIP(Predicate<String> stopGZIP) {
        this.stopGZIP = stopGZIP;
    }
}
