package comum;

/**
 * Configuração central do sistema: host, portas e parâmetros de tempo.
 * Todos os elementos rodam em localhost, diferenciados por porta.
 */
public final class Config {

    private Config() {}

    public static final String HOST = "localhost";

    // Tamanho da fila de conexões pendentes de cada ServerSocket (o padrão do
    // Java é 50). No Windows, quando a fila enche numa rajada, o sistema
    // RECUSA as conexões excedentes ("Connection refused") em vez de esperar.
    public static final int BACKLOG = 1000;

    // Pastas (relativas à raiz do projeto) dos arquivos gerados na execução:
    // dados/dados<id>.txt (servidores de dados) e logs/log_cliente<id>.txt (clientes)
    public static final String PASTA_DADOS = "dados";
    public static final String PASTA_LOGS = "logs";

    // Balanceador
    public static final int PORTA_BALANCEADOR = 5000;

    // Servidores de Aplicação (índice 0..2 => App 1..3)
    public static final int[] PORTAS_APP = {6001, 6002, 6003};

    // Servidores de Dados (índice 0..1 => Dados 1..2)
    // Porta que atende os workers
    public static final int[] PORTAS_DADOS = {7001, 7002};
    
    // Porta usada na comunicação entre os dois servidores de dados
    // (exclusão mútua + replicação)
    public static final int[] PORTAS_SYNC = {7101, 7102};

    // Cliente
    public static final int NUM_MIN = 1;
    public static final int NUM_MAX = 1000;
    public static final int CLIENTE_SLEEP_MIN_MS = 50;
    public static final int CLIENTE_SLEEP_MAX_MS = 200;
    // Tempo máximo esperando o RECEBIDO do balanceador antes de reenviar
    public static final int CLIENTE_TIMEOUT_MS = 10000;
    // Arquivo-sinal: quando existe, os clientes terminam a requisição atual e
    // saem (criado pelo PararTudo.ps1, apagado pelo SubirTudo.ps1)
    public static final String ARQUIVO_PARAR = "out/parar";

    // Servidor de Dados
    public static final int DADOS_SLEEP_MIN_MS = 100;
    public static final int DADOS_SLEEP_MAX_MS = 300;
}
