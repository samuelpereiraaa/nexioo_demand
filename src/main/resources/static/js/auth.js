/**
 * Nexioo Demand — auth.js (Supabase Native Auth Integration)
 *
 * Módulo para gerenciamento de autenticação via Supabase:
 * - signUp(email, password)
 * - signIn(email, password)
 * - signOut()
 * - onAuthStateChange(callback)
 * - Gestão automática de JWT e persistência em localStorage.
 */

(function () {
    'use strict';

    var SUPABASE_URL = window.SUPABASE_URL || 'https://bbxeajlvzajcjkdzhmcz.supabase.co';
    var SUPABASE_ANON_KEY = window.SUPABASE_ANON_KEY || 'sb_publishable_Ekx7QfVbyHtXOjFEtPFGAg_v5KLHPQA';


    var supabaseClient = null;

    function initSupabase() {
        if (typeof window.supabase !== 'undefined' && window.supabase.createClient) {
            supabaseClient = window.supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
                auth: {
                    persistSession: true,
                    autoRefreshToken: true,
                    detectSessionInUrl: true,
                    storage: window.localStorage
                }
            });
            console.log('[Supabase Auth] Cliente inicializado com sucesso.');
            bindAuthListeners();
        } else {
            console.warn('[Supabase Auth] SDK do Supabase não encontrado na janela.');
        }
    }

    /**
     * Cadastro de novo usuário via Supabase Auth.
     */
    function signUp(email, password) {
        if (!supabaseClient) initSupabase();
        if (!supabaseClient) return Promise.reject(new Error('Supabase client não inicializado.'));

        return supabaseClient.auth.signUp({
            email: email,
            password: password
        }).then(function (response) {
            if (response.error) throw response.error;
            return response.data;
        });
    }

    /**
     * Login de usuário via Supabase Auth (email e senha).
     */
    function signIn(email, password) {
        if (!supabaseClient) initSupabase();
        if (!supabaseClient) return Promise.reject(new Error('Supabase client não inicializado.'));

        return supabaseClient.auth.signInWithPassword({
            email: email,
            password: password
        }).then(function (response) {
            if (response.error) throw response.error;
            return response.data;
        });
    }

    /**
     * Logout do usuário ativo.
     */
    function signOut() {
        if (!supabaseClient) initSupabase();
        if (!supabaseClient) return Promise.resolve();

        return supabaseClient.auth.signOut().then(function (response) {
            if (response && response.error) throw response.error;
            atualizarInterfaceUsuario(null);
            return true;
        });
    }

    /**
     * Listener para monitorar alterações no estado de autenticação.
     */
    function onAuthStateChange(callback) {
        if (!supabaseClient) initSupabase();
        if (!supabaseClient || !supabaseClient.auth) return;

        supabaseClient.auth.onAuthStateChange(function (event, session) {
            console.log('[Supabase Auth State]:', event, session ? session.user.email : 'Sem sessão');
            atualizarInterfaceUsuario(session ? session.user : null);
            if (typeof callback === 'function') {
                callback(event, session);
            }
        });
    }

    /**
     * Retorna o usuário logado atualmente.
     */
    function getCurrentUser() {
        if (!supabaseClient) return null;
        return supabaseClient.auth.getUser();
    }

    /**
     * Atualiza a interface do usuário com base no estado da sessão.
     */
    function atualizarInterfaceUsuario(user) {
        var avatarEls = document.querySelectorAll('.user-avatar span');
        var authBtn = document.getElementById('btn-topbar-auth');

        if (user && user.email) {
            var iniciais = 'SP';
            var emailPrefix = user.email.split('@')[0];
            if (user.user_metadata && user.user_metadata.full_name) {
                var parts = user.user_metadata.full_name.trim().split(/\s+/);
                iniciais = parts.length === 1 ? parts[0].charAt(0).toUpperCase() : (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
            } else if (emailPrefix) {
                var p = emailPrefix.replace(/[._-]/g, ' ').trim().split(/\s+/);
                iniciais = p.length === 1 ? p[0].charAt(0).toUpperCase() : (p[0].charAt(0) + p[p.length - 1].charAt(0)).toUpperCase();
            }
            avatarEls.forEach(function (el) {
                el.textContent = iniciais;
            });
            var avatarWrappers = document.querySelectorAll('.user-avatar-wrapper');
            avatarWrappers.forEach(function(w) {
                w.title = 'Usuário logado: ' + user.email;
            });
            if (authBtn) {
                authBtn.textContent = 'Sair';
                authBtn.setAttribute('data-logged', 'true');
            }
        } else {
            if (authBtn) {
                authBtn.textContent = 'Sair';
                authBtn.setAttribute('data-logged', 'true');
            }
        }

    }


    function bindAuthListeners() {
        if (!supabaseClient) return;

        // Escuta inicial de estado
        onAuthStateChange(function (event, session) {
            // Callback opcional de controle
        });

        // Event listener no botão da topbar
        document.addEventListener('click', function (e) {
            var btn = e.target.closest('#btn-topbar-auth') || (e.target && e.target.id === 'btn-topbar-auth' ? e.target : null);
            if (btn) {
                e.preventDefault();
                if (typeof signOut === 'function') {
                    try { signOut(); } catch (err) {}
                }
                try {
                    // Limpa chaves do Supabase e da aplicação Nexioo sem apagar dados alheios
                    Object.keys(localStorage).forEach(function (k) {
                        if (k.startsWith('sb-') || k.startsWith('nexioo:')) {
                            localStorage.removeItem(k);
                        }
                    });
                    Object.keys(sessionStorage).forEach(function (k) {
                        if (k.startsWith('sb-') || k.startsWith('nexioo:')) {
                            sessionStorage.removeItem(k);
                        }
                    });
                } catch (err) {}

                // Obtém token CSRF do cookie ou meta tag
                var csrfToken = '';
                var match = document.cookie.match(new RegExp('(^|;\\s*)XSRF-TOKEN=([^;]*)'));
                if (match) {
                    csrfToken = decodeURIComponent(match[2]);
                }
                if (!csrfToken) {
                    var metaEl = document.querySelector('meta[name="_csrf"]');
                    if (metaEl) csrfToken = metaEl.getAttribute('content');
                }

                // Executa logout via POST seguro
                var form = document.createElement('form');
                form.method = 'POST';
                form.action = '/logout';
                if (csrfToken) {
                    var inputCsrf = document.createElement('input');
                    inputCsrf.type = 'hidden';
                    inputCsrf.name = '_csrf';
                    inputCsrf.value = csrfToken;
                    form.appendChild(inputCsrf);
                }
                document.body.appendChild(form);
                form.submit();
            }
        });
    }


    // Inicialização ao carregar a página
    document.addEventListener('DOMContentLoaded', function () {
        initSupabase();
    });

    // Exposição global
    window.supabaseAuth = {
        signUp: signUp,
        signIn: signIn,
        signOut: signOut,
        onAuthStateChange: onAuthStateChange,
        getCurrentUser: getCurrentUser
    };

})();
