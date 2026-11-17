package com.xiaoyu.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    private final SecretKey secret;
    private final long expiration;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration-ms:7200000}") long expiration) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 64) {
            throw new IllegalArgumentException("JWT secret 至少需要 64 字节，以支持 HS512");
        }
        this.secret = Keys.hmacShaKeyFor(keyBytes);
        this.expiration = expiration;
    }

    //生成token:存入id,和角色
    public String generateToken(Long userid,Integer role) {
        Date now = new Date();
        Date expireTime = new Date(now.getTime() + expiration);
        String token = Jwts.builder()
                //设置头信息
                .setHeaderParam("typ", "JWT")
                //设置主体
                .setSubject("buxixi")
                //存放用户id和角色
                .claim("userId", userid)
                .claim("role", role)
                //设置签发时间
                .setIssuedAt(now)
                //设置过期时间
                .setExpiration(expireTime)
                //设置加密方式
                .signWith(SignatureAlgorithm.HS512, secret)
                //创建token
                .compact();
      return token;
    }
    public Claims parseToken(String token) {
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }

}
