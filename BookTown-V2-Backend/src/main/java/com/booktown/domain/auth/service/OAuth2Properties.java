package com.booktown.domain.auth.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "booktown.oauth2")
public class OAuth2Properties {

    private String successRedirectUri;
    private String failureRedirectUri;
    private Map<String, Provider> providers = new HashMap<>();

    @Getter
    @Setter
    public static class Provider {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private String authorizationUri;
        private String tokenUri;
        private String userInfoUri;
        private String scope;
    }
}
