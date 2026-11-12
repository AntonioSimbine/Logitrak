# Frontend Logitrack (PWA do motorista + sistema web) servido por Nginx no Railway.
FROM nginx:1.27-alpine

ENV PORT=8080 \
    NGINX_ENVSUBST_FILTER=PORT

COPY default.conf.template /etc/nginx/templates/default.conf.template
COPY . /usr/share/nginx/html

# O APK é descarregado da release estável do GitHub durante o build e passa a
# ser servido pelo próprio Railway (sem guardar binários no repositório).
ARG APK_URL=https://github.com/AntonioSimbine/Logitrak/releases/latest/download/Logitrack-Motorista.apk
RUN mkdir -p /usr/share/nginx/html/downloads \
    && (wget -q -O /usr/share/nginx/html/downloads/Logitrack-Motorista.apk "$APK_URL" \
        || echo "aviso: APK indisponivel no build")

EXPOSE 8080
