package br.com.nexioo.demand.config;

import br.com.nexioo.demand.model.Coluna;
import br.com.nexioo.demand.service.ColunaService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Conversor Spring MVC para converter String (ID da coluna) no objeto {@link Coluna}.
 */
@Component
public class StringToColunaConverter implements Converter<String, Coluna> {

    private final ColunaService colunaService;

    public StringToColunaConverter(ColunaService colunaService) {
        this.colunaService = colunaService;
    }

    @Override
    public Coluna convert(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        return colunaService.buscarPorId(source);
    }
}
