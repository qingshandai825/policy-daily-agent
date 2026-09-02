package com.itheima.policydailyagent.service;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * 共享的 Jsoup 连接工厂。
 *
 * <p>人民政府类站点常用国产 CA（如 CFCA、沃通、TrustAsia 等）签发证书，
 * 而 JVM 默认信任库（cacerts）往往不包含这些根证书，静态抓取会抛
 * {@code SSLHandshakeException: unable to find valid certification path to requested target}。
 * 本工厂使用「信任所有证书」的 SSL 上下文以兼容此类站点。</p>
 *
 * <p>说明：此处仅禁用证书链校验，并不绕过同源或访问控制；面向的是已知政府公开站点。
 * 如需更严格的安全策略，可改为加载系统/自定义信任库。</p>
 */
public final class PolicyHttpFetcher {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36";
    private static final SSLSocketFactory TRUST_ALL_SOCKET_FACTORY = buildTrustAllSocketFactory();

    private PolicyHttpFetcher() {
    }

    public static Connection connect(String url) {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(15000)
                .followRedirects(true)
                .maxBodySize(0)
                .sslSocketFactory(TRUST_ALL_SOCKET_FACTORY);
    }

    private static SSLSocketFactory buildTrustAllSocketFactory() {
        try {
            X509TrustManager trustAll = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            };
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{trustAll}, new SecureRandom());
            return context.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException("无法初始化宽松 SSL 上下文", e);
        }
    }
}
