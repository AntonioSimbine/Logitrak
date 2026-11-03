/* ============================================================
   Logitrack — Service Worker da aplicação do motorista
   App shell offline-first. As chamadas /api/** nunca são cacheadas.
   ============================================================ */
const VERSAO = 'logitrack-motorista-v2';
const SHELL = [
    './',
    './index.html',
    './styles.css',
    './manifest.webmanifest',
    '../js/config.js',
    './js/nucleo.js',
    './js/app.js',
    './js/registo.js',
    '../img/favicon.svg',
    '../img/icon-192.png',
    '../img/icon-512.png',
    '../img/icon-maskable-512.png'
];

self.addEventListener('install', function (evento) {
    evento.waitUntil(
        caches.open(VERSAO).then(function (cache) {
            return Promise.all(SHELL.map(function (url) {
                return cache.add(new Request(url, {cache: 'reload'})).catch(function () {
                    // Um recurso opcional em falta não deve bloquear a instalação.
                });
            }));
        }).then(function () {
            return self.skipWaiting();
        })
    );
});

self.addEventListener('activate', function (evento) {
    evento.waitUntil(
        caches.keys().then(function (chaves) {
            return Promise.all(chaves.map(function (chave) {
                if (chave !== VERSAO) {
                    return caches.delete(chave);
                }
                return null;
            }));
        }).then(function () {
            return self.clients.claim();
        })
    );
});

function eApi(url) {
    return url.pathname.indexOf('/api/') === 0
        || url.pathname.indexOf('/ws/') === 0
        || url.pathname.indexOf('/ws') === 0;
}

self.addEventListener('fetch', function (evento) {
    const pedido = evento.request;
    if (pedido.method !== 'GET') {
        return;
    }
    const url = new URL(pedido.url);
    if (url.origin !== self.location.origin || eApi(url)) {
        return;
    }

    if (pedido.mode === 'navigate') {
        evento.respondWith(
            fetch(pedido).then(function (resposta) {
                if (resposta && resposta.ok) {
                    const copia = resposta.clone();
                    caches.open(VERSAO).then(function (cache) {
                        cache.put('./index.html', copia);
                    });
                }
                return resposta;
            }).catch(function () {
                return caches.match('./index.html').then(function (resposta) {
                    return resposta || caches.match('./');
                });
            })
        );
        return;
    }

    evento.respondWith(
        caches.match(pedido).then(function (cacheado) {
            if (cacheado) {
                // Atualização em segundo plano, mantendo a resposta rápida.
                fetch(pedido).then(function (resposta) {
                    if (resposta && resposta.ok) {
                        caches.open(VERSAO).then(function (cache) {
                            cache.put(pedido, resposta);
                        });
                    }
                }).catch(function () {
                    /* offline: mantém a versão em cache */
                });
                return cacheado;
            }
            return fetch(pedido).then(function (resposta) {
                if (resposta && resposta.ok) {
                    const copia = resposta.clone();
                    caches.open(VERSAO).then(function (cache) {
                        cache.put(pedido, copia);
                    });
                }
                return resposta;
            });
        })
    );
});
