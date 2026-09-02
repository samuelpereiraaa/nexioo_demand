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
        if (!currentDetailId) return Promise.resolve();

        var dialog = document.getElementById('modal-detail-dialog-content');
        if (!dialog) return Promise.resolve();

        var options = {
            method: method || 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
        };
        if (params) {
            options.body = params.toString();
        }

        return fetch(url, options)
            .then(function (res) {
                if (!res.ok) throw new Error('Ocorreu um erro na requisição.');
                return res.text();
            })
            .then(function (html) {
                dialog.innerHTML = html;
                return sincronizarCardDoServidor(currentDetailId);
            })
            .then(function () {
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
        window.abrirModalDetalheDemandas = function (target) {
            var backdrop = document.getElementById('modal-detalhe-demanda');
            var dialog = document.getElementById('modal-detail-dialog-content');
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

        dialog.addEventListener('keydown', function (e) {

            if (e.key === 'Enter' && e.target.matches('.chk-item-input')) {
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

        // Funções de Ação no Modal

        window.salvarTituloModal = function (input) {
            if (!currentDetailId || !input) return;
            var titulo = input.value ? input.value.trim() : '';
            var original = input.getAttribute('data-original-value') || '';
            if (!titulo || titulo === original) return;
            var params = new URLSearchParams();
            params.append('titulo', titulo);
            return atualizarConteudoModalAjax(
                '/demandas/' + currentDetailId + '/titulo',
                'POST',
                params,
                'Título atualizado.'
            );
        };

        window.salvarDescricaoModal = function () {
            if (!currentDetailId) return;
            var textarea = dialog.querySelector('#modal-textarea-descricao');
            var desc = textarea ? textarea.value : '';
            var params = new URLSearchParams();
            params.append('descricao', desc);
            return atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/descricao', 'POST', params, 'Descrição salva com sucesso.');
        };

        window.restaurarDescricaoModal = function () {
            if (currentDetailId) {
                fetch('/demandas/' + currentDetailId + '/modal')
                    .then(function (res) { return res.text(); })
                    .then(function (html) { dialog.innerHTML = html; });
            }
        };

        window.adicionarImagemModal = function () {
            if (!currentDetailId) return;
            var input = dialog.querySelector('#input-modal-imagem-url');
            if (!input || !input.value.trim()) {
                exibirToast('Informe a URL da imagem.', 'erro');
                return;
            }
            var params = new URLSearchParams();
            params.append('imagemUrl', input.value.trim());
            return atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/imagem', 'POST', params, 'Imagem anexada.');
        };

        window.removerImagemModal = function () {
            if (!currentDetailId) return;
            return atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/imagem/remover', 'POST', null, 'Imagem removida.');
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

            return atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/modal-update', 'POST', params, 'Prioridade atualizada.');
        };

        window.salvarColunaModal = function (form) {
            if (!currentDetailId || !form) return;
            var select = form.querySelector('select[name="coluna"]');
            var novaColuna = select ? select.value : null;

            var params = new URLSearchParams(new FormData(form));
            return fetch(form.action, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: params.toString()
            })
            .then(function () {
                if (!novaColuna) throw new Error('Selecione uma lista.');
                return fetch('/demandas/' + currentDetailId + '/modal');
            })
            .then(function (res) { return res.text(); })
            .then(function (html) {
                dialog.innerHTML = html;
                return sincronizarCardDoServidor(currentDetailId);
            })
            .then(function () {
                exibirToast('Coluna atualizada!', 'sucesso');
            })
            .catch(function (err) {
                exibirToast(err.message || 'Erro ao alterar a lista.', 'erro');
            });
        };

        window.toggleConcluidoModal = function (form) {
            if (!currentDetailId || !form) return;
            return fetch(form.action, { method: 'POST' })
            .then(function () {
                return fetch('/demandas/' + currentDetailId + '/modal');
            })
            .then(function (res) { return res.text(); })
            .then(function (html) {
                dialog.innerHTML = html;
                return sincronizarCardDoServidor(currentDetailId);
            })
            .then(function () {
                exibirToast('Status de conclusão alterado!', 'sucesso');
            })
            .catch(function (err) {
                exibirToast(err.message || 'Erro ao alterar a conclusão.', 'erro');
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

        window.renomearChecklistModal = function (checklistId, input) {
            if (!input) return;
            var titulo = input.value ? input.value.trim() : '';
            var original = input.getAttribute('data-original-value') || '';
            if (!titulo || titulo === original) return;
            var params = new URLSearchParams();
            params.append('checklistId', checklistId);
            params.append('titulo', titulo);
            return atualizarConteudoModalAjax(
                '/demandas/' + currentDetailId + '/checklists/renomear',
                'POST',
                params,
                'Checklist atualizada.'
            );
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

        window.atualizarChecklistItemModal = function (checklistId, itemId, input) {
            if (!input) return;
            var texto = input.value ? input.value.trim() : '';
            var original = input.getAttribute('data-original-value') || '';
            if (!texto || texto === original) return;
            var params = new URLSearchParams();
            params.append('checklistId', checklistId);
            params.append('itemId', itemId);
            params.append('texto', texto);
            return atualizarConteudoModalAjax(
                '/demandas/' + currentDetailId + '/checklists/itens/atualizar',
                'POST',
                params,
                'Item atualizado.'
            );
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

        // Imagem
        window.uploadImagemArquivoModal = function (input) {
            if (!currentDetailId || !input || !input.files || !input.files[0]) return;
            var file = input.files[0];
            if (file.size > 10 * 1024 * 1024) {
                exibirToast('A imagem deve ter no máximo 10MB.', 'erro');
                return;
            }

            var reader = new FileReader();
            reader.onload = function (e) {
                var base64Url = e.target.result;
                var params = new URLSearchParams();
                params.append('imagemUrl', base64Url);
                atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/imagem', 'POST', params, 'Imagem enviada com sucesso!');
            };
            reader.readAsDataURL(file);
        };

        window.adicionarImagemModal = function () {
            if (!currentDetailId) return;
            var input = dialog.querySelector('#input-modal-imagem-url');
            if (!input || !input.value.trim()) {
                exibirToast('Informe a URL ou selecione uma imagem do seu computador.', 'erro');
                return;
            }
            var params = new URLSearchParams();
            params.append('imagemUrl', input.value.trim());
            atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/imagem', 'POST', params, 'Imagem anexada!');
        };

        window.removerImagemModal = function () {
            if (!currentDetailId) return;
            atualizarConteudoModalAjax('/demandas/' + currentDetailId + '/imagem/remover', 'POST', null, 'Imagem removida.');
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
            btn.textContent = ocultaSistema ? 'Mostrar detalhes' : 'Ocultar detalhes';
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

        document.querySelectorAll('[data-action="toggle-column-menu"]').forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.preventDefault();
                e.stopPropagation();
                var menu = btn.parentNode.querySelector('.column-options-menu');
                var abrir = menu && menu.classList.contains('is-hidden');
                document.querySelectorAll('.column-options-menu').forEach(function (item) {
                    item.classList.add('is-hidden');
                });
                document.querySelectorAll('[data-action="toggle-column-menu"]').forEach(function (item) {
                    item.setAttribute('aria-expanded', 'false');
                });
                if (menu && abrir) {
                    menu.classList.remove('is-hidden');
                    btn.setAttribute('aria-expanded', 'true');
                }
            });
        });

        document.addEventListener('click', function (e) {
            if (!e.target.closest('.column-options-wrapper')) {
                document.querySelectorAll('.column-options-menu').forEach(function (menu) {
                    menu.classList.add('is-hidden');
                });
                document.querySelectorAll('[data-action="toggle-column-menu"]').forEach(function (btn) {
                    btn.setAttribute('aria-expanded', 'false');
                });
            }
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



    function inicializar() {
        iniciarInteracaoCartoes();
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

