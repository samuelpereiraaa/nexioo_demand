/**
 * Nexioo Demand — app.js (Dark Premium)
 *
 * Gerenciamento de interações ricas, acessibilidade por teclado,
 * comportamento de expansão no toque/mobile, fechamento por ESC,
 * recolhimento de colunas e modais.
 */

(function () {
    'use strict';

    /**
     * Gerenciador de Expansão e Interação das Seções de Demanda (<section>)
     */
    function iniciarInteracaoCartoes() {
        var cards = document.querySelectorAll('.kanban-card');
        if (!cards.length) return;

        function fecharTodosOsCartoes() {
            cards.forEach(function (card) {
                card.classList.remove('is-expanded');
            });
        }

        cards.forEach(function (card) {
            var id = card.getAttribute('data-id');
            if (!id) return;

            // Toque / Clique em dispositivos móveis ou alternância de expansão
            card.addEventListener('click', function (e) {
                // Não intercepta cliques em botões, links, forms, selects
                if (e.target.closest('a, button, select, form, input, label')) {
                    return;
                }

                var isTouchDevice = ('ontouchstart' in window) || (navigator.maxTouchPoints > 0);

                if (isTouchDevice) {
                    var jaExpandido = card.classList.contains('is-expanded');
                    fecharTodosOsCartoes();
                    if (!jaExpandido) {
                        card.classList.add('is-expanded');
                    } else {
                        window.location.href = '/demandas/' + id;
                    }
                } else {
                    window.location.href = '/demandas/' + id;
                }
            });

            // Teclado: Enter abre detalhes da demanda
            card.addEventListener('keydown', function (e) {
                if (e.key === 'Enter' && e.target === card && !e.target.closest('a, button, select, form')) {
                    e.preventDefault();
                    window.location.href = '/demandas/' + id;
                }
            });
        });

        // Fechamento de estado expandido com a tecla Escape
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') {
                fecharTodosOsCartoes();
            }
        });

        // Clicar fora de qualquer cartão fecha as seções expandidas no mobile
        document.addEventListener('click', function (e) {
            if (!e.target.closest('.kanban-card')) {
                fecharTodosOsCartoes();
            }
        });
    }

    /**
     * Gerenciador de Recolhimento / Expansão de Colunas (Collapse / Expand)
     */
    function iniciarRecolhimentoColunas() {
        document.querySelectorAll('[data-action="toggle-collapse-coluna"]').forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();
                var coluna = btn.closest('.kanban-column');
                if (coluna) {
                    coluna.classList.toggle('kanban-column--collapsed');
                }
            });
        });

        // Clicar no corpo da coluna recolhida também a expande
        document.querySelectorAll('.kanban-column').forEach(function (coluna) {
            coluna.addEventListener('click', function (e) {
                if (coluna.classList.contains('kanban-column--collapsed')) {
                    if (!e.target.closest('a, button, select, form')) {
                        coluna.classList.remove('kanban-column--collapsed');
                    }
                }
            });
        });
    }

    /**
     * Gerenciador do Modal Acessível de Nova Demanda
     */
    function iniciarModal() {
        var modal = document.getElementById('modal-nova-demanda');
        if (!modal) return;

        var lastActiveElement = null;

        function abrirModal(colunaDefault) {
            lastActiveElement = document.activeElement;
            modal.classList.add('is-open');
            modal.setAttribute('aria-hidden', 'false');

            if (colunaDefault) {
                var selectColuna = modal.querySelector('#modal-coluna');
                if (selectColuna) {
                    selectColuna.value = colunaDefault;
                }
            }

            var primeiroInput = modal.querySelector('#modal-titulo');
            if (primeiroInput) {
                setTimeout(function () {
                    primeiroInput.focus();
                }, 50);
            }
        }

        function fecharModal() {
            modal.classList.remove('is-open');
            modal.setAttribute('aria-hidden', 'true');
            if (lastActiveElement && typeof lastActiveElement.focus === 'function') {
                lastActiveElement.focus();
            }
        }

        // Gatilhos de abertura
        document.querySelectorAll('[data-modal-target="modal-nova-demanda"]').forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.preventDefault();
                var colunaDefault = btn.getAttribute('data-coluna-default');
                abrirModal(colunaDefault);
            });
        });

        // Gatilhos de fechamento (botão X e botão Cancelar)
        modal.querySelectorAll('[data-close-modal]').forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.preventDefault();
                fecharModal();
            });
        });

        // Fechar ao clicar no backdrop
        modal.addEventListener('click', function (e) {
            if (e.target === modal) {
                fecharModal();
            }
        });

        // Fechar modal com Escape
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && modal.classList.contains('is-open')) {
                fecharModal();
            }
        });
    }

    /**
     * Auto-dismiss suave para alertas toast
     */
    function iniciarAlertas() {
        document.querySelectorAll('.toast-alert').forEach(function (toast) {
            setTimeout(function () {
                toast.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
                toast.style.opacity = '0';
                toast.style.transform = 'translateY(8px)';
                setTimeout(function () {
                    if (toast && toast.parentNode) {
                        toast.parentNode.removeChild(toast);
                    }
                }, 300);
            }, 6000);
        });
    }

    /**
     * Prevenção de múltiplos envios em formulários
     */
    function iniciarProtecaoSubmit() {
        document.querySelectorAll('form').forEach(function (form) {
            if (form.classList.contains('card-move-form') ||
                form.classList.contains('card-complete-form') ||
                form.classList.contains('search-form')) {
                return;
            }
            form.addEventListener('submit', function () {
                var btnSubmit = form.querySelector('button[type="submit"]');
                if (btnSubmit && !btnSubmit.disabled) {
                    btnSubmit.disabled = true;
                    var originalText = btnSubmit.innerHTML;
                    btnSubmit.innerHTML = 'Salvando...';
                    setTimeout(function () {
                        btnSubmit.disabled = false;
                        btnSubmit.innerHTML = originalText;
                    }, 8000);
                }
            });
        });
    }

    // Inicialização ao carregar o DOM
    function inicializar() {
        iniciarInteracaoCartoes();
        iniciarRecolhimentoColunas();
        iniciarModal();
        iniciarAlertas();
        iniciarProtecaoSubmit();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', inicializar);
    } else {
        inicializar();
    }

}());
