package com.umc.pediupatrao.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Formatacao padrao do sistema (moeda, data/hora, percentual e mascara de telefone).
 * Registrado como bean "fmt" para ser usado nas telas: ${@fmt.moeda(pedido.valorTotal)}
 */
@Component("fmt")
public class Formatos {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /** Aceita BigDecimal (pedidos) e Double (preco do produto, como veio no projeto original). */
    public String moeda(Number valor) {
        if (valor == null) {
            return "";
        }
        BigDecimal v = valor instanceof BigDecimal bd ? bd : BigDecimal.valueOf(valor.doubleValue());
        DecimalFormat df = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
        return "R$ " + df.format(v.setScale(2, RoundingMode.HALF_UP));
    }

    public String percentual(BigDecimal valor) {
        if (valor == null) {
            return "";
        }
        return valor.stripTrailingZeros().toPlainString().replace('.', ',') + "%";
    }

    /** Codigo curto de um id do MongoDB (6 ultimos caracteres), igual ao exibido nas telas de pedido. */
    public String codigo(String id) {
        if (id == null) {
            return "";
        }
        return (id.length() <= 6 ? id : id.substring(id.length() - 6)).toUpperCase();
    }

    public String dataHora(LocalDateTime dataHora) {
        return dataHora == null ? "" : dataHora.format(DATA_HORA);
    }

    /**
     * Deixa visiveis apenas o DDD e os 4 ultimos digitos: "(11) *****-4321".
     * Usado na auditoria, que precisa mostrar QUE o telefone mudou, nao o numero inteiro.
     */
    public static String mascararTelefone(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return telefone;
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() < 6) {
            return "****";
        }
        String ddd = digitos.substring(0, 2);
        String fim = digitos.substring(digitos.length() - 4);
        String meio = "*".repeat(digitos.length() - 6);
        return "(" + ddd + ") " + meio + "-" + fim;
    }
}
