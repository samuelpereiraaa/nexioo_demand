/**
 * Proteção CSRF do navegador para formulários e requisições AJAX de mesma origem.
 * O servidor continua sendo a autoridade responsável pela validação do token.
 */
(function () {
    'use strict';

    var SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS', 'TRACE'];
    var COOKIE_NAME = 'XSRF-TOKEN';
    var HEADER_NAME = 'X-CSRF-TOKEN';

    function readCookie(name) {
        var prefix = name + '=';
        var entries = document.cookie ? document.cookie.split(';') : [];

        for (var i = 0; i < entries.length; i++) {
            var entry = entries[i].trim();
            if (entry.indexOf(prefix) === 0) {
                try {
                    return decodeURIComponent(entry.substring(prefix.length));
                } catch (error) {
                    return entry.substring(prefix.length);
                }
            }
        }

        return '';
    }

    function getToken() {
        var cookieToken = readCookie(COOKIE_NAME);
        if (cookieToken) return cookieToken;

        var meta = document.querySelector('meta[name="_csrf"]');
        return meta && meta.content ? meta.content : '';
    }

    function isUnsafe(method) {
        return SAFE_METHODS.indexOf(String(method || 'GET').toUpperCase()) === -1;
    }

    function isSameOrigin(resource) {
        var target = typeof resource === 'string' || resource instanceof URL
            ? resource
            : resource && resource.url;

        try {
            return new URL(target || window.location.href, window.location.href).origin === window.location.origin;
        } catch (error) {
            return false;
        }
    }

    function protectFetch() {
        if (!window.fetch || window.fetch.__nexiooCsrfProtected) return;

        var originalFetch = window.fetch.bind(window);
        var protectedFetch = function (resource, options) {
            var requestOptions = options || {};
            var method = requestOptions.method || (resource && resource.method) || 'GET';

            if (!isUnsafe(method) || !isSameOrigin(resource)) {
                return originalFetch(resource, options);
            }

            var token = getToken();
            if (!token) {
                return Promise.reject(new Error('Token CSRF não foi disponibilizado pela página. Atualize a tela e tente novamente.'));
            }

            var securedOptions = Object.assign({}, requestOptions);
            securedOptions.headers = new Headers(
                requestOptions.headers || (resource instanceof Request ? resource.headers : undefined)
            );
            if (!securedOptions.headers.has(HEADER_NAME)) {
                securedOptions.headers.set(HEADER_NAME, token);
            }

            return originalFetch(resource, securedOptions);
        };

        protectedFetch.__nexiooCsrfProtected = true;
        window.fetch = protectedFetch;
    }

    function protectForm(form) {
        if (!form || !isUnsafe(form.method) || !isSameOrigin(form.action)) return;

        var token = getToken();
        if (!token) return;

        var input = form.querySelector('input[name="_csrf"]');
        if (!input) {
            input = document.createElement('input');
            input.type = 'hidden';
            input.name = '_csrf';
            form.appendChild(input);
        }
        input.value = token;
    }

    protectFetch();

    document.addEventListener('submit', function (event) {
        protectForm(event.target);
    }, true);

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('form').forEach(protectForm);
    });

    window.nexiooCsrf = {
        getToken: getToken,
        protectForm: protectForm
    };
})();
