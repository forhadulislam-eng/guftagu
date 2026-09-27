package com.guftagu.platform.security.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "guftagu.security.jwt")
public class JwtProperties {

    private String secret;
    private String issuer = "guftagu";
    private Duration accessTokenTtl = Duration.ofMinutes(10);

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer
                + ", accessTokenTtl=" + accessTokenTtl
                + ", secret=[PROTECTED]]";
    }
}
