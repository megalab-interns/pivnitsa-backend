package kg.megalab.pivnitsabackend.security;

import jakarta.persistence.QueryTimeoutException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kg.megalab.pivnitsabackend.entity.Staff;
import kg.megalab.pivnitsabackend.repository.StaffRepository;
import kg.megalab.pivnitsabackend.exception.InvalidTokenException;
import kg.megalab.pivnitsabackend.exception.TokenBlacklistUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import io.jsonwebtoken.JwtException;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final StaffRepository staffRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        try {
            final String jti = jwtService.extractJti(token);
            final String phone = jwtService.extractPhone(token);
            final String scope = jwtService.extractScope(token);

            if (scope.equals("STAFF") || scope.equals("OWNER")) {
                boolean stillActive = staffRepository.findByEmail(phone).map(Staff::isActive).orElse(false);

                if (!stillActive) {
                    throw new InvalidTokenException("Аккаунт деактивирован");
                }
            }

            if (phone != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                if (tokenBlacklistService.isBlacklisted(jti)) {
                    throw new InvalidTokenException("Невалидный или просроченный токен");
                }

                if (jwtService.isTokenValid(token, phone)) {
                    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + scope));

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(phone, null, authorities);

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

        } catch (InvalidTokenException e) {
            log.warn("Ошибка валидации токена: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            handlerExceptionResolver.resolveException(request, response, null, e);
            return;                                                     // ответ уже сформирован, дальше не идём
        } catch (QueryTimeoutException | RedisConnectionFailureException e) {
            log.error("Redis недоступен при запросе {}: {}", request.getRequestURI(), e.getMessage(), e);
            SecurityContextHolder.clearContext();

            handlerExceptionResolver.resolveException(request, response, null, new TokenBlacklistUnavailableException("База данных токенов недоступна"));
            return;                                                     // то же самое
        } catch (JwtException e) {
            log.warn("Невалидный или повреждённый JWT токен для {}: {}", request.getRequestURI(), e.getMessage());
            SecurityContextHolder.clearContext();
            handlerExceptionResolver.resolveException(request, response, null, new InvalidTokenException("Невалидный или повреждённый токен"));
            return;                                                     // то же самое
        } catch (Exception e) {
            log.error("Непредвиденная ошибка в фильтре для {}: {}", request.getRequestURI(), e.getMessage(), e);
            SecurityContextHolder.clearContext();
            handlerExceptionResolver.resolveException(request, response, null, e);
            return;                                                     // то же самое
        }

        filterChain.doFilter(request, response);                        // сюда доходим, только если с токеном всё в порядке
    }
}

