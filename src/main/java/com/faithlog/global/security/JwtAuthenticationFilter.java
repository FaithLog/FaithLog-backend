package com.faithlog.global.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtProvider jwtProvider;
	private final AccessTokenBlacklistChecker accessTokenBlacklistChecker;

	public JwtAuthenticationFilter(
		JwtProvider jwtProvider,
		AccessTokenBlacklistChecker accessTokenBlacklistChecker
	) {
		this.jwtProvider = jwtProvider;
		this.accessTokenBlacklistChecker = accessTokenBlacklistChecker;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		String token = resolveBearerToken(request);
		if (token != null) {
			authenticate(token);
		}

		filterChain.doFilter(request, response);
	}

	private String resolveBearerToken(HttpServletRequest request) {
		String authorization = request.getHeader(AUTHORIZATION_HEADER);
		if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
			return null;
		}
		return authorization.substring(BEARER_PREFIX.length());
	}

	private void authenticate(String token) {
		try {
			Claims claims = jwtProvider.parseAccessToken(token);
			String jti = claims.getId();
			if (jti == null || accessTokenBlacklistChecker.isBlacklisted(jti)) {
				return;
			}

			Long userId = claims.get("userId", Long.class);
			String role = claims.get("role", String.class);
			String sessionId = claims.get("sessionId", String.class);
			if (userId == null || role == null || sessionId == null) {
				return;
			}

			AuthenticatedUser principal = new AuthenticatedUser(userId, role, sessionId, jti);
			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
				principal,
				null,
				List.of(new SimpleGrantedAuthority("ROLE_" + role))
			);
			SecurityContextHolder.getContext().setAuthentication(authentication);
		} catch (RuntimeException ignored) {
			SecurityContextHolder.clearContext();
		}
	}
}
