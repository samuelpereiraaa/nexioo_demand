package br.com.nexioo.demand.service;

import br.com.nexioo.demand.dto.ProjetoForm;
import br.com.nexioo.demand.model.Projeto;
import br.com.nexioo.demand.repository.ProjetoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementação da regra de negócios de projetos.
 */
@Service
public class ProjetoServiceImpl implements ProjetoService {

    private final ProjetoRepository projetoRepository;

    public ProjetoServiceImpl(ProjetoRepository projetoRepository) {
        this.projetoRepository = projetoRepository;
    }

    @Override
    public List<Projeto> listarTodos() {
        return projetoRepository.listarTodos();
    }

    @Override
    public List<Projeto> listarRecentes() {
        List<Projeto> recentes = projetoRepository.listarRecentes();
        if (recentes.isEmpty()) {
            List<Projeto> todos = projetoRepository.listarTodos();
            if (!todos.isEmpty()) {
                return List.of(todos.get(0));
            }
        }
        return recentes;
    }

    @Override
    public Projeto buscarPorId(Long id) {
        return projetoRepository.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Projeto não encontrado com o ID: " + id));
    }

    @Override
    public Projeto criar(ProjetoForm form) {
        if (form == null || form.getNome() == null || form.getNome().isBlank()) {
            throw new IllegalArgumentException("O nome do projeto é obrigatório.");
        }

        Projeto projeto = new Projeto();
        projeto.setNome(form.getNome().trim());
        projeto.setDescricao(form.getDescricao() != null ? form.getDescricao().trim() : "");
        if (form.getGradiente() != null && !form.getGradiente().isBlank()) {
            projeto.setGradiente(form.getGradiente());
        }
        projeto.setRecentementeVisualizado(true);

        return projetoRepository.salvar(projeto);
    }

    @Override
    public void excluir(Long id) {
        projetoRepository.excluir(id);
    }

    @Override
    public void marcarComoRecente(Long id) {
        projetoRepository.buscarPorId(id).ifPresent(p -> {
            p.setRecentementeVisualizado(true);
            projetoRepository.salvar(p);
        });
    }
}
