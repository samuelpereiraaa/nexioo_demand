/**
 * Nexioo Demand — app.js (Dark Premium)
 *
 * Gerenciamento de interações ricas, acessibilidade por teclado,
 * modal de detalhes da demanda sobreposto ao quadro com sincronização em tempo real,
 * etiquetas, prazos, checklists, membros, acompanhamento e histórico de atividades.
 */

(function () {
    'use strict';

    var currentDetailId = null;
    var lastActiveCard = null;

    /**
     * Atualização dos contadores de demandas em uma coluna no DOM.
     */
    function atualizarContadoresColuna(colunaEl) {
        if (!colunaEl) return;
        var cardsList = colunaEl.querySelector('.column-cards-list');
        if (!cardsList) return;

        var count = cardsList.querySelectorAll('.kanban-card').length;
        var countSpan = colunaEl.querySelector('.column-count');
        var collapsedCountSpan = colunaEl.querySelector('.collapsed-count');
        var emptyState = colunaEl.querySelector('.column-empty-state');

        if (countSpan) countSpan.textContent = count;
        if (collapsedCountSpan) collapsedCountSpan.textContent = count;

        if (emptyState) {
            emptyState.style.display = count === 0 ? 'block' : 'none';
        }
    }

    /**
     * Move o elemento HTML do cartão para a nova coluna no DOM sem recarregar.
     */
    function moverCardDom(demandaId, novaColunaId) {
        var card = document.getElementById('card-' + demandaId);
        if (!card) return;

        var colunaOrigem = card.closest('.kanban-column');
        var colunaDestino = document.getElementById('coluna-' + String(novaColunaId).toLowerCase());

        if (colunaDestino) {
            var cardsListDestino = colunaDestino.querySelector('.column-cards-list');
            if (cardsListDestino && card.parentNode !== cardsListDestino) {
                cardsListDestino.appendChild(card);
                card.setAttribute('data-coluna', novaColunaId);

                var moveSelect = card.querySelector('.card-move-select');
                if (moveSelect) {
                    moveSelect.value = novaColunaId;
                }

                if (colunaOrigem) atualizarContadoresColuna(colunaOrigem);
                atualizarContadoresColuna(colunaDestino);
            }
        }
    }

    /**
     * Atualizar dados textuais no cartão da demanda no DOM.
     */
    function atualizarDadosCardDom(demandaId, titulo, descricao, prioridade, responsavel) {
        var card = document.getElementById('card-' + demandaId);
        if (!card) return;

        if (titulo) {
            var titleEl = card.querySelector('.card-title-text');
            if (titleEl) titleEl.textContent = titulo;
            card.setAttribute('data-titulo', titulo);
        }

        if (descricao !== undefined) {
            var descEl = card.querySelector('.card-desc');
            var expandableContent = card.querySelector('.card-expandable-content');
            if (descEl) {
                descEl.textContent = descricao;
                descEl.style.display = descricao ? 'block' : 'none';
            } else if (descricao && expandableContent) {
                var p = document.createElement('p');
                p.className = 'card-desc';
                p.textContent = descricao;
                expandableContent.appendChild(p);
            }
        }

        if (prioridade) {
            var pLower = prioridade.toLowerCase();
            var strip = card.querySelector('.card-priority-strip');
            if (strip) {
                strip.className = 'card-priority-strip priority-strip--' + pLower;
            }
            var tag = card.querySelector('.priority-tag');
            if (tag) {
                tag.className = 'priority-tag tag--' + pLower;
                tag.textContent = prioridade.charAt(0).toUpperCase() + prioridade.slice(1).toLowerCase();
            }
        }

        if (responsavel !== undefined) {
            var avatar = card.querySelector('.card-assignee-avatar');
            if (avatar) {
                var inc = "ND";
                if (responsavel && responsavel.trim()) {
                    var partes = responsavel.trim().split(/\s+/);
                    if (partes.length === 1) {
                        inc = partes[0].substring(0, Math.min(2, partes[0].length())).toUpperCase();
                    } else {
                        inc = (partes[0].charAt(0) + partes[partes.length - 1].charAt(0)).toUpperCase();
                    }
                }
                avatar.querySelector('span').textContent = inc;
                avatar.setAttribute('title', 'Responsável: ' + (responsavel || 'Não atribuído'));
            }
        }
    }

    /**
     * Exibe notificação toast de feedback.
     */
    function exibirToast(mensagem, tipo) {
        var container = document.querySelector('.alerts-container');
        if (!container) {
            container = document.createElement('div');
            container.className = 'alerts-container';
            document.body.appendChild(container);
        }

        var toast = document.createElement('div');
        toast.className = 'toast-alert ' + (tipo === 'erro' ? 'toast--error' : 'toast--success');
        toast.innerHTML = '<span class="toast-message">' + mensagem + '</span>' +
                          '<button type="button" class="toast-close" onclick="this.parentNode.remove()">✕</button>';
        container.appendChild(toast);

        setTimeout(function () {
            toast.style.opacity = '0';
            toast.style.transform = 'translateY(8px)';
            setTimeout(function () { if (toast.parentNode) toast.parentNode.removeChild(toast); }, 300);
        }, 4500);
    }

    /**
     * Envia requisição AJAX e atualiza o conteúdo do modal.
     */
    function atualizarConteudoModalAjax(url, method, params, mensagemSucesso) {
        if (!currentDetailId) return;

        var dialog = document.getElementById('modal-detail-dialog-content');
        if (!dialog) return;

        var options = {
            method: method || 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
        };
        if (params) {
            options.body = params.toString();
        }

        fetch(url, options)
            .then(function (res) {
                if (!res.ok) throw new Error('Ocorreu um erro na requisição.');
                return res.text();
            })
            .then(function (html) {
                dialog.innerHTML = html;
                if (mensagemSucesso) exibirToast(mensagemSucesso, 'sucesso');
            })
            .catch(function (err) {
                exibirToast(err.message || 'Erro ao atualizar demanda.', 'erro');
            });
    }

    /**
     * Gerenciador do Modal de Detalhes da Demanda
     */
    function iniciarModalDetalhes() {
        var backdrop = document.getElementById('modal-detalhe-demanda');
        var dialog = document.getElementById('modal-detail-dialog-content');
        if (!backdrop || !dialog) return;

        window.abrirModalDetalheDemandas = function (id) {
            if (!id) return;
            currentDetailId = id;
            lastActiveCard = document.activeElement;

            document.body.classList.add('modal-open');
            backdrop.classList.add('is-open');
            backdrop.setAttribute('aria-hidden', 'false');

            dialog.innerHTML = '<div style="padding: 40px; text-align: center; color: var(--color-text-secondary);">' +
                               'Carregando detalhes...' +
                               '</div>';

            fetch('/demandas/' + id + '/modal')
                .then(function (res) {
                    if (!res.ok) throw new Error('Não foi possível carregar os detalhes.');
                    return res.text();
                })
                .then(function (html) {
                    dialog.innerHTML = html;
                    var titleInput = dialog.querySelector('#modal-input-titulo');
                    if (titleInput) setTimeout(function () { titleInput.focus(); }, 60);
                })
                .catch(function (err) {
                    exibirToast(err.message, 'erro');
                    window.fecharModalDetalheDemandas();
                });
        };

        window.fecharModalDetalheDemandas = function () {
            backdrop.classList.remove('is-open');
            backdrop.setAttribute('aria-hidden', 'true');
            document.body.classList.remove('modal-open');
            dialog.innerHTML = '';
            currentDetailId = null;

            if (lastActiveCard && typeof lastActiveCard.focus === 'function') {
                lastActiveCard.focus();
            }
        };

        // Fechar no botão X ou backdrop
        backdrop.addEventListener('click', function (e) {
            if (e.target.closest('[data-close-modal-detail]') || e.target === backdrop) {
                e.preventDefault();
                window.fecharModalDetalheDemandas();
            }
        });

        // Fechar na tecla Escape
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && backdrop.classList.contains('is-open')) {
                e.preventDefault();
                window.fecharModalDetalheDemandas();
            }
        });

        // Fechar popovers ao clicar fora
        document.addEventListener('click', function (e) {
            if (!e.target.closest('.popover-wrapper')) {
                document.querySelectorAll('.popover-menu').forEach(function (pop) {
                    pop.classList.add('is-hidden');
                });
            }
        });

        // Controles de Popovers
        window.togglePopover = function (id) {
            var target = document.getElementById(id);
            if (!target) return;
            var isHidden = target.classList.contains('is-hidden');
            document.querySelectorAll('.popover-menu').forEach(function (pop) {
                pop.classList.add('is-hidden');
            });
            if (isHidden) {
                target.classList.remove('is-hidden');
            }
        };

        window.openPopoverMenu = function (id) {
            window.togglePopover(id);
        };

        // Funções de Ação no Modal

        window.salvarTituloModal = function (titulo) {
            if (!currentDetailId || !titulo) return;
            var params = new URLSearchParams();
            params.append('titulo', titulo.trim());
            atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/titulo', 'POST', params, 'Título atualizado.');
            atualizarDadosCardDom(currentDetailId, titulo);
        };

        window.salvarDescricaoModal = function () {
            if (!currentDetailId) return;
            var textarea = dialog.querySelector('#modal-textarea-descricao');
            var desc = textarea ? textarea.value : '';
            var params = new URLSearchParams();
            params.append('descricao', desc);
            atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/descricao', 'POST', params, 'Descrição salva com sucesso.');
            atualizarDadosCardDom(currentDetailId, undefined, desc);
        };

        window.restaurarDescricaoModal = function () {
            if (currentDetailId) {
                fetch('/demandas/' + currentDetailId + '/modal')
                    .then(function (res) { return res.text(); })
                    .then(function (html) { dialog.innerHTML = html; });
            }
        };

        window.inserirFormatacao = function (tipo) {
            var textarea = dialog.querySelector('#modal-textarea-descricao');
            if (!textarea) return;
            var start = textarea.selectionStart;
            var end = textarea.selectionEnd;
            var val = textarea.value;
            var selected = val.substring(start, end);
            var replacement = '';

            if (tipo === 'bold') replacement = '**' + (selected || 'texto') + '**';
            else if (tipo === 'italic') replacement = '*' + (selected || 'texto') + '*';
            else if (tipo === 'list') replacement = '\n- ' + (selected || 'item');
            else if (tipo === 'link') replacement = '[' + (selected || 'link') + '](url)';

            textarea.value = val.substring(0, start) + replacement + val.substring(end);
            textarea.focus();
        };

        window.salvarCamposModal = function () {
            if (!currentDetailId) return;
            var selectPrioridade = dialog.querySelector('#modal-prop-prioridade');
            var params = new URLSearchParams();
            if (selectPrioridade) params.append('prioridade', selectPrioridade.value);

            atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/modal-update', 'POST', params, 'Prioridade atualizada.');
            if (selectPrioridade) {
                atualizarDadosCardDom(currentDetailId, undefined, undefined, selectPrioridade.value);
            }
        };

        window.salvarColunaModal = function (form) {
            if (!currentDetailId || !form) return;
            var select = form.querySelector('select[name="coluna"]');
            var novaColuna = select ? select.value : null;

            var params = new URLSearchParams(new FormData(form));
            fetch(form.action, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString()
            })
            .then(function () {
                if (novaColuna) moverCardDom(currentDetailId, novaColuna);
                return fetch('/demandas/' + currentDetailId + '/modal');
            })
            .then(function (res) { return res.text(); })
            .then(function (html) {
                dialog.innerHTML = html;
                exibirToast('Coluna atualizada!', 'sucesso');
            });
        };

        window.toggleConcluidoModal = function (form) {
            if (!currentDetailId || !form) return;
            fetch(form.action, { method: 'POST' })
            .then(function () {
                return fetch('/demandas/' + currentDetailId + '/modal');
            })
            .then(function (res) { return res.text(); })
            .then(function (html) {
                dialog.innerHTML = html;
                var selectColuna = dialog.querySelector('#modal-detail-coluna-select');
                if (selectColuna) moverCardDom(currentDetailId, selectColuna.value);
                exibirToast('Status de conclusão alterado!', 'sucesso');
            });
        };

        // Etiquetas
        window.adicionarEtiquetaModal = function (nome, corHex) {
            var params = new URLSearchParams();
            params.append('nome', nome);
            params.append('corHex', corHex);
            atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/etiquetas/adicionar', 'POST', params, 'Etiqueta adicionada.');
        };

        window.removerEtiquetaModal = function (etiquetaId) {
            var params = new URLSearchParams();
            params.append('etiquetaId', etiquetaId);
            atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/etiquetas/remover', 'POST', params, 'Etiqueta removida.');
        };

        // Datas
        window.salvarPrazoModal = function (prazo) {
            var params = new URLSearchParams();
            if (prazo) params.append('prazo', prazo);
            atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/prazo', 'POST', params, 'Prazo atualizado.');
        };

        // Checklists
        window.adicionarChecklistModal = function () {
            var input = dialog.querySelector('#input-titulo-checklist');
            var titulo = input ? input.value : 'Checklist';
            var params = new URLSearchParams();
            params.append('titulo', titulo);
            atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/checklists/adicionar', 'POST', params, 'Checklist criada.');
        };

        window.removerChecklistModal = function (checklistId) {
            var params = new URLSearchParams();
            params.append('checklistId', checklistId);
            atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/checklists/remover', 'POST', params, 'Checklist removida.');
        };

        window.adicionarItemChecklistModal = function (inputEl, checklistId) {
            if (!inputEl || !inputEl.value.trim()) return;
            var params = new URLSearchParams();
            params.append('checklistId', checklistId);
            params.append('texto', inputEl.value.trim());
            atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/checklists/itens/adicionar', 'POST', params, 'Item adicionado.');
        };

        window.toggleChecklistItemModal = function (checklistId, itemId) {
            var params = new URLSearchParams();
            params.append('checklistId', checklistId);
            params.append('itemId', itemId);
            atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/checklists/itens/toggle', 'POST', params);
        };

        window.removerChecklistItemModal = function (checklistId, itemId) {
            var params = new URLSearchParams();
            params.append('checklistId', checklistId);
            params.append('itemId', itemId);
            atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/checklists/itens/remover', 'POST', params, 'Item removido.');
        };

        // Membros
        window.adicionarMembroModal = function (membro) {
            var params = new URLSearchParams();
            params.append('membro', membro);
            atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/membros/adicionar', 'POST', params, 'Membro adicionado.');
        };

        window.removerMembroModal = function (membro) {
            var params = new URLSearchParams();
            params.append('membro', membro);
            atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/membros/remover', 'POST', params, 'Membro removido.');
        };

        // Acompanhamento
        window.toggleAcompanharModal = function () {
            atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/acompanhar', 'POST', null, 'Preferência de acompanhamento alterada.');
        };

        // Comentários
        window.validarBotaoComentario = function (textarea) {
            var btn = dialog.querySelector('#btn-salvar-comentario');
            if (btn) {
                btn.disabled = !(textarea && textarea.value.trim().length > 0);
            }
        };

        window.enviarComentarioModal = function () {
            if (!currentDetailId) return;
            var input = dialog.querySelector('#input-novo-comentario');
            if (!input || !input.value.trim()) return;

            var params = new URLSearchParams();
            params.append('texto', input.value.trim());
            atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/comentar', 'POST', params, 'Comentário adicionado!');
        };

        // Alternar Visualização de Detalhes na Atividade
        window.toggleMostrarDetalhesAtividade = function () {
            var timeline = dialog.querySelector('#modal-activity-list');
            var btn = dialog.querySelector('#btn-toggle-detalhes-atividade');
            if (!timeline || !btn) return;

            var ocultaSistema = timeline.classList.toggle('hide-system');
            btn.textContent = ocultaSistema ? 'Ocultar Detalhes' : 'Mostrar Detalhes';
        };

        // Exclusão
        window.confirmarExclusaoModal = function () {
            if (!currentDetailId) return;
            if (confirm('Tem certeza que deseja excluir esta demanda? Esta ação não pode ser desfeita.')) {
                var idParaExcluir = currentDetailId;
                fetch('/demandas/' + idParaExcluir + '/api', { method: 'DELETE' })
                .then(function (res) {
                    if (res.ok) {
                        var card = document.getElementById('card-' + idParaExcluir);
                        if (card) {
                            var col = card.closest('.kanban-column');
                            card.remove();
                            if (col) atualizarContadoresColuna(col);
                        }
                        window.fecharModalDetalheDemandas();
                        exibirToast('Demanda excluída com sucesso.', 'sucesso');
                    } else {
                        exibirToast('Erro ao excluir a demanda.', 'erro');
                    }
                });
            }
        };
    }

    /**
     * Interação dos Cartões no Quadro
     */
    function iniciarInteracaoCartoes() {
        var cards = document.querySelectorAll('.kanban-card');
        if (!cards.length) return;

        document.addEventListener('click', function (e) {
            var card = e.target.closest('.kanban-card');
            if (card) {
                if (e.target.closest('a, button, select, form, input, label')) {
                    return;
                }
                var id = card.getAttribute('data-id');
                if (id && window.abrirModalDetalheDemandas) {
                    e.preventDefault();
                    window.abrirModalDetalheDemandas(id);
                }
            }
        });

        document.addEventListener('keydown', function (e) {
            var card = e.target.closest('.kanban-card');
            if (card && (e.key === 'Enter' || e.key === ' ') && !e.target.closest('a, button, select, form, input')) {
                e.preventDefault();
                var id = card.getAttribute('data-id');
                if (id && window.abrirModalDetalheDemandas) {
                    window.abrirModalDetalheDemandas(id);
                }
            }
        });
    }

    /**
     * Recolhimento de Colunas
     */
    function iniciarRecolhimentoColunas() {
        document.querySelectorAll('[data-action="toggle-collapse-coluna"]').forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();
                var coluna = btn.closest('.kanban-column');
                if (coluna) coluna.classList.toggle('kanban-column--collapsed');
            });
        });

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
     * Modal de Nova Demanda
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
                if (selectColuna) selectColuna.value = colunaDefault;
            }

            var primeiroInput = modal.querySelector('#modal-titulo');
            if (primeiroInput) setTimeout(function () { primeiroInput.focus(); }, 50);
        }

        function fecharModal() {
            modal.classList.remove('is-open');
            modal.setAttribute('aria-hidden', 'true');
            if (lastActiveElement && typeof lastActiveElement.focus === 'function') {
                lastActiveElement.focus();
            }
        }

        document.querySelectorAll('[data-modal-target="modal-nova-demanda"]').forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.preventDefault();
                abrirModal(btn.getAttribute('data-coluna-default'));
            });
        });

        modal.querySelectorAll('[data-close-modal]').forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.preventDefault();
                fecharModal();
            });
        });

        modal.addEventListener('click', function (e) {
            if (e.target === modal) fecharModal();
        });

        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && modal.classList.contains('is-open')) fecharModal();
        });
    }

    /**
     * Adição Inline de Lista
     */
    function iniciarAdicaoLista() {
        var triggerBtn = document.getElementById('btn-add-lista-trigger');
        var form = document.getElementById('form-add-lista');
        var input = document.getElementById('input-nome-lista');
        var cancelBtn = document.getElementById('btn-cancelar-add-lista');
        var errorMsg = document.getElementById('add-list-error-msg');
        var track = document.getElementById('board-columns-track');

        if (!triggerBtn || !form || !input) return;

        function mostrarFormulario() {
            triggerBtn.style.display = 'none';
            triggerBtn.setAttribute('aria-expanded', 'true');
            form.classList.remove('is-hidden');
            input.value = '';
            limparErro();
            setTimeout(function () { input.focus(); }, 50);
        }

        function ocultarFormulario() {
            form.classList.add('is-hidden');
            triggerBtn.style.display = 'flex';
            triggerBtn.setAttribute('aria-expanded', 'false');
            input.value = '';
            limparErro();
            triggerBtn.focus();
        }

        function mostrarErro(mensagem) {
            if (errorMsg) {
                errorMsg.textContent = mensagem;
                errorMsg.classList.add('is-visible');
            }
            input.classList.add('has-error');
            input.setAttribute('aria-invalid', 'true');
            input.focus();
        }

        function limparErro() {
            if (errorMsg) {
                errorMsg.textContent = '';
                errorMsg.classList.remove('is-visible');
            }
            input.classList.remove('has-error');
            input.removeAttribute('aria-invalid');
        }

        triggerBtn.addEventListener('click', function (e) {
            e.preventDefault();
            mostrarFormulario();
        });

        if (cancelBtn) {
            cancelBtn.addEventListener('click', function (e) {
                e.preventDefault();
                ocultarFormulario();
            });
        }

        input.addEventListener('input', function () {
            if (input.value.trim().length > 0) limparErro();
        });

        input.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') {
                e.preventDefault();
                e.stopPropagation();
                ocultarFormulario();
            }
        });

        form.addEventListener('submit', function (e) {
            var nome = input.value ? input.value.trim() : '';
            if (!nome) {
                e.preventDefault();
                mostrarErro('O nome da lista não pode ser vazio.');
                return false;
            }
            limparErro();
        });
    }

    function inicializar() {
        iniciarInteracaoCartoes();
        iniciarRecolhimentoColunas();
        iniciarModal();
        iniciarModalDetalhes();
        iniciarAdicaoLista();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', inicializar);
    } else {
        inicializar();
    }

}());
