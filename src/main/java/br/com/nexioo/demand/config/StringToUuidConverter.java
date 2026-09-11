package br.com.nexioo.demand.config;

import br.com.nexioo.demand.util.IdUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Conversor Spring MVC para converter String em UUID, aceitando UUIDs padrão,
 * inteiros legados e identificadores textuais via IdUtils.parseUuid.
 */
@Component
public class StringToUuidConverter implements Converter<String, UUID> {

    @Override
    public UUID convert(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        return IdUtils.parseUuid(source);
    }
}
