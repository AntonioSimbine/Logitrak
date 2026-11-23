# Frontend Logitrack (sistema web + APK do motorista) servido por Nginx no Railway.
FROM nginx:1.27-alpine

ENV PORT=8080 \
    NGINX_ENVSUBST_FILTER=PORT

COPY default.conf.template /etc/nginx/templates/default.conf.template
COPY . /usr/share/nginx/html

# APK da app do motorista. Se já existir uma cópia local em frontend/downloads,
# usa-a (útil quando não há release do GitHub disponível). Caso contrário,
# tenta descarregar da release estável do GitHub.
ARG APK_URL=https://github.com/AntonioSimbine/Logitrak/releases/latest/download/Logitrack-Motorista.apk
# Alterar este valor a cada publicação de APK para invalidar a camada do Docker.
ARG APK_CACHE_BUST=20261123f
RUN echo "APK build $APK_CACHE_BUST" >/dev/null \
    && mkdir -p /usr/share/nginx/html/downloads \
    && if [ -s /usr/share/nginx/html/downloads/Logitrack-Motorista.apk ]; then \
         echo "a usar APK local"; \
       else \
         (wget -q -O /usr/share/nginx/html/downloads/Logitrack-Motorista.apk "$APK_URL" \
           || echo "aviso: APK indisponivel no build"); \
       fi

EXPOSE 8080
