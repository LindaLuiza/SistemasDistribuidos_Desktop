package dados;

import comum.Config;
import comum.Protocolo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Servidor de Dados. Uso: java dados.ServidorDados <1|2>
 *
 * Cada conexão de um worker traz uma única requisição (CHECK;n) e recebe uma
 * única resposta (INSERIDO;n;linha ou EXISTE;n;linha). As conexões são aceitas
 * em paralelo, uma thread por conexão, então nenhuma é recusada; a verificação
 * e a escrita ficam dentro da seção crítica, onde entram uma de cada vez.
 */
public class ServidorDados {

    private final int id;
    private final ArquivoDados arquivo;
    private final ExclusaoMutua exclusao;

    public ServidorDados(int id, ArquivoDados arquivo, ExclusaoMutua exclusao) {
        this.id = id;
        this.arquivo = arquivo;
        this.exclusao = exclusao;
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Uso: java dados.ServidorDados <1|2>");
            return;
        }
        int id = Integer.parseInt(args[0]);
        int porta = Config.PORTAS_DADOS[id - 1];

        ArquivoDados arquivo = new ArquivoDados(Config.PASTA_DADOS + "/dados" + id + ".txt");
        // TODO Etapa 6: trocar por Ricart-Agrawala + replicação síncrona
        ExclusaoMutua exclusao = new ExclusaoMutuaLocal();

        System.out.println("[Dados " + id + "] iniciando na porta " + porta
                + " (arquivo com " + arquivo.getTotalLinhas() + " linhas)");
        new ServidorDados(id, arquivo, exclusao).escutar(porta);
    }

    /** Aceita conexões para sempre, despachando cada uma para uma thread do pool. */
    private void escutar(int porta) throws IOException {
        ExecutorService pool = Executors.newCachedThreadPool();
        try (ServerSocket servidor = new ServerSocket(porta, Config.BACKLOG)) {
            while (true) {
                Socket conexao = servidor.accept();
                pool.execute(() -> atender(conexao));
            }
        }
    }

    /** Atende uma requisição: lê CHECK;n, processa e responde. */
    private void atender(Socket conexao) {
        try (Socket s = conexao;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {

            String mensagem = in.readLine();
            if (mensagem == null) return;

            String[] campos = Protocolo.separar(mensagem);
            if (campos.length != 2 || !campos[0].equals(Protocolo.CHECK)) {
                System.err.println("[Dados " + id + "] mensagem inválida: " + mensagem);
                return;
            }
            int numero = Integer.parseInt(campos[1]);

            out.println(processar(numero));
        } catch (NumberFormatException e) {
            System.err.println("[Dados " + id + "] número inválido: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("[Dados " + id + "] erro na conexão: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Passos i, ii e iii do enunciado. O sleep fica FORA da seção crítica:
     * as esperas de várias requisições acontecem em paralelo e só a parte que
     * precisa de exclusividade (verificar + escrever + consistência) é serializada.
     */
    private String processar(int numero) throws IOException, InterruptedException {
        // i) dormir 100-300 ms
        Thread.sleep(ThreadLocalRandom.current().nextInt(
                Config.DADOS_SLEEP_MIN_MS, Config.DADOS_SLEEP_MAX_MS + 1));

        ArquivoDados.Resultado r;
        exclusao.entrar();
        try {
            // ii) verificar existência e inserir se necessário (dentro da seção
            // crítica, para que o mesmo número nunca seja inserido duas vezes)
            r = arquivo.consultarOuInserir(numero);
            // iii) TODO Etapa 6: se r.inserido, enviar REPLICAR ao par e aguardar ACK aqui
        } finally {
            exclusao.sair();
        }

        String resposta = Protocolo.montar(
                r.inserido ? Protocolo.INSERIDO : Protocolo.EXISTE, numero, r.linha);
        System.out.println("[Dados " + id + "] " + numero + " -> "
                + (r.inserido ? "INSERIDO na linha " : "EXISTE na linha ") + r.linha);
        return resposta;
    }
}
