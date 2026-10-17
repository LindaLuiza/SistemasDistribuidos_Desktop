package comum;

/**
 * Constantes do protocolo de texto: uma mensagem por linha, campos separados por ';'.
 *
 * Cliente -> Balanceador -> App:   CHECK;<numero>
 * App -> Balanceador -> Cliente:   RECEBIDO;<numero>      confirmação imediata, antes de processar
 * App -> Dados:                    CHECK;<numero>
 * Dados -> App:                    INSERIDO;<numero>;<linha>
 *                                  EXISTE;<numero>;<linha>
 *
 * Entre servidores de dados (canal de sincronização):
 *   REQUEST;<timestamp>;<idServidor>   pedido de entrada na seção crítica (Ricart-Agrawala)
 *   OK;<idServidor>                    permissão concedida
 *   REPLICAR;<numero>;<linha>          replica a escrita no par
 *   ACK                                confirmação da replicação
 */
public final class Protocolo {

    private Protocolo() {}

    public static final String SEP = ";";

    // Requisição e respostas worker <-> dados
    public static final String CHECK = "CHECK";
    public static final String RECEBIDO = "RECEBIDO";
    public static final String INSERIDO = "INSERIDO";
    public static final String EXISTE = "EXISTE";

    // Sincronização entre servidores de dados
    public static final String REQUEST = "REQUEST";
    public static final String OK = "OK";
    public static final String REPLICAR = "REPLICAR";
    public static final String ACK = "ACK";

    public static String montar(Object... campos) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) sb.append(SEP);
            sb.append(campos[i]);
        }
        return sb.toString();
    }

    public static String[] separar(String mensagem) {
        return mensagem.trim().split(SEP);
    }
}
