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
                var parts = user.user_metadata.full_name.trim().split(' ');
                iniciais = (parts[0].charAt(0) + (parts.length > 1 ? parts[parts.length - 1].charAt(0) : '')).toUpperCase();
            } else if (emailPrefix) {
                if (emailPrefix.indexOf('.') !== -1 || emailPrefix.indexOf('_') !== -1) {
                    var p = emailPrefix.split(/[._-]/);
                    iniciais = (p[0].charAt(0) + (p[1] ? p[1].charAt(0) : '')).toUpperCase();
                } else {
                    iniciais = emailPrefix.substring(0, 2).toUpperCase();
                }
            }
            avatarEls.forEach(function (el) {
                el.textContent = iniciais;
            });
            var avatarWrappers = document.querySelectorAll('.user-avatar-wrapper');
            avatarWrappers.forEach(function(w) {
                w.title = 'Usuário logado: ' + user.email;
            });
            if (authBtn) {
                authBtn.textContent = 'Sair (' + user.email.split('@')[0] + ')';
                authBtn.setAttribute('data-logged', 'true');
            }
        } else {
            avatarEls.forEach(function (el) {
                el.textContent = 'SP';
            });
            if (authBtn) {
                authBtn.textContent = 'Entrar';
                authBtn.removeAttribute('data-logged');
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
            if (e.target && e.target.id === 'btn-topbar-auth') {
                if (e.target.hasAttribute('data-logged')) {
                    signOut().then(function() {
                        if (window.exibirToast) window.exibirToast('Sessão encerrada com sucesso.', 'sucesso');
                    });
                } else {
                    if (window.abrirModalAuth) window.abrirModalAuth();
                }
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
