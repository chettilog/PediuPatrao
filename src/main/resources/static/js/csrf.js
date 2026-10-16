// Envia o token CSRF em toda chamada fetch() que altera dados (POST, PUT, DELETE...).
// O token vem das <meta> do layout.html. Sem ele o Spring Security responde 403.
(function () {
    const fetchOriginal = window.fetch;

    function lerMeta(nome) {
        const el = document.querySelector('meta[name="' + nome + '"]');
        return el ? el.getAttribute('content') : null;
    }

    window.fetch = function (url, opcoes) {
        opcoes = opcoes || {};
        const metodo = (opcoes.method || 'GET').toUpperCase();
        if (metodo !== 'GET' && metodo !== 'HEAD') {
            const token = lerMeta('_csrf');
            const cabecalho = lerMeta('_csrf_header');
            if (token && cabecalho) {
                const headers = new Headers(opcoes.headers || {});
                headers.set(cabecalho, token);
                opcoes.headers = headers;
            }
        }
        return fetchOriginal(url, opcoes);
    };
})();
