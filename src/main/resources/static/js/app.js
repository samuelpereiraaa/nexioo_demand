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
    var boardScrollLeft = 0;
    var boardScrollTop = 0;
    var pageScrollTop = 0;

    function resolveUrl(url) {
        if (!url) return url;
        if (typeof url === 'string' && url.startsWith('/')) {
            return url;
        }
        return url;
    }




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

        if (countSpan) {
            countSpan.setAttribute('aria-label', 'Total de ' + count + ' demandas');
        }

        if (!emptyState && count === 0) {
            emptyState = document.createElement('div');
            emptyState.className = 'column-empty-state';
            emptyState.innerHTML = '<span class="empty-state-text">Nenhuma demanda</span>';
            cardsList.appendChild(emptyState);
        }

        if (emptyState) {
            emptyState.hidden = count !== 0;
            emptyState.style.display = count === 0 ? 'block' : 'none';
        }
    }

    /**
     * Reconsulta o fragmento Thymeleaf do cartão depois de cada mutação.
     * Assim prazo, etiquetas, checklist, membros, conclusão e lista usam a
     * mesma fonte de verdade do servidor.
     */
    function sincronizarCardDoServidor(demandaId) {
        return fetch('/demandas/' + demandaId + '/cartao')

            .then(function (res) {
                if (!res.ok) throw new Error('Não foi possível sincronizar o cartão.');
                return res.text();
            })
            .then(function (html) {
                var template = document.createElement('template');
                template.innerHTML = html.trim();
                var novoCard = template.content.querySelector('.kanban-card');
                if (!novoCard) throw new Error('Cartão atualizado inválido.');

                var colunaDestinoId = novoCard.getAttribute('data-coluna');
                var colunaDestino = document.getElementById('coluna-' + String(colunaDestinoId))
                    || document.getElementById('coluna-' + String(colunaDestinoId).toLowerCase())
                    || document.getElementById('coluna-' + String(colunaDestinoId).toUpperCase())
                    || document.querySelector('.kanban-column[data-id="' + String(colunaDestinoId) + '"]');
                var listaDestino = colunaDestino
                    ? colunaDestino.querySelector('.column-cards-list')
                    : (document.querySelector('.column-cards-list'));
                if (!listaDestino) throw new Error('Lista de destino não encontrada.');


                var cardAtual = document.getElementById('card-' + demandaId);
                var colunaOrigem = cardAtual ? cardAtual.closest('.kanban-column') : null;

                if (cardAtual && cardAtual.parentNode === listaDestino) {
                    cardAtual.replaceWith(novoCard);
                } else {
                    if (cardAtual) cardAtual.remove();
                    listaDestino.appendChild(novoCard);
                }


                if (colunaOrigem && colunaOrigem !== colunaDestino) {
                    atualizarContadoresColuna(colunaOrigem);
                }
                atualizarContadoresColuna(colunaDestino);
                return novoCard;
            });
    }
    window.sincronizarCardDoServidor = sincronizarCardDoServidor;


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

    var ultimasMensagensToast = {};

    function exibirToast(mensagem, tipo) {
        if (!mensagem) return;
        var agora = Date.now();
        if (ultimasMensagensToast[mensagem] && (agora - ultimasMensagensToast[mensagem]) < 2000) {
            return;
        }
        ultimasMensagensToast[mensagem] = agora;

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
        if (!currentDetailId) {
            var cardEl = document.querySelector('#modal-detail-dialog-content .modal-detail-card');
            if (cardEl && cardEl.getAttribute('data-id')) {
                currentDetailId = cardEl.getAttribute('data-id');
            }
        }
        if (!currentDetailId) return Promise.resolve();

        var dialog = document.getElementById('modal-detail-dialog-content');
        if (!dialog) return Promise.resolve();

        var options = {
            method: method || 'POST',
            headers: {
                'X-Requested-With': 'XMLHttpRequest'
            }
        };

        if (params instanceof FormData) {
            options.body = params;
        } else if (params) {
            options.headers['Content-Type'] = 'application/x-www-form-urlencoded';
            options.body = params.toString();
        }

        return fetch(url, options)
            .then(function (res) {
                if (!res.ok) {
                    return res.text().then(function (body) {
                        console.error('Falha na requisição AJAX:', {
                            metodo: method || 'POST',
                            url: url,
                            status: res.status,
                            resposta: body
                        });
                        throw new Error('Ocorreu um erro na requisição (' + res.status + ').');
                    });
                }
                return res.text();
            })

            .then(function (html) {
                var mainCol = dialog.querySelector('.modal-detail-main') || dialog.querySelector('.modal-detail-main-col');
                var currentMainScrollTop = mainCol ? mainCol.scrollTop : 0;
                var sidebarCol = dialog.querySelector('.modal-detail-sidebar') || dialog.querySelector('.modal-detail-sidebar-col');
                var currentSidebarScrollTop = sidebarCol ? sidebarCol.scrollTop : 0;
                var modalBody = dialog.querySelector('.modal-detail-body');
                var currentBodyScrollTop = modalBody ? modalBody.scrollTop : 0;

                // Preservar valores digitados que o usuário ainda não salvou
                var commentInput = dialog.querySelector('#input-novo-comentario');
                var pendingComment = commentInput ? commentInput.value : '';
                var isCommentAction = url.indexOf('/comentar') !== -1;

                var descInput = dialog.querySelector('#modal-textarea-descricao');
                var pendingDesc = descInput ? descInput.value : '';
                var isDescAction = url.indexOf('/descricao') !== -1;

                dialog.innerHTML = html;

                // Restaurar scroll de ambas as colunas
                var newMainCol = dialog.querySelector('.modal-detail-main') || dialog.querySelector('.modal-detail-main-col');
                if (newMainCol && currentMainScrollTop) {
                    newMainCol.scrollTop = currentMainScrollTop;
                }
                var newSidebarCol = dialog.querySelector('.modal-detail-sidebar') || dialog.querySelector('.modal-detail-sidebar-col');
                if (newSidebarCol && currentSidebarScrollTop) {
                    newSidebarCol.scrollTop = currentSidebarScrollTop;
                }
                var newModalBody = dialog.querySelector('.modal-detail-body');
                if (newModalBody && currentBodyScrollTop) {
                    newModalBody.scrollTop = currentBodyScrollTop;
                }


                // Restaurar comentário pendente se a ação não era de salvar comentário
                if (pendingComment && !isCommentAction) {
                    var newCommentInput = dialog.querySelector('#input-novo-comentario');
                    if (newCommentInput) {
                        newCommentInput.value = pendingComment;
                        window.validarBotaoComentario(newCommentInput);
                    }
                }

                // Restaurar descrição pendente se a ação não era de salvar descrição
                if (pendingDesc && !isDescAction) {
                    var newDescInput = dialog.querySelector('#modal-textarea-descricao');
                    if (newDescInput) {
                        newDescInput.value = pendingDesc;
                    }
                }

                return sincronizarCardDoServidor(currentDetailId);
            })

            .then(function () {
                if (mensagemSucesso) exibirToast(mensagemSucesso, 'sucesso');
            })
            .catch(function (err) {
                console.error('Falha ao atualizar conteúdo da demanda:', err);
                exibirToast(err.message || 'Erro ao atualizar demanda.', 'erro');
            });
    }


    /**
     * Gerenciador do Modal de Detalhes da Demanda
     */
    function iniciarModalDetalhes() {
        var backdrop = document.getElementById('modal-detalhe-demanda');
        var dialog = document.getElementById('modal-detail-dialog-content');

        window.abrirModalDetalheDemandas = function (target) {
            backdrop = document.getElementById('modal-detalhe-demanda');
            dialog = document.getElementById('modal-detail-dialog-content');
            var board = document.getElementById('conteudo-quadro');
            var boardTrack = document.getElementById('board-columns-track');

            if (!backdrop || !dialog) return;


            var id = target;
            var targetCard = null;
            if (target && typeof target === 'object') {
                targetCard = target.closest('.kanban-card');
                id = targetCard ? targetCard.getAttribute('data-id') : (target.getAttribute('data-id') || target.getAttribute('data-open-detail'));
            }
            if (!id || id === 'undefined' || id === 'null') return;
            currentDetailId = id;

            // Destacar o cartão ativo visualmente
            document.querySelectorAll('.kanban-card').forEach(function(c) { c.classList.remove('is-active'); });
            if (targetCard) targetCard.classList.add('is-active');

            lastActiveCard = target && typeof target.focus === 'function' ? target : (targetCard || document.activeElement);
            boardScrollLeft = boardTrack ? boardTrack.scrollLeft : 0;
            boardScrollTop = boardTrack ? boardTrack.scrollTop : 0;
            pageScrollTop = window.pageYOffset || document.documentElement.scrollTop || 0;

            document.body.classList.add('modal-open');
            if (board) board.setAttribute('inert', '');
            backdrop.classList.add('is-open');
            backdrop.setAttribute('aria-hidden', 'false');

            dialog.innerHTML = '<div style="padding: 40px; text-align: center; color: var(--color-text-secondary);">' +
                               'Carregando detalhes...' +
                               '</div>';

            fetch(resolveUrl('/demandas/' + id + '/modal'))
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
                    exibirToast(err && err.message ? err.message : 'Erro ao carregar os detalhes da demanda.', 'erro');
                    window.fecharModalDetalheDemandas();
                });
        };

        window.fecharModalDetalheDemandas = function () {
            var backdrop = document.getElementById('modal-detalhe-demanda');
            var dialog = document.getElementById('modal-detail-dialog-content');
            var board = document.getElementById('conteudo-quadro');
            var boardTrack = document.getElementById('board-columns-track');

            if (backdrop) {
                backdrop.classList.remove('is-open');
                backdrop.setAttribute('aria-hidden', 'true');
            }
            document.body.classList.remove('modal-open');
            if (board) board.removeAttribute('inert');
            if (dialog) dialog.innerHTML = '';
            document.querySelectorAll('.kanban-card').forEach(function(c) { c.classList.remove('is-active'); });
            currentDetailId = null;

            if (boardTrack) {
                boardTrack.scrollLeft = boardScrollLeft;
                boardTrack.scrollTop = boardScrollTop;
            }
            window.scrollTo(0, pageScrollTop);

            if (lastActiveCard && typeof lastActiveCard.focus === 'function') {
                lastActiveCard.focus();
            }
        };

        document.addEventListener('keydown', function (e) {
            var viewer = document.getElementById('image-viewer-lightbox');
            if (viewer && !viewer.classList.contains('is-hidden')) {
                if (e.key === 'Escape') {
                    e.preventDefault();
                    e.stopPropagation();
                    window.fecharVisualizadorImagem();
                    return;
                }
                if (e.key === 'ArrowLeft') {
                    e.preventDefault();
                    window.navegarVisualizadorImagem(-1);
                    return;
                }
                if (e.key === 'ArrowRight') {
                    e.preventDefault();
                    window.navegarVisualizadorImagem(1);
                    return;
                }
            }

            var backdrop = document.getElementById('modal-detalhe-demanda');
            var dialog = document.getElementById('modal-detail-dialog-content');

            if (e.key === 'Escape' && backdrop && backdrop.classList.contains('is-open')) {
                if (dialog) {
                    var popoverAberto = dialog.querySelector('.popover-menu:not(.is-hidden)');
                    if (popoverAberto) {
                        popoverAberto.classList.add('is-hidden');
                        return;
                    }
                }
                e.preventDefault();
                window.fecharModalDetalheDemandas();
                return;
            }

            if (e.key === 'Tab' && backdrop && backdrop.classList.contains('is-open') && dialog) {
                var focaveis = dialog.querySelectorAll(
                    'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href]'
                );
                if (!focaveis.length) return;
                var primeiro = focaveis[0];
                var ultimo = focaveis[focaveis.length - 1];
                if (e.shiftKey && document.activeElement === primeiro) {
                    e.preventDefault();
                    ultimo.focus();
                } else if (!e.shiftKey && document.activeElement === ultimo) {
                    e.preventDefault();
                    primeiro.focus();
                }
            }
        });

        document.addEventListener('click', function (e) {
            var backdrop = document.getElementById('modal-detalhe-demanda');
            if (backdrop && backdrop.classList.contains('is-open')) {
                if (e.target.closest('[data-close-modal-detail]') || e.target === backdrop) {
                    e.preventDefault();
                    window.fecharModalDetalheDemandas();
                }
            }
        });

        document.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && e.target && e.target.matches && e.target.matches('.chk-item-input')) {
                e.preventDefault();
                window.adicionarItemChecklistModal(
                    e.target,
                    e.target.getAttribute('data-checklist-id')
                );
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

        // Utilitário para obter o ID da demanda atualmente aberta no modal
        function obterDemandaIdAtual() {
            if (currentDetailId) return String(currentDetailId);
            var cardEl = document.querySelector('#modal-detail-dialog-content .modal-detail-card')
                || document.querySelector('.modal-detail-card');
            if (cardEl && cardEl.getAttribute('data-id')) {
                currentDetailId = cardEl.getAttribute('data-id');
                return String(currentDetailId);
            }
            var elWithData = document.querySelector('#modal-detail-dialog-content [data-demanda-id]')
                || document.querySelector('[data-demanda-id]');
            if (elWithData && elWithData.getAttribute('data-demanda-id')) {
                currentDetailId = elWithData.getAttribute('data-demanda-id');
                return String(currentDetailId);
            }
            return null;
        }
        window.obterDemandaIdAtual = obterDemandaIdAtual;

        // Funções de Ação no Modal

        window.salvarTituloModal = function (input) {
            var id = obterDemandaIdAtual();

            if (!id || !input) return;
            var titulo = input.value ? input.value.trim() : '';
            var original = input.getAttribute('data-original-value') || '';
            if (!titulo || titulo === original) return;
            input.setAttribute('data-original-value', titulo);
            var params = new URLSearchParams();
            params.append('titulo', titulo);
            return atualizarConteudoModalAjax(
                '/demandas/' + id + '/titulo',
                'POST',
                params,
                'Título atualizado.'
            );
        };

        window.abrirEditorDescricaoModal = function () {
            var wrapper = document.getElementById('editor-descricao-wrapper');
            var viewMode = document.getElementById('modal-desc-view-mode');
            var emptyPlaceholder = document.getElementById('modal-desc-empty-placeholder');
            var btnEditar = document.getElementById('btn-editar-descricao');
            var textarea = document.getElementById('modal-textarea-descricao');

            if (viewMode) viewMode.style.display = 'none';
            if (emptyPlaceholder) emptyPlaceholder.style.display = 'none';
            if (btnEditar) btnEditar.style.display = 'none';
            if (wrapper) {
                wrapper.style.display = 'flex';
                if (textarea) {
                    setTimeout(function () {
                        textarea.focus();
                        textarea.selectionStart = textarea.selectionEnd = textarea.value.length;
                    }, 30);
                }
            }
        };

        window.fecharEditorDescricaoModal = function () {
            var wrapper = document.getElementById('editor-descricao-wrapper');
            var viewMode = document.getElementById('modal-desc-view-mode');
            var emptyPlaceholder = document.getElementById('modal-desc-empty-placeholder');
            var btnEditar = document.getElementById('btn-editar-descricao');
            var textarea = document.getElementById('modal-textarea-descricao');

            if (textarea) {
                var original = textarea.getAttribute('data-original-value') || '';
                textarea.value = original;
            }

            if (wrapper) wrapper.style.display = 'none';

            var temTexto = textarea && textarea.value && textarea.value.trim().length > 0;
            if (temTexto) {
                if (viewMode) {
                    viewMode.textContent = textarea.value;
                    viewMode.style.display = '';
                }
                if (btnEditar) btnEditar.style.display = '';
                if (emptyPlaceholder) emptyPlaceholder.style.display = 'none';
            } else {
                if (emptyPlaceholder) emptyPlaceholder.style.display = '';
                if (viewMode) viewMode.style.display = 'none';
                if (btnEditar) btnEditar.style.display = 'none';
            }
        };

        window.restaurarDescricaoModal = function () {
            window.fecharEditorDescricaoModal();
        };

        window.salvarDescricaoModal = function (triggerBtn) {
            var id = (triggerBtn && triggerBtn.getAttribute('data-demanda-id')) || obterDemandaIdAtual();
            if (!id) {
                console.error('salvarDescricaoModal: ID da demanda não encontrado.');
                exibirToast('Não foi possível identificar o cartão.', 'erro');
                return Promise.reject(new Error('ID da demanda não encontrado'));
            }
            var textarea = document.getElementById('modal-textarea-descricao');
            if (!textarea) {
                console.error('salvarDescricaoModal: Campo #modal-textarea-descricao não encontrado.');
                return Promise.reject(new Error('Campo de descrição não encontrado'));
            }
            var desc = textarea.value;
            var params = new URLSearchParams();
            params.append('descricao', desc);
            return atualizarConteudoModalAjax(
                '/demandas/' + id + '/descricao',
                'POST',
                params,
                'Descrição salva com sucesso.'
            ).then(function () {
                var el = document.getElementById('modal-textarea-descricao');
                if (el) el.setAttribute('data-original-value', desc);
            });
        };


        window.inserirFormatacao = function (tipo) {
            var textarea = document.getElementById('modal-textarea-descricao');
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
            var id = obterDemandaIdAtual();
            if (!id) return;
            var selectPrioridade = document.getElementById('modal-prop-prioridade');
            var params = new URLSearchParams();
            if (selectPrioridade) params.append('prioridade', selectPrioridade.value);

            return atualizarConteudoModalAjax('/demandas/' + id + '/modal-update', 'POST', params, 'Prioridade atualizada.');
        };


        window.salvarColunaModal = function (form) {
            var id = obterDemandaIdAtual();
            if (!id || !form) return;
            var select = form.querySelector('select[name="coluna"]');
            var novaColuna = select ? select.value : null;
            if (!novaColuna) return;

            var params = new URLSearchParams(new FormData(form));
            return atualizarConteudoModalAjax(form.action, 'POST', params, 'Coluna atualizada!');
        };

        window.toggleConcluidoModal = function (form) {
            var id = obterDemandaIdAtual();
            if (!id || !form) return;
            return atualizarConteudoModalAjax(form.action, 'POST', null, 'Status de conclusão alterado!');
        };


        // ── Gestão Dinâmica de Etiquetas ──────────────────────────────────────────
        window.__corEtiquetaSelecionada = '#00E6A8';

        window.selecionarCorEtiqueta = function (cor, btn) {
            window.__corEtiquetaSelecionada = cor;
            var swatches = document.querySelectorAll('#container-cores-etiqueta .color-swatch');
            swatches.forEach(function (s) {
                s.style.border = '2px solid transparent';
                s.classList.remove('is-selected');
            });
            if (btn) {
                btn.style.border = '2px solid #fff';
                btn.classList.add('is-selected');
            }
            var picker = document.getElementById('input-nova-etiqueta-colorpicker');
            if (picker) picker.value = cor;
            var hexInput = document.getElementById('input-nova-etiqueta-cor-hex');
            if (hexInput) hexInput.value = cor.toUpperCase();
            window.atualizarPreviewEtiqueta();
        };

        window.selecionarCorPersonalizada = function (hex) {
            if (!hex) return;
            if (!hex.startsWith('#')) hex = '#' + hex;
            if (hex.length > 7) hex = hex.substring(0, 7);
            window.__corEtiquetaSelecionada = hex;

            var picker = document.getElementById('input-nova-etiqueta-colorpicker');
            if (picker && /^#[0-9A-Fa-f]{6}$/.test(hex)) picker.value = hex;
            var hexInput = document.getElementById('input-nova-etiqueta-cor-hex');
            if (hexInput && hexInput.value !== hex) hexInput.value = hex.toUpperCase();

            var swatches = document.querySelectorAll('#container-cores-etiqueta .color-swatch');
            swatches.forEach(function (s) {
                if (s.getAttribute('data-color') && s.getAttribute('data-color').toLowerCase() === hex.toLowerCase()) {
                    s.style.border = '2px solid #fff';
                    s.classList.add('is-selected');
                } else {
                    s.style.border = '2px solid transparent';
                    s.classList.remove('is-selected');
                }
            });
            window.atualizarPreviewEtiqueta();
        };

        window.atualizarPreviewEtiqueta = function () {
            var input = document.getElementById('input-nova-etiqueta-nome');
            var preview = document.getElementById('preview-nova-etiqueta');
            if (!preview) return;
            var nome = (input && input.value.trim()) ? input.value.trim() : 'Nome da etiqueta';
            var cor = window.__corEtiquetaSelecionada || '#00E6A8';
            preview.textContent = nome;
            preview.style.color = cor;
            preview.style.borderColor = cor;
            preview.style.backgroundColor = cor + '22';
        };

        window.salvarNovaEtiquetaModal = function () {
            var id = obterDemandaIdAtual();
            if (!id) {
                if (typeof exibirToast === 'function') exibirToast('Não foi possível identificar o cartão.', 'erro');
                return;
            }
            var inputNome = document.getElementById('input-nova-etiqueta-nome');
            if (!inputNome || !inputNome.value.trim()) {
                if (typeof exibirToast === 'function') exibirToast('Digite o nome da etiqueta.', 'erro');
                if (inputNome) inputNome.focus();
                return;
            }
            var nome = inputNome.value.trim();

            // Validação de duplicação no mesmo cartão
            var badges = document.querySelectorAll('.tags-badges-row .applied-tag-badge span');
            for (var i = 0; i < badges.length; i++) {
                if (badges[i].textContent.trim().toLowerCase() === nome.toLowerCase()) {
                    if (typeof exibirToast === 'function') exibirToast('Esta etiqueta já foi adicionada ao cartão.', 'erro');
                    return;
                }
            }

            var cor = window.__corEtiquetaSelecionada || '#00E6A8';
            if (!/^#[0-9A-Fa-f]{3,6}$/.test(cor)) {
                if (typeof exibirToast === 'function') exibirToast('Selecione uma cor hexadecimal válida.', 'erro');
                return;
            }

            var btnCriar = document.getElementById('btn-criar-etiqueta');
            if (btnCriar) {
                if (btnCriar.disabled) return;
                btnCriar.disabled = true;
                btnCriar.textContent = 'Criando...';
            }

            var params = new URLSearchParams();
            params.append('nome', nome);
            params.append('corHex', cor);

            return atualizarConteudoModalAjax('/demandas/' + id + '/etiquetas/adicionar', 'POST', params, 'Etiqueta criada com sucesso!')
                .then(function () {
                    window.fecharTodosPopovers();
                    if (inputNome) inputNome.value = '';
                    window.sincronizarCardDoServidor(id);
                })
                .catch(function (err) {
                    console.error('[Etiquetas] Erro ao criar etiqueta:', err);
                    if (typeof exibirToast === 'function') exibirToast('Erro ao criar etiqueta.', 'erro');
                })
                .finally(function () {
                    if (btnCriar) {
                        btnCriar.disabled = false;
                        btnCriar.textContent = 'Criar';
                    }
                });
        };

        window.removerEtiquetaModal = function (etiquetaId) {
            var id = obterDemandaIdAtual();
            if (!id || !etiquetaId) return;
            if (!confirm('Deseja realmente remover esta etiqueta do cartão?')) return;
            var params = new URLSearchParams();
            params.append('etiquetaId', etiquetaId);
            return atualizarConteudoModalAjax('/demandas/' + id + '/etiquetas/remover', 'POST', params, 'Etiqueta removida.')
                .then(function () {
                    window.sincronizarCardDoServidor(id);
                })
                .catch(function (err) {
                    console.error('[Etiquetas] Erro ao remover etiqueta:', err);
                    if (typeof exibirToast === 'function') exibirToast('Erro ao remover etiqueta.', 'erro');
                });
        };


        // Datas
        window.salvarPrazoModal = function (prazo) {
            var id = obterDemandaIdAtual();
            if (!id) return;
            var params = new URLSearchParams();
            if (prazo) params.append('prazo', prazo);
            atualizarConteudoModalAjax('/demandas/' + id + '/prazo', 'POST', params, 'Prazo atualizado.');
        };

        // Checklists
        window.adicionarChecklistModal = function () {
            var id = obterDemandaIdAtual();
            if (!id) return;
            var input = document.getElementById('input-titulo-checklist');
            var titulo = input ? input.value : 'Checklist';
            var params = new URLSearchParams();
            params.append('titulo', titulo);
            atualizarConteudoModalAjax('/demandas/' + id + '/checklists/adicionar', 'POST', params, 'Checklist criada.');
        };

        window.removerChecklistModal = function (checklistId) {
            var id = obterDemandaIdAtual();
            if (!id) return;
            var params = new URLSearchParams();
            params.append('checklistId', checklistId);
            atualizarConteudoModalAjax('/demandas/' + id + '/checklists/remover', 'POST', params, 'Checklist removida.');
        };

        window.renomearChecklistModal = function (checklistId, input) {
            var id = obterDemandaIdAtual();
            if (!id || !input) return;
            var titulo = input.value ? input.value.trim() : '';
            var original = input.getAttribute('data-original-value') || '';
            if (!titulo || titulo === original) return;
            var params = new URLSearchParams();
            params.append('checklistId', checklistId);
            params.append('titulo', titulo);
            return atualizarConteudoModalAjax(
                '/demandas/' + id + '/checklists/renomear',
                'POST',
                params,
                'Checklist atualizada.'
            );
        };

        window.adicionarItemChecklistModal = function (inputEl, checklistId) {
            var id = obterDemandaIdAtual();
            if (!id || !inputEl || !inputEl.value.trim()) return;
            var params = new URLSearchParams();
            params.append('checklistId', checklistId);
            params.append('texto', inputEl.value.trim());
            atualizarConteudoModalAjax('/demandas/' + id + '/checklists/itens/adicionar', 'POST', params, 'Item adicionado.');
        };

        window.toggleChecklistItemModal = function (checklistId, itemId) {
            var id = obterDemandaIdAtual();
            if (!id) return;
            var params = new URLSearchParams();
            params.append('checklistId', checklistId);
            params.append('itemId', itemId);
            atualizarConteudoModalAjax('/demandas/' + id + '/checklists/itens/toggle', 'POST', params);
        };

        window.removerChecklistItemModal = function (checklistId, itemId) {
            var id = obterDemandaIdAtual();
            if (!id) return;
            var params = new URLSearchParams();
            params.append('checklistId', checklistId);
            params.append('itemId', itemId);
            atualizarConteudoModalAjax('/demandas/' + id + '/checklists/itens/remover', 'POST', params, 'Item removido.');
        };

        window.atualizarChecklistItemModal = function (checklistId, itemId, input) {
            var id = obterDemandaIdAtual();
            if (!id || !input) return;
            var texto = input.value ? input.value.trim() : '';
            var original = input.getAttribute('data-original-value') || '';
            if (!texto || texto === original) return;
            var params = new URLSearchParams();
            params.append('checklistId', checklistId);
            params.append('itemId', itemId);
            params.append('texto', texto);
            return atualizarConteudoModalAjax(
                '/demandas/' + id + '/checklists/itens/atualizar',
                'POST',
                params,
                'Item atualizado.'
            );
        };

        // Membros
        window.adicionarMembroModal = function (membro) {
            var id = obterDemandaIdAtual();
            if (!id) return;
            var params = new URLSearchParams();
            params.append('membro', membro);
            atualizarConteudoModalAjax('/demandas/' + id + '/membros/adicionar', 'POST', params, 'Membro adicionado.');
        };

        window.removerMembroModal = function (membro) {
            var id = obterDemandaIdAtual();
            if (!id) return;
            var params = new URLSearchParams();
            params.append('membro', membro);
            atualizarConteudoModalAjax('/demandas/' + id + '/membros/remover', 'POST', params, 'Membro removido.');
        };


        // ── Imagem e Anexos ──

        // ── Imagem e Anexos ──

        var _ultimoCliqueSeletorImg = 0;
        window.acionarSeletorImagemModal = function (triggerBtn) {
            var agora = Date.now();
            if (agora - _ultimoCliqueSeletorImg < 400) {
                return;
            }
            _ultimoCliqueSeletorImg = agora;
            console.log('[Upload] Clique detectado no botão "Escolher Imagem do Computador"');

            var fileInput = document.getElementById('input-modal-imagem-file')
                || document.querySelector('#modal-detail-dialog-content input[type="file"]')
                || document.querySelector('input[type="file"][name="arquivo"]');

            if (!fileInput) {
                console.error('[Upload] Elemento input[type="file"] não encontrado no DOM!');
                exibirToast('Campo de upload não encontrado.', 'erro');
                return;
            }

            fileInput.value = '';
            console.log('[Upload] Abrindo seletor de arquivos do sistema operacional...');
            fileInput.click();
        };

        window.focarAbaUploadImagem = function () {
            window.fecharTodosPopovers();
            var sec = document.getElementById('modal-section-upload-imagem');
            if (sec) {
                sec.scrollIntoView({ behavior: 'smooth', block: 'center' });
                var btn = document.getElementById('btn-escolher-imagem-computador');
                if (btn) btn.focus();
            }
        };

        window.uploadImagemArquivoModal = function (input) {
            console.log('[Upload] Evento change disparado no input file');
            if (!input || !input.files || !input.files.length) {
                console.warn('[Upload] Nenhum arquivo selecionado no input');
                return;
            }
            if (input._isUploading) {
                console.warn('[Upload] Upload já em andamento, ignorando chamada duplicada.');
                return;
            }
            input._isUploading = true;

            var file = input.files[0];
            console.log('[Upload] Arquivo selecionado:', file.name, '| Tamanho:', file.size, 'bytes | Tipo MIME:', file.type);

            var id = (input && input.getAttribute('data-demanda-id')) || obterDemandaIdAtual();
            console.log('[Upload] ID do cartão identificado:', id);
            if (!id) {
                console.error('[Upload] Não foi possível identificar o ID do cartão!');
                exibirToast('Não foi possível identificar o cartão.', 'erro');
                input._isUploading = false;
                return;
            }

            var tiposPermitidos = ['image/png', 'image/jpeg', 'image/jpg', 'image/webp', 'image/gif'];
            if (!tiposPermitidos.includes(file.type.toLowerCase())) {
                console.warn('[Upload] Formato de arquivo recusado:', file.type);
                exibirToast('Formato não aceito. Utilize PNG, JPG, JPEG, WEBP ou GIF.', 'erro');
                input.value = '';
                input._isUploading = false;
                return;
            }

            if (file.size > 10 * 1024 * 1024) {
                console.warn('[Upload] Tamanho do arquivo excede o limite de 10MB:', file.size);
                exibirToast('A imagem deve ter no máximo 10MB.', 'erro');
                input.value = '';
                input._isUploading = false;
                return;
            }

            console.log('[Upload] Início do upload multipart para /demandas/' + id + '/imagem/upload');
            var formData = new FormData();
            formData.append('arquivo', file);

            var uploadBtn = document.getElementById('btn-escolher-imagem-computador') || document.querySelector('[data-action="escolher-imagem"]');
            if (uploadBtn) {
                uploadBtn.disabled = true;
                uploadBtn.style.opacity = '0.7';
            }

            window.fecharTodosPopovers();

            return atualizarConteudoModalAjax('/demandas/' + id + '/imagem/upload', 'POST', formData, 'Imagem enviada com sucesso!')
                .then(function () {
                    console.log('[Upload] Resposta do servidor recebida e fragmento do modal atualizado no DOM');
                    return window.sincronizarCardDoServidor(id).then(function () {
                        console.log('[Upload] Cartão sincronizado no quadro Kanban (capa e contador atualizados)');
                    });
                })
                .catch(function (err) {
                    console.error('[Upload] Erro durante o upload da imagem:', err);
                    exibirToast('Erro ao enviar imagem. Verifique o arquivo e tente novamente.', 'erro');
                })
                .finally(function () {
                    input._isUploading = false;
                    var b = document.getElementById('btn-escolher-imagem-computador') || document.querySelector('[data-action="escolher-imagem"]');
                    if (b) {
                        b.disabled = false;
                        b.style.opacity = '1';
                    }
                    if (input) input.value = '';
                });
        };


        window.fecharTodosPopovers = function () {
            document.querySelectorAll('.popover-menu').forEach(function (p) {
                p.classList.add('is-hidden');
            });
        };

        window.adicionarImagemModal = function (triggerBtn) {
            console.log('[Upload URL] Clique detectado em Anexar via URL');
            var id = (triggerBtn && triggerBtn.getAttribute('data-demanda-id')) || obterDemandaIdAtual();
            console.log('[Upload URL] ID do cartão identificado:', id);
            if (!id) {
                console.error('[Upload URL] Não foi possível identificar o ID do cartão!');
                exibirToast('Não foi possível identificar o cartão.', 'erro');
                return;
            }

            var input = (triggerBtn && triggerBtn.parentElement && triggerBtn.parentElement.querySelector('input[type="url"]'))
                || document.getElementById('input-modal-imagem-url');
            if (!input || !input.value.trim()) {
                exibirToast('Informe a URL da imagem (https://...).', 'erro');
                return;
            }
            var url = input.value.trim();
            if (!url.startsWith('http://') && !url.startsWith('https://') && !url.startsWith('data:image/')) {
                exibirToast('A URL deve começar com http:// ou https://', 'erro');
                return;
            }

            console.log('[Upload URL] Início do envio POST /demandas/' + id + '/imagem com URL:', url);
            var params = new URLSearchParams();
            params.append('imagemUrl', url);
            window.fecharTodosPopovers();

            var btn = (triggerBtn && triggerBtn.tagName === 'BUTTON') ? triggerBtn : document.getElementById('btn-anexar-imagem-url');
            if (btn) {
                btn.disabled = true;
            }

            return atualizarConteudoModalAjax('/demandas/' + id + '/imagem', 'POST', params, 'Imagem anexada com sucesso!')
                .then(function () {
                    if (input) input.value = '';
                    console.log('[Upload URL] Resposta do servidor recebida e fragmento do modal atualizado');
                    return window.sincronizarCardDoServidor(id).then(function () {
                        console.log('[Upload URL] Cartão sincronizado no quadro Kanban');
                    });
                })
                .catch(function (err) {
                    console.error('[Upload URL] Erro ao anexar imagem via URL:', err);
                    exibirToast('Erro ao anexar imagem via URL.', 'erro');
                })
                .finally(function () {
                    if (btn) btn.disabled = false;
                });
        };


        window.removerAnexoModal = function (anexoId) {
            var id = obterDemandaIdAtual();
            if (!id || !anexoId) return;
            if (!confirm('Deseja realmente remover este anexo?')) return;
            window.fecharTodosPopovers();
            var params = new URLSearchParams();
            params.append('anexoId', anexoId);
            return atualizarConteudoModalAjax('/demandas/' + id + '/anexos/remover', 'POST', params, 'Anexo removido.')
                .then(function () {
                    window.sincronizarCardDoServidor(id);
                });
        };

        window.tornarCapaAnexoModal = function (anexoId) {
            var id = obterDemandaIdAtual();
            if (!id || !anexoId) return;
            window.fecharTodosPopovers();
            var params = new URLSearchParams();
            params.append('anexoId', anexoId);
            params.append('capa', 'true');
            return atualizarConteudoModalAjax('/demandas/' + id + '/anexos/capa', 'POST', params, 'Capa atualizada.')
                .then(function () {
                    window.sincronizarCardDoServidor(id);
                });
        };

        window.removerCapaAnexoModal = function (anexoId) {
            var id = obterDemandaIdAtual();
            if (!id || !anexoId) return;
            window.fecharTodosPopovers();
            var params = new URLSearchParams();
            params.append('anexoId', anexoId);
            params.append('capa', 'false');
            return atualizarConteudoModalAjax('/demandas/' + id + '/anexos/capa', 'POST', params, 'Capa removida.')
                .then(function () {
                    window.sincronizarCardDoServidor(id);
                });
        };


        window.abrirEditarNomeAnexoModal = function (anexoId, nomeAtual) {
            var id = obterDemandaIdAtual();
            if (!id || !anexoId) return;
            window.fecharTodosPopovers();
            var novoNome = prompt('Editar nome do anexo:', nomeAtual || '');
            if (novoNome === null || !novoNome.trim() || novoNome.trim() === nomeAtual) return;
            var params = new URLSearchParams();
            params.append('anexoId', anexoId);
            params.append('nome', novoNome.trim());
            return atualizarConteudoModalAjax('/demandas/' + id + '/anexos/renomear', 'POST', params, 'Anexo renomeado.');
        };

        window.comentarSobreAnexoModal = function (anexoId, nomeAnexo) {
            window.fecharTodosPopovers();
            var input = document.getElementById('input-novo-comentario');
            if (input) {
                var prefixo = nomeAnexo ? (nomeAnexo + ' ') : '';
                input.value = prefixo;
                input.focus();
                window.validarBotaoComentario(input);
                input.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        };

        window.removerImagemModal = function () {
            var id = obterDemandaIdAtual();
            if (!id) return;
            return atualizarConteudoModalAjax('/demandas/' + id + '/imagem/remover', 'POST', null, 'Anexos removidos.');
        };

        window.removerImagemEspecificaModal = function (imgUrl) {
            var id = obterDemandaIdAtual();
            if (!id || !imgUrl) return;
            var params = new URLSearchParams();
            params.append('imagemUrl', imgUrl);
            return atualizarConteudoModalAjax('/demandas/' + id + '/imagem/remover', 'POST', params, 'Anexo removido.');
        };

        /* ── Visualizador Ampliado de Imagens (Lightbox estilo Trello) ── */

        window.listaImagensVisualizador = [];
        window.indiceImagemVisualizador = 0;

        window.abrirVisualizadorImagem = function (url, nome, metaInfo, anexoId, isCapa) {
            if (!url) return;
            window.listaImagensVisualizador = [{
                url: url,
                nome: nome || 'Imagem',
                meta: metaInfo || '',
                id: anexoId || null,
                isCapa: !!isCapa
            }];
            window.indiceImagemVisualizador = 0;
            window.renderizarImagemVisualizador();
        };

        window.abrirVisualizadorPorAnexo = function (anexoId) {
            var items = [];
            var elements = document.querySelectorAll('.attachment-info-clickable[data-anexo-img="true"]');
            var selectedIndex = 0;

            elements.forEach(function (el, idx) {
                var id = el.getAttribute('data-anexo-id');
                var url = el.getAttribute('data-anexo-url');
                var nome = el.getAttribute('data-anexo-nome') || 'Anexo';
                var isCapa = el.getAttribute('data-anexo-capa') === 'true';
                var metaSpan = el.querySelector('span');
                var metaText = metaSpan ? metaSpan.textContent.trim() : '';

                items.push({
                    id: id,
                    url: url,
                    nome: nome,
                    meta: metaText,
                    isCapa: isCapa
                });

                if (String(id) === String(anexoId)) {
                    selectedIndex = idx;
                }
            });

            if (items.length === 0 && anexoId) {
                var fallback = document.querySelector('[data-anexo-id="' + anexoId + '"]');
                if (fallback) {
                    items.push({
                        id: anexoId,
                        url: fallback.getAttribute('data-anexo-url'),
                        nome: fallback.getAttribute('data-anexo-nome') || 'Anexo',
                        meta: '',
                        isCapa: fallback.getAttribute('data-anexo-capa') === 'true'
                    });
                }
            }

            if (items.length > 0) {
                window.listaImagensVisualizador = items;
                window.indiceImagemVisualizador = selectedIndex;
                window.renderizarImagemVisualizador();
            }
        };

        window.navegarVisualizadorImagem = function (delta) {
            var total = window.listaImagensVisualizador.length;
            if (total <= 1) return;
            window.indiceImagemVisualizador = (window.indiceImagemVisualizador + delta + total) % total;
            window.renderizarImagemVisualizador();
        };

        window.renderizarImagemVisualizador = function () {
            var viewer = document.getElementById('image-viewer-lightbox');
            if (!viewer) return;

            var items = window.listaImagensVisualizador;
            var idx = window.indiceImagemVisualizador;
            if (!items || items.length === 0 || !items[idx]) return;

            var item = items[idx];
            var imgEl = document.getElementById('viewer-img-element');
            var titleEl = document.getElementById('viewer-file-title');
            var metaEl = document.getElementById('viewer-file-meta');
            var openEl = document.getElementById('viewer-action-open');
            var downloadEl = document.getElementById('viewer-action-download');
            var capaBtn = document.getElementById('viewer-action-capa');
            var capaText = document.getElementById('viewer-action-capa-text');
            var delBtn = document.getElementById('viewer-action-delete');
            var prevBtn = document.getElementById('btn-viewer-prev');
            var nextBtn = document.getElementById('btn-viewer-next');

            if (imgEl) imgEl.src = item.url;
            if (titleEl) titleEl.textContent = item.nome;
            if (metaEl) {
                var posText = (idx + 1) + ' de ' + items.length;
                metaEl.textContent = item.meta ? (item.meta + ' • ' + posText) : posText;
            }

            if (openEl) openEl.href = item.url;
            if (downloadEl) {
                downloadEl.href = item.url;
                downloadEl.setAttribute('download', item.nome);
            }

            if (capaBtn) {
                if (item.id) {
                    capaBtn.style.display = 'inline-flex';
                    if (capaText) capaText.textContent = item.isCapa ? 'Remover capa' : 'Tornar capa';
                } else {
                    capaBtn.style.display = 'none';
                }
            }

            if (delBtn) {
                delBtn.style.display = item.id ? 'inline-flex' : 'none';
            }

            if (prevBtn) {
                if (items.length > 1) prevBtn.classList.remove('is-hidden');
                else prevBtn.classList.add('is-hidden');
            }
            if (nextBtn) {
                if (items.length > 1) nextBtn.classList.remove('is-hidden');
                else nextBtn.classList.add('is-hidden');
            }

            viewer.classList.remove('is-hidden');
        };

        window.fecharVisualizadorImagem = function () {
            var viewer = document.getElementById('image-viewer-lightbox');
            if (viewer) {
                viewer.classList.add('is-hidden');
                var imgEl = document.getElementById('viewer-img-element');
                if (imgEl) imgEl.src = '';
            }
        };

        window.toggleCapaVisualizador = function () {
            var items = window.listaImagensVisualizador;
            var idx = window.indiceImagemVisualizador;
            if (!items || !items[idx] || !items[idx].id) return;
            var item = items[idx];

            if (item.isCapa) {
                window.removerCapaAnexoModal(item.id).then(function () {
                    item.isCapa = false;
                    var capaText = document.getElementById('viewer-action-capa-text');
                    if (capaText) capaText.textContent = 'Tornar capa';
                });
            } else {
                window.tornarCapaAnexoModal(item.id).then(function () {
                    items.forEach(function (it) { it.isCapa = false; });
                    item.isCapa = true;
                    var capaText = document.getElementById('viewer-action-capa-text');
                    if (capaText) capaText.textContent = 'Remover capa';
                });
            }
        };

        window.excluirAnexoVisualizador = function () {
            var items = window.listaImagensVisualizador;
            var idx = window.indiceImagemVisualizador;
            if (!items || !items[idx] || !items[idx].id) return;
            var item = items[idx];

            window.removerAnexoModal(item.id).then(function () {
                items.splice(idx, 1);
                if (items.length === 0) {
                    window.fecharVisualizadorImagem();
                } else {
                    if (window.indiceImagemVisualizador >= items.length) {
                        window.indiceImagemVisualizador = items.length - 1;
                    }
                    window.renderizarImagemVisualizador();
                }
            });
        };





        // Acompanhamento
        window.toggleAcompanharModal = function () {
            var id = obterDemandaIdAtual();
            if (!id) return;
            return atualizarConteudoModalAjax('/demandas/' + id + '/acompanhar', 'POST', null, 'Preferência de acompanhamento alterada.');
        };

        // Comentários
        window.validarBotaoComentario = function (textarea) {
            var btn = document.getElementById('btn-salvar-comentario') || document.querySelector('[data-action="salvar-comentario"]');
            if (btn) {
                var texto = textarea ? textarea.value.trim() : '';
                btn.disabled = texto.length === 0;
            }
        };

        window.enviarComentarioModal = function (triggerBtn) {
            var id = (triggerBtn && triggerBtn.getAttribute('data-demanda-id')) || obterDemandaIdAtual();
            if (!id) {
                console.error('enviarComentarioModal: ID da demanda não encontrado.');
                exibirToast('Não foi possível identificar o cartão.', 'erro');
                return Promise.reject(new Error('ID da demanda não encontrado'));
            }
            var input = document.getElementById('input-novo-comentario') || document.querySelector('.comment-input-field');
            if (!input || !input.value.trim()) {
                exibirToast('O comentário não pode estar vazio.', 'erro');
                return Promise.resolve();
            }

            var params = new URLSearchParams();
            params.append('texto', input.value.trim());
            return atualizarConteudoModalAjax('/demandas/' + id + '/comentar', 'POST', params, 'Comentário adicionado!')
                .then(function () {
                    var el = document.getElementById('input-novo-comentario') || document.querySelector('.comment-input-field');
                    if (el) {
                        el.value = '';
                        window.validarBotaoComentario(el);
                    }
                });
        };

        // Alternar Visualização de Detalhes na Atividade
        window.toggleMostrarDetalhesAtividade = function (triggerBtn) {
            var timeline = document.getElementById('modal-activity-list') || document.querySelector('.activity-timeline');
            var btn = triggerBtn || document.getElementById('btn-toggle-detalhes-atividade') || document.querySelector('[data-action="mostrar-detalhes"]');
            if (!timeline) return;

            var ocultaSistema = timeline.classList.toggle('hide-system');
            if (btn) {
                btn.textContent = ocultaSistema ? 'Mostrar Detalhes' : 'Ocultar Detalhes';
            }
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

        // ── Delegação de Eventos para os Controles do Modal (Idempotente) ──
        if (!window.__nexiooModalDelegacaoAtiva) {
            window.__nexiooModalDelegacaoAtiva = true;

            document.addEventListener('click', function (e) {
                // 0. Botão Editar Descrição ou clique na área de leitura / placeholder
                var btnEditarDesc = e.target.closest('[data-action="editar-descricao"], #btn-editar-descricao, #modal-desc-view-mode, #modal-desc-empty-placeholder');
                if (btnEditarDesc) {
                    e.preventDefault();
                    e.stopPropagation();
                    window.abrirEditorDescricaoModal();
                    return;
                }

                // 1. Botão Salvar Descrição
                var btnSalvarDesc = e.target.closest('[data-action="salvar-descricao"], #btn-salvar-descricao');
                if (btnSalvarDesc) {
                    e.preventDefault();
                    e.stopPropagation();
                    window.salvarDescricaoModal(btnSalvarDesc);
                    return;
                }

                // 2. Botão Cancelar Descrição
                var btnCancelarDesc = e.target.closest('[data-action="cancelar-descricao"], #btn-cancelar-descricao');
                if (btnCancelarDesc) {
                    e.preventDefault();
                    e.stopPropagation();
                    window.fecharEditorDescricaoModal();
                    return;
                }


                // 3. Botão Escolher Imagem do Computador
                var btnEscolherImg = e.target.closest('[data-action="escolher-imagem"], #btn-escolher-imagem-computador');
                if (btnEscolherImg) {
                    e.preventDefault();
                    e.stopPropagation();
                    window.acionarSeletorImagemModal(btnEscolherImg);
                    return;
                }




                // 4. Botão Anexar via URL
                var btnAnexarUrl = e.target.closest('[data-action="anexar-imagem-url"], #btn-anexar-imagem-url');
                if (btnAnexarUrl) {
                    e.preventDefault();
                    e.stopPropagation();
                    window.adicionarImagemModal(btnAnexarUrl);
                    return;
                }

                // 5. Botão Salvar Comentário
                var btnSalvarCom = e.target.closest('[data-action="salvar-comentario"], #btn-salvar-comentario');
                if (btnSalvarCom) {
                    e.preventDefault();
                    e.stopPropagation();
                    window.enviarComentarioModal(btnSalvarCom);
                    return;
                }

                // 6. Botão Mostrar Detalhes da Atividade
                var btnToggleDet = e.target.closest('[data-action="mostrar-detalhes"], #btn-toggle-detalhes-atividade');
                if (btnToggleDet) {
                    e.preventDefault();
                    e.stopPropagation();
                    window.toggleMostrarDetalhesAtividade(btnToggleDet);
                    return;
                }
            });

            document.addEventListener('change', function (e) {
                if (e.target && (e.target.id === 'input-modal-imagem-file' || e.target.matches('#modal-detail-dialog-content input[type="file"]'))) {
                    window.uploadImagemArquivoModal(e.target);
                }
            });

            document.addEventListener('input', function (e) {
                if (e.target && (e.target.id === 'input-novo-comentario' || e.target.classList.contains('comment-input-field'))) {
                    window.validarBotaoComentario(e.target);
                }
            });

            document.addEventListener('keydown', function (e) {
                if (e.target && (e.target.id === 'input-novo-comentario' || e.target.classList.contains('comment-input-field'))) {
                    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
                        e.preventDefault();
                        window.enviarComentarioModal();
                    }
                }
                if (e.target && (e.target.id === 'input-modal-imagem-url' || e.target.matches('.popover-input-text[type="url"]'))) {
                    if (e.key === 'Enter') {
                        e.preventDefault();
                        window.adicionarImagemModal();
                    }
                }
                if (e.target && e.target.id === 'modal-input-titulo') {
                    if (e.key === 'Enter') {
                        e.preventDefault();
                        window.salvarTituloModal(e.target);
                        e.target.blur();
                    }
                }
            });
        }
    }



    /**
     * Interação dos Cartões no Quadro
     */
    function iniciarInteracaoCartoes() {
        var cards = document.querySelectorAll('.kanban-card');

        function enviarFormularioCard(form, mensagem) {
            if (!form) return Promise.resolve();
            var cardEl = form.closest('.kanban-card');
            var demandaId = cardEl ? cardEl.getAttribute('data-id') : null;
            var params = new URLSearchParams(new FormData(form));

            return fetch(resolveUrl(form.action), {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body: params.toString()
            })
            .then(function (res) {
                if (!res.ok) throw new Error('Não foi possível atualizar a demanda.');
                return res.text();
            })
            .then(function (html) {
                if (demandaId) {
                    return sincronizarCardDoServidor(demandaId).then(function () {
                        if (mensagem) exibirToast(mensagem, 'sucesso');
                    });
                }
            })
            .catch(function (err) {
                exibirToast(err.message || 'Erro ao atualizar a demanda.', 'erro');
            });
        }



        window.toggleConcluidoCard = function (form) {
            return enviarFormularioCard(form, null);
        };


        window.alterarStatusCard = function (form) {
            return enviarFormularioCard(form, 'Lista atualizada.');
        };

        window.iniciarEdicaoRapidaCard = function (btn) {
            var card = btn ? btn.closest('.kanban-card') : null;
            if (!card) return;

            var id = card.getAttribute('data-id');
            var titleEl = card.querySelector('.card-title');
            var editorEl = card.querySelector('.card-quick-edit-editor');
            var inputEl = card.querySelector('.card-quick-edit-input');
            var saveBtn = card.querySelector('.btn-quick-edit-save');
            if (!id || !titleEl || !editorEl || !inputEl) return;

            var currentTitle = titleEl.textContent.trim();
            inputEl.value = currentTitle;

            card.classList.add('is-quick-editing');
            editorEl.style.display = 'flex';

            setTimeout(function () {
                inputEl.focus();
                inputEl.select();
            }, 30);

            function fecharEdicaoRapida() {
                card.classList.remove('is-quick-editing');
                editorEl.style.display = 'none';
                card.focus();
            }

            function salvarEdicaoRapida() {
                var novoTitulo = inputEl.value.trim();
                if (!novoTitulo) {
                    exibirToast('O título não pode ser vazio.', 'erro');
                    inputEl.focus();
                    return;
                }

                if (saveBtn) saveBtn.disabled = true;

                var params = new URLSearchParams();
                params.append('titulo', novoTitulo);

                fetch('/demandas/' + id + '/titulo', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded',
                        'X-Requested-With': 'XMLHttpRequest'
                    },
                    body: params.toString()
                })
                .then(function (res) {
                    if (!res.ok) throw new Error('Não foi possível salvar o título.');
                    titleEl.textContent = novoTitulo;
                    fecharEdicaoRapida();
                    exibirToast('Título atualizado com sucesso.', 'sucesso');
                })
                .catch(function (err) {
                    exibirToast(err.message || 'Erro ao salvar o título.', 'erro');
                })
                .finally(function () {
                    if (saveBtn) saveBtn.disabled = false;
                });
            }

            if (saveBtn) {
                saveBtn.onclick = function (e) {
                    e.preventDefault();
                    e.stopPropagation();
                    salvarEdicaoRapida();
                };
            }

            var openModalBtn = card.querySelector('.btn-quick-edit-open-modal');
            if (openModalBtn) {
                openModalBtn.onclick = function (e) {
                    e.preventDefault();
                    e.stopPropagation();
                    fecharEdicaoRapida();
                    window.abrirModalDetalheDemandas(id);
                };
            }

            inputEl.onkeydown = function (e) {

                if (e.key === 'Escape') {
                    e.preventDefault();
                    e.stopPropagation();
                    fecharEdicaoRapida();
                } else if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    e.stopPropagation();
                    salvarEdicaoRapida();
                }
            };
        };


        window.excluirDemanda = function (btn) {
            var card = btn ? btn.closest('.kanban-card') : null;
            if (!card) return;
            var id = card.getAttribute('data-id');
            if (!id) return;

            if (!confirm('Tem certeza que deseja excluir esta demanda?')) return;

            var coluna = card.closest('.kanban-column');
            fetch(resolveUrl('/demandas/' + id + '/api'), {
                method: 'DELETE',
                headers: {
                    'X-Requested-With': 'XMLHttpRequest'
                }
            })
            .then(function (res) {
                if (!res.ok) throw new Error('Não foi possível excluir a demanda.');
                card.remove();
                if (coluna) atualizarContadoresColuna(coluna);
                document.querySelectorAll('.kanban-column').forEach(atualizarContadoresColuna);
                exibirToast('Demanda excluída com sucesso.', 'sucesso');
            })
            .catch(function (err) {
                exibirToast(err.message || 'Erro ao excluir a demanda.', 'erro');
            });
        };

        window.excluirLista = function (colunaId) {

            if (!colunaId) return;
            if (!confirm('Tem certeza que deseja excluir esta lista?')) return;

            fetch('/colunas/' + colunaId + '/excluir', {
                method: 'POST',
                headers: {
                    'X-Requested-With': 'XMLHttpRequest'
                }
            })
            .then(function (res) {
                if (!res.ok) throw new Error('Não foi possível excluir a lista.');
                var colunaEl = document.getElementById('coluna-' + colunaId)
                    || document.getElementById('coluna-' + String(colunaId).toLowerCase())
                    || document.getElementById('coluna-' + String(colunaId).toUpperCase());
                if (colunaEl) {
                    colunaEl.remove();
                    exibirToast('Lista excluída com sucesso.', 'sucesso');
                } else {
                    window.location.reload();
                }
            })
            .catch(function (err) {
                exibirToast(err.message || 'Erro ao excluir a lista.', 'erro');
            });
        };

        document.addEventListener('click', function (e) {
            if (window.__dragAcabouDeOcorrer) {
                e.preventDefault();
                e.stopPropagation();
                return;
            }

            var openBtn = e.target.closest('.btn-card-open-modal, .btn-quick-edit-open-modal, [data-action="open-card-modal"]');
            if (openBtn) {
                e.preventDefault();
                e.stopPropagation();
                window.abrirModalDetalheDemandas(openBtn);
                return;
            }

            var card = e.target.closest('.kanban-card');
            if (card && !e.target.closest('button') && !e.target.closest('form') && !e.target.closest('a') && !e.target.closest('textarea') && !card.classList.contains('is-quick-editing')) {
                window.abrirModalDetalheDemandas(card);
            }
        });
    }





    /**
     * Recolhimento e Menus de Colunas
     */
    function iniciarRecolhimentoColunas() {
        /* Registrado via delegação de eventos global em document */
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
            document.querySelectorAll('.column-options-menu').forEach(function (menu) {
                menu.classList.add('is-hidden');
            });

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
    /**
     * Botão "Adicionar outra lista" — visibilidade via classe CSS, sem inline style.
     * O formulário é enviado via fetch para POST /colunas e a nova coluna é
     * injetada no DOM uma única vez, sem recarregar a página.
     */
    /**
     * Adição Inline de Lista
     */
    function fecharFormularioAddLista() {
        var container = document.getElementById('container-add-lista');
        var triggerBtn = document.getElementById('btn-add-lista-trigger');
        var form = document.getElementById('form-add-lista');
        var input = document.getElementById('input-nome-lista');
        var errorMsg = document.getElementById('add-list-error-msg');

        if (container) container.classList.remove('form-open');
        if (form) form.classList.add('is-hidden');
        if (triggerBtn) {
            triggerBtn.setAttribute('aria-expanded', 'false');
            triggerBtn.focus();
        }
        if (input) input.value = '';
        if (errorMsg) {
            errorMsg.textContent = '';
            errorMsg.classList.remove('is-visible');
        }
    }

    function mostrarErroAddLista(msg) {
        var input = document.getElementById('input-nome-lista');
        var errorMsg = document.getElementById('add-list-error-msg');
        if (errorMsg) {
            errorMsg.textContent = msg;
            errorMsg.classList.add('is-visible');
        }
        if (input) {
            input.classList.add('has-error');
            input.setAttribute('aria-invalid', 'true');
            input.focus();
        }
    }

    function submeterFormularioAddLista(form) {
        var input = document.getElementById('input-nome-lista');
        if (!input) return;

        var nome = input.value.trim();
        if (!nome) {
            mostrarErroAddLista('O nome da lista não pode ser vazio.');
            return;
        }

        var container = document.getElementById('container-add-lista');
        var track = document.getElementById('board-columns-track');
        var submitBtn = form ? form.querySelector('.btn-add-list-submit') : null;
        if (submitBtn) submitBtn.disabled = true;

        var params = new URLSearchParams();
        params.append('nome', nome);

        fetch('/colunas', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-Requested-With': 'XMLHttpRequest'
            },
            body: params.toString()
        })
        .then(function (res) {
            if (!res.ok) throw new Error('Não foi possível criar a lista.');
            return res.text();
        })
        .then(function (html) {
            var tmpl = document.createElement('template');
            tmpl.innerHTML = html.trim();
            var novaColunaEl = tmpl.content.querySelector('.kanban-column');
            if (novaColunaEl && track && container) {
                track.insertBefore(novaColunaEl, container);
                fecharFormularioAddLista();
                setTimeout(function () {
                    track.scrollTo({ left: track.scrollWidth, behavior: 'smooth' });
                }, 100);
                exibirToast('Lista "' + nome + '" criada com sucesso.', 'sucesso');
            } else {
                return fetch('/quadro')
                    .then(function (r) { return r.text(); })
                    .then(function (qHtml) {
                        var parser = new DOMParser();
                        var doc = parser.parseFromString(qHtml, 'text/html');
                        var novasColunasEls = doc.querySelectorAll('.kanban-column');
                        var existentes = new Set();
                        document.querySelectorAll('.kanban-column').forEach(function (c) {
                            existentes.add(c.id);
                        });
                        novasColunasEls.forEach(function (col) {
                            if (!existentes.has(col.id) && track && container) {
                                track.insertBefore(col, container);
                            }
                        });
                        fecharFormularioAddLista();
                        setTimeout(function () {
                            track.scrollTo({ left: track.scrollWidth, behavior: 'smooth' });
                        }, 100);
                        exibirToast('Lista "' + nome + '" criada com sucesso.', 'sucesso');
                    });
            }
        })
        .catch(function (err) {
            mostrarErroAddLista(err.message || 'Não foi possível criar a lista.');
        })
        .finally(function () {
            if (submitBtn) submitBtn.disabled = false;
        });
    }

    function iniciarAdicaoLista() {
        /* Registrado via delegação de eventos global em document */
    }

    /**
     * Compositor de Novo Cartão ("Adicionar um cartão")
     */
    function abrirCompositor(coluna) {
        if (!coluna) return;
        coluna.classList.add('composer-open');

        var footer = coluna.querySelector('.column-footer');
        var triggerBtn = footer ? footer.querySelector('.btn-add-card, .btn-open-compositor') : null;
        if (triggerBtn) triggerBtn.setAttribute('aria-expanded', 'true');

        var input = coluna.querySelector('.card-composer-input');
        if (input) {
            input.value = '';
            limparErroCompositor(coluna);
            setTimeout(function () { input.focus(); }, 30);
        }
    }

    function fecharCompositor(coluna, focarTrigger) {
        if (!coluna) return;
        coluna.classList.remove('composer-open');

        var footer = coluna.querySelector('.column-footer');
        var triggerBtn = footer ? footer.querySelector('.btn-add-card, .btn-open-compositor') : null;
        if (triggerBtn) {
            triggerBtn.setAttribute('aria-expanded', 'false');
            if (focarTrigger) triggerBtn.focus();
        }

        var input = coluna.querySelector('.card-composer-input');
        if (input) {
            input.value = '';
            limparErroCompositor(coluna);
        }
    }

    function mostrarErroCompositor(coluna, msg) {
        if (!coluna) return;
        var input = coluna.querySelector('.card-composer-input');
        var errorEl = coluna.querySelector('.card-composer-error');
        if (input) {
            input.classList.add('has-error');
            input.setAttribute('aria-invalid', 'true');
            input.focus();
        }
        if (errorEl) {
            errorEl.textContent = msg;
            errorEl.classList.add('is-visible');
        }
    }

    function limparErroCompositor(coluna) {
        if (!coluna) return;
        var input = coluna.querySelector('.card-composer-input');
        var errorEl = coluna.querySelector('.card-composer-error');
        if (input) {
            input.classList.remove('has-error');
            input.removeAttribute('aria-invalid');
        }
        if (errorEl) {
            errorEl.textContent = '';
            errorEl.classList.remove('is-visible');
        }
    }

    function submeterCompositor(coluna) {
        if (!coluna) return;
        var input = coluna.querySelector('.card-composer-input');
        if (!input) return;

        var titulo = input.value.trim();
        if (!titulo) {
            mostrarErroCompositor(coluna, 'O título não pode ser vazio.');
            return;
        }

        var colunaId = coluna.getAttribute('data-coluna-id') || coluna.getAttribute('data-id');
        if (!colunaId) {
            var idAttr = coluna.id;
            if (idAttr && idAttr.startsWith('coluna-')) {
                colunaId = idAttr.replace('coluna-', '').toUpperCase();
            }
        }
        if (!colunaId) return;

        var submitBtn = coluna.querySelector('.btn-compositor-submit');
        if (submitBtn) submitBtn.disabled = true;

        var params = new URLSearchParams();
        params.append('titulo', titulo);
        params.append('coluna', colunaId);
        params.append('prioridade', 'MEDIA');

        var boardTrack = document.getElementById('board-columns-track');
        var projId = boardTrack ? boardTrack.getAttribute('data-projeto-id') : null;
        if (!projId) {
            var urlParams = new URLSearchParams(window.location.search);
            projId = urlParams.get('projetoId');
        }
        if (projId) params.append('projetoId', projId);


        fetch('/demandas/compositor', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-Requested-With': 'XMLHttpRequest'
            },
            body: params.toString()
        })
        .then(function (res) {
            if (!res.ok) throw new Error('Não foi possível criar o cartão.');
            return res.text();
        })
        .then(function (html) {
            var tmpl = document.createElement('template');
            tmpl.innerHTML = html.trim();
            var novoCard = tmpl.content.querySelector('.kanban-card');
            if (!novoCard) throw new Error('Fragmento retornado é inválido.');

            novoCard.classList.add('card--inserido');

            var cardsList = coluna.querySelector('.column-cards-list');
            if (cardsList) {
                var emptyState = cardsList.querySelector('.column-empty-state');
                if (emptyState) emptyState.remove();

                cardsList.appendChild(novoCard);
                atualizarContadoresColuna(coluna);
            }

            fecharCompositor(coluna, false);
            novoCard.focus();
            exibirToast('Cartão adicionado com sucesso.', 'sucesso');
        })
        .catch(function (err) {
            mostrarErroCompositor(coluna, err.message || 'Erro ao criar o cartão.');
        })
        .finally(function () {
            if (submitBtn) submitBtn.disabled = false;
        });
    }

    function iniciarCompositorCartoes() {
        /* Registrado via delegação de eventos global em document */
    }

    /* ── Delegação de Eventos Global no Document ── */
    document.addEventListener('click', function (e) {
        // 1. Trigger "Adicionar outra lista"
        var triggerAddLista = e.target.closest('#btn-add-lista-trigger, .btn-add-list-trigger');
        if (triggerAddLista) {
            e.preventDefault();
            e.stopPropagation();
            var container = document.getElementById('container-add-lista');
            var form = document.getElementById('form-add-lista');
            var input = document.getElementById('input-nome-lista');
            if (container) {
                container.classList.add('form-open');
                if (form) form.classList.remove('is-hidden');
                triggerAddLista.setAttribute('aria-expanded', 'true');
                if (input) {
                    input.value = '';
                    setTimeout(function () { input.focus(); }, 30);
                }
            }
            return;
        }

        // 2. Cancelar "Adicionar outra lista"
        var cancelAddLista = e.target.closest('#btn-cancelar-add-lista, .btn-add-list-cancel');
        if (cancelAddLista) {
            e.preventDefault();
            e.stopPropagation();
            fecharFormularioAddLista();
            return;
        }

        // 3. Trigger "Adicionar um cartão"
        var triggerAddCard = e.target.closest('.btn-open-compositor, .btn-add-card');
        if (triggerAddCard) {
            e.preventDefault();
            e.stopPropagation();
            var coluna = triggerAddCard.closest('.kanban-column');
            if (coluna) {
                document.querySelectorAll('.kanban-column.composer-open').forEach(function (col) {
                    if (col !== coluna) fecharCompositor(col, false);
                });
                abrirCompositor(coluna);
            }
            return;
        }

        // 4. Cancelar compositor "Adicionar um cartão"
        var cancelAddCard = e.target.closest('.btn-compositor-cancel');
        if (cancelAddCard) {
            e.preventDefault();
            e.stopPropagation();
            var coluna = cancelAddCard.closest('.kanban-column');
            if (coluna) fecharCompositor(coluna, true);
            return;
        }

        // 5. Submit compositor "Adicionar um cartão"
        var submitAddCard = e.target.closest('.btn-compositor-submit');
        if (submitAddCard) {
            e.preventDefault();
            e.stopPropagation();
            var coluna = submitAddCard.closest('.kanban-column');
            if (coluna) submeterCompositor(coluna);
            return;
        }

        // 6. Recolher / Expandir Coluna
        var btnCollapse = e.target.closest('[data-action="toggle-collapse-coluna"]');
        if (btnCollapse) {
            e.preventDefault();
            e.stopPropagation();
            var colToCollapse = btnCollapse.closest('.kanban-column');
            if (colToCollapse) colToCollapse.classList.toggle('kanban-column--collapsed');
            return;
        }

        // 7. Menu de opções da Coluna (...)
        var btnMenu = e.target.closest('[data-action="toggle-column-menu"]');
        if (btnMenu) {
            e.preventDefault();
            e.stopPropagation();
            var wrapper = btnMenu.closest('.column-options-wrapper');
            var menu = wrapper ? wrapper.querySelector('.column-options-menu') : null;
            var abrir = menu && menu.classList.contains('is-hidden');
            document.querySelectorAll('.column-options-menu').forEach(function (m) { m.classList.add('is-hidden'); });
            document.querySelectorAll('[data-action="toggle-column-menu"]').forEach(function (b) { b.setAttribute('aria-expanded', 'false'); });
            if (menu && abrir) {
                menu.classList.remove('is-hidden');
                btnMenu.setAttribute('aria-expanded', 'true');
            }
            return;
        }

        // 8. Fechar menus de coluna ao clicar fora
        if (!e.target.closest('.column-options-wrapper')) {
            document.querySelectorAll('.column-options-menu').forEach(function (m) { m.classList.add('is-hidden'); });
            document.querySelectorAll('[data-action="toggle-column-menu"]').forEach(function (b) { b.setAttribute('aria-expanded', 'false'); });
        }

        // 10. Clique em Anexo com Imagem (abrir visualizador ampliado)
        var clickAnexoImg = e.target.closest('.attachment-info-clickable');
        if (clickAnexoImg && !e.target.closest('.popover-wrapper, a, button')) {
            var anxImg = clickAnexoImg.getAttribute('data-anexo-img');
            var anxId = clickAnexoImg.getAttribute('data-anexo-id');
            if (anxImg === 'true' && anxId) {
                e.preventDefault();
                e.stopPropagation();
                window.abrirVisualizadorPorAnexo(anxId);
                return;
            }
        }

        // 11. Clique na Capa do Cartão (abrir visualizador ampliado)
        var cardCover = e.target.closest('.card-cover-wrapper');
        if (cardCover && !e.target.closest('.btn-card-complete, button, a, form')) {
            e.preventDefault();
            e.stopPropagation();
            var imgEl = cardCover.querySelector('img');
            var url = cardCover.getAttribute('data-cover-url') || (imgEl ? imgEl.src : null);
            var title = cardCover.getAttribute('data-cover-title') || 'Capa';
            if (url) {
                window.abrirVisualizadorImagem(url, title, null);
                return;
            }
        }
    });



    document.addEventListener('keydown', function (e) {
        // Escape no input de nova lista
        var inputLista = e.target.closest('#input-nome-lista');
        if (inputLista && e.key === 'Escape') {
            e.preventDefault();
            fecharFormularioAddLista();
            return;
        }

        // Enter/Escape no compositor de cartão
        var inputCard = e.target.closest('.card-composer-input');
        if (inputCard) {
            var coluna = inputCard.closest('.kanban-column');
            if (!coluna) return;

            if (e.key === 'Escape') {
                e.preventDefault();
                fecharCompositor(coluna, true);
            } else if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                submeterCompositor(coluna);
            }
        }
    });

    document.addEventListener('submit', function (e) {
        if (e.target && e.target.id === 'form-add-lista') {
            e.preventDefault();
            submeterFormularioAddLista(e.target);
        }
    });



    /**
     * Sistema de Arrastar e Soltar (Drag and Drop) de Cartões Kanban — Estilo Trello
     */
    function iniciarDragAndDrop() {
        var boardTrack = document.getElementById('board-columns-track');
        if (!boardTrack) return;

        var draggedCard = null;
        var placeholder = null;
        var origemColumn = null;
        var origemList = null;
        var origemNextSibling = null;
        var origemColunaId = null;

        function limparDestaquesColunas() {
            document.querySelectorAll('.kanban-column.is-drag-over').forEach(function (col) {
                col.classList.remove('is-drag-over');
            });
        }

        function verificarAutoScroll(e) {
            if (!draggedCard || !boardTrack) return;

            var trackRect = boardTrack.getBoundingClientRect();
            var thresholdX = 80;

            if (e.clientX < trackRect.left + thresholdX) {
                var speedLeft = Math.max(6, Math.min(22, (trackRect.left + thresholdX - e.clientX) / 3));
                boardTrack.scrollLeft -= speedLeft;
            } else if (e.clientX > trackRect.right - thresholdX) {
                var speedRight = Math.max(6, Math.min(22, (e.clientX - (trackRect.right - thresholdX)) / 3));
                boardTrack.scrollLeft += speedRight;
            }

            var colUnderCursor = e.target.closest('.kanban-column');
            if (colUnderCursor) {
                var cardsList = colUnderCursor.querySelector('.column-cards-list');
                if (cardsList) {
                    var listRect = cardsList.getBoundingClientRect();
                    var thresholdY = 50;
                    if (e.clientY < listRect.top + thresholdY && cardsList.scrollTop > 0) {
                        cardsList.scrollTop -= 10;
                    } else if (e.clientY > listRect.bottom - thresholdY) {
                        cardsList.scrollTop += 10;
                    }
                }
            }
        }

        document.addEventListener('dragstart', function (e) {
            var card = e.target.closest('.kanban-card');
            if (!card || e.target.closest('input, textarea, button, form, a') || card.classList.contains('is-quick-editing')) {
                if (card && e.target.closest('input, textarea, button, form, a')) {
                    e.preventDefault();
                }
                return;
            }

            draggedCard = card;
            origemColumn = card.closest('.kanban-column');
            origemList = card.parentElement;
            origemNextSibling = card.nextElementSibling;
            origemColunaId = card.getAttribute('data-coluna') || (origemColumn ? origemColumn.getAttribute('data-coluna-id') : null);

            var cardId = card.getAttribute('data-id');
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/plain', cardId || '');

            if (!placeholder) {
                placeholder = document.createElement('div');
                placeholder.className = 'card-drop-placeholder';
            }
            var cardHeight = card.offsetHeight || 56;
            placeholder.style.height = cardHeight + 'px';

            window.__isDraggingCard = true;
            window.__dragAcabouDeOcorrer = false;

            setTimeout(function () {
                if (draggedCard) {
                    draggedCard.classList.add('is-dragging');
                    document.body.classList.add('is-dragging-card');
                }
            }, 0);
        });

        document.addEventListener('dragover', function (e) {
            if (!draggedCard) return;

            e.preventDefault();
            e.dataTransfer.dropEffect = 'move';

            verificarAutoScroll(e);

            var targetCol = e.target.closest('.kanban-column');
            if (!targetCol) return;

            document.querySelectorAll('.kanban-column').forEach(function (col) {
                if (col === targetCol) {
                    if (!col.classList.contains('is-drag-over')) col.classList.add('is-drag-over');
                } else {
                    col.classList.remove('is-drag-over');
                }
            });

            var cardsList = targetCol.querySelector('.column-cards-list');
            if (!cardsList) return;

            var cards = Array.from(cardsList.querySelectorAll('.kanban-card:not(.is-dragging)'));

            var insertBeforeCard = null;
            for (var i = 0; i < cards.length; i++) {
                var c = cards[i];
                var rect = c.getBoundingClientRect();
                var midpoint = rect.top + rect.height / 2;
                if (e.clientY < midpoint) {
                    insertBeforeCard = c;
                    break;
                }
            }

            if (insertBeforeCard) {
                if (placeholder.nextSibling !== insertBeforeCard) {
                    cardsList.insertBefore(placeholder, insertBeforeCard);
                }
            } else {
                var emptyState = cardsList.querySelector('.column-empty-state');
                if (emptyState) {
                    cardsList.insertBefore(placeholder, emptyState);
                } else if (placeholder.parentElement !== cardsList || placeholder.nextElementSibling) {
                    cardsList.appendChild(placeholder);
                }
            }
        });

        document.addEventListener('drop', function (e) {
            if (!draggedCard || !placeholder || !placeholder.parentElement) return;

            e.preventDefault();
            e.stopPropagation();

            var destinoList = placeholder.parentElement;
            var destinoCol = destinoList.closest('.kanban-column');
            if (!destinoCol) {
                limparEstadoArrasto();
                return;
            }

            var cardId = draggedCard.getAttribute('data-id');
            var destinoColunaId = destinoCol.getAttribute('data-coluna-id');
            var track = document.getElementById('board-columns-track');
            var projetoId = track ? track.getAttribute('data-projeto-id') : null;

            destinoList.insertBefore(draggedCard, placeholder);
            if (placeholder.parentElement) placeholder.remove();

            var cardsNaColuna = Array.from(destinoList.querySelectorAll('.kanban-card'));
            var novaPosicao = cardsNaColuna.indexOf(draggedCard);
            if (novaPosicao < 0) novaPosicao = 0;

            draggedCard.setAttribute('data-coluna', destinoColunaId);
            draggedCard.setAttribute('data-posicao', novaPosicao);

            atualizarContadoresColuna(origemColumn);
            if (destinoCol !== origemColumn) {
                atualizarContadoresColuna(destinoCol);
            }

            var params = new URLSearchParams();
            params.append('colunaOrigemId', origemColunaId || destinoColunaId);
            params.append('colunaDestinoId', destinoColunaId);
            params.append('novaPosicao', novaPosicao);
            if (projetoId) params.append('projetoId', projetoId);

            var savedOrigemList = origemList;
            var savedOrigemNextSibling = origemNextSibling;
            var savedOrigemCol = origemColumn;
            var savedOrigemColId = origemColunaId;
            var savedDestinoCol = destinoCol;
            var cardMovido = draggedCard;

            fetch('/demandas/' + cardId + '/mover', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body: params.toString()
            })
            .then(function (res) {
                if (!res.ok) throw new Error('Não foi possível mover o cartão.');
                return res.json();
            })
            .then(function (dados) {
                if (dados && dados.status === 'ok') {
                    cardMovido.setAttribute('data-posicao', dados.posicao != null ? dados.posicao : novaPosicao);
                }
            })
            .catch(function (err) {
                if (savedOrigemList && cardMovido) {
                    if (savedOrigemNextSibling && savedOrigemNextSibling.parentNode === savedOrigemList) {
                        savedOrigemList.insertBefore(cardMovido, savedOrigemNextSibling);
                    } else {
                        savedOrigemList.appendChild(cardMovido);
                    }
                    cardMovido.setAttribute('data-coluna', savedOrigemColId);
                    atualizarContadoresColuna(savedOrigemCol);
                    if (savedDestinoCol !== savedOrigemCol) {
                        atualizarContadoresColuna(savedDestinoCol);
                    }
                }
                exibirToast('Não foi possível mover o cartão. Tente novamente.', 'erro');
            });

            limparEstadoArrasto();
        });

        document.addEventListener('dragend', function () {
            limparEstadoArrasto();
        });

        function limparEstadoArrasto() {
            if (draggedCard) {
                draggedCard.classList.remove('is-dragging');
            }
            document.body.classList.remove('is-dragging-card');
            limparDestaquesColunas();

            if (placeholder && placeholder.parentElement) {
                placeholder.remove();
            }

            window.__dragAcabouDeOcorrer = true;
            window.__isDraggingCard = false;
            setTimeout(function () {
                window.__dragAcabouDeOcorrer = false;
            }, 250);

            draggedCard = null;
            origemColumn = null;
            origemList = null;
            origemNextSibling = null;
            origemColunaId = null;
        }

        // Acessibilidade por teclado (Alt + Setas)
        document.addEventListener('keydown', function (e) {
            if (!e.altKey) return;
            var key = e.key;
            if (key !== 'ArrowUp' && key !== 'ArrowDown' && key !== 'ArrowLeft' && key !== 'ArrowRight') return;

            var card = document.activeElement ? document.activeElement.closest('.kanban-card') : null;
            if (!card || card.classList.contains('is-quick-editing')) return;

            var currentList = card.parentElement;
            var currentCol = card.closest('.kanban-column');
            if (!currentList || !currentCol) return;

            var track = document.getElementById('board-columns-track');
            if (!track) return;

            var cols = Array.from(track.querySelectorAll('.kanban-column:not(.kanban-column--collapsed)'));
            var currentColIndex = cols.indexOf(currentCol);

            var origemColId = currentCol.getAttribute('data-coluna-id');
            var targetCol = currentCol;
            var targetList = currentList;

            if (key === 'ArrowUp') {
                e.preventDefault();
                var prevCard = card.previousElementSibling;
                while (prevCard && !prevCard.classList.contains('kanban-card')) {
                    prevCard = prevCard.previousElementSibling;
                }
                if (prevCard) {
                    currentList.insertBefore(card, prevCard);
                    salvarPosicaoTeclado(card, currentCol, currentCol, origemColId, currentCol.getAttribute('data-coluna-id'));
                }
            } else if (key === 'ArrowDown') {
                e.preventDefault();
                var nextCard = card.nextElementSibling;
                while (nextCard && !nextCard.classList.contains('kanban-card')) {
                    nextCard = nextCard.nextElementSibling;
                }
                if (nextCard) {
                    currentList.insertBefore(nextCard, card);
                    salvarPosicaoTeclado(card, currentCol, currentCol, origemColId, currentCol.getAttribute('data-coluna-id'));
                }
            } else if (key === 'ArrowLeft') {
                e.preventDefault();
                if (currentColIndex > 0) {
                    targetCol = cols[currentColIndex - 1];
                    targetList = targetCol.querySelector('.column-cards-list');
                    if (targetList) {
                        targetList.appendChild(card);
                        salvarPosicaoTeclado(card, currentCol, targetCol, origemColId, targetCol.getAttribute('data-coluna-id'));
                    }
                }
            } else if (key === 'ArrowRight') {
                e.preventDefault();
                if (currentColIndex >= 0 && currentColIndex < cols.length - 1) {
                    targetCol = cols[currentColIndex + 1];
                    targetList = targetCol.querySelector('.column-cards-list');
                    if (targetList) {
                        targetList.appendChild(card);
                        salvarPosicaoTeclado(card, currentCol, targetCol, origemColId, targetCol.getAttribute('data-coluna-id'));
                    }
                }
            }
        });

        function salvarPosicaoTeclado(card, colOrigem, colDestino, origemId, destinoId) {
            var cardsNaColuna = Array.from(colDestino.querySelectorAll('.kanban-card'));
            var novaPosicao = cardsNaColuna.indexOf(card);
            if (novaPosicao < 0) novaPosicao = 0;

            card.setAttribute('data-coluna', destinoId);
            card.setAttribute('data-posicao', novaPosicao);

            atualizarContadoresColuna(colOrigem);
            if (colDestino !== colOrigem) {
                atualizarContadoresColuna(colDestino);
            }

            card.focus();

            var track = document.getElementById('board-columns-track');
            var projetoId = track ? track.getAttribute('data-projeto-id') : null;
            var cardId = card.getAttribute('data-id');

            var params = new URLSearchParams();
            params.append('colunaOrigemId', origemId);
            params.append('colunaDestinoId', destinoId);
            params.append('novaPosicao', novaPosicao);
            if (projetoId) params.append('projetoId', projetoId);

            fetch('/demandas/' + cardId + '/mover', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body: params.toString()
            })
            .then(function (res) {
                if (!res.ok) throw new Error();
                return res.json();
            })
            .then(function (dados) {
                if (dados && dados.status === 'ok') {
                    card.setAttribute('data-posicao', dados.posicao != null ? dados.posicao : novaPosicao);
                }
            })
            .catch(function () {
                exibirToast('Não foi possível mover o cartão. Tente novamente.', 'erro');
            });
        }
    }

    function inicializar() {
        iniciarInteracaoCartoes();
        iniciarDragAndDrop();
        iniciarRecolhimentoColunas();
        iniciarModal();
        iniciarModalDetalhes();
        iniciarAdicaoLista();
        iniciarCompositorCartoes();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', inicializar);
    } else {
        inicializar();
    }

}());

