FROM debian:bookworm-slim

WORKDIR /app

COPY target/rinha-netty-native /app/rinha-app

RUN chmod +x /app/rinha-app

EXPOSE 8080

ENTRYPOINT ["/app/rinha-app"]

# Opcional: Se você quiser um usuário não-root por segurança (recomendado para produção)
# RUN useradd -ms /bin/bash appuser
# USER appuser
# ENTRYPOINT ["/app/rinha-app"]