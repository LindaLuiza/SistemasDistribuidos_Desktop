package balanceador;

import comum.Config;
import comum.Protocolo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Balanceador de Carga (Round Robin, proxy). Uso: java balanceador.Balanceador
 *
 * O cliente só conhece este endereço. Para cada requisição, o balanceador
 * escolhe o próximo servidor de aplicação da vez, repassa o CHECK;n, espera o
 * RECEBIDO;n e o devolve ao cliente. A conexão dura só alguns milissegundos,
 * porque o servidor de aplicação confirma antes de processar.
 */
public class Balanceador {

    /** Tempo máximo para conectar num servidor de aplicação antes de tentar o próximo. */
    private static final int TIMEOUT_CONEXAO_MS = 1000;
    /** O App responde RECEBIDO na hora; demorar mais que isso significa que travou. */
    private static final int TIMEOUT_RESPOSTA_MS = 5000;

    private final RoundRobin roundRobin = new RoundRobin(Config.PORTAS_APP.length);
    private final AtomicIntegerArray enviadas = new AtomicIntegerArray(Config.PORTAS_APP.length);

    public static void main(String[] args) throws IOException {
        System.out.println("[Balanceador] iniciando na porta " + Config.PORTA_BALANCEADOR);
        new Balanceador().escutar();
    }

    private void escutar() throws IOException {
        ExecutorService pool = Executors.newCachedThreadPool();
        try (ServerSocket servidor = new ServerSocket(Config.PORTA_BALANCEADOR, Config.BACKLOG)) {
            while (true) {
                Socket conexao = servidor.accept();
                pool.execute(() -> atender(conexao));
            }
        }
    }

    /** Lê o CHECK;n do cliente, repassa a um servidor de aplicação e devolve a confirmação. */
    private void atender(Socket conexao) {
        try (Socket s = conexao;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {

            String mensagem = in.readLine();
            if (mensagem == null) return;

            String[] campos = Protocolo.separar(mensagem);
            if (campos.length != 2 || !campos[0].equals(Protocolo.CHECK)) {
                System.err.println("[Balanceador] mensagem inválida: " + mensagem);
                return;
            }

            String resposta = repassar(mensagem.trim());
            if (resposta != null) {
                out.println(resposta);
            } else {
                // Sem resposta ao cliente: ele percebe a falha (tratado no Cliente)
                System.err.println("[Balanceador] ERRO: nenhum servidor de aplicação disponível para " + mensagem);
            }
        } catch (IOException e) {
            System.err.println("[Balanceador] erro na conexão com o cliente: " + e.getMessage());
        }
    }

    /**
     * Envia ao servidor da vez (Round Robin). Se ele estiver fora do ar, tenta
     * o próximo da sequência. Reenviar é seguro porque CHECK é idempotente: se
     * um número for processado duas vezes, a segunda só responde EXISTE.
     *
     * @return a resposta do servidor de aplicação (RECEBIDO;n), ou null se nenhum respondeu
     */
    private String repassar(String mensagem) {
        int total = Config.PORTAS_APP.length;
        int escolhido = roundRobin.proximo();
        for (int tentativa = 0; tentativa < total; tentativa++) {
            int i = (escolhido + tentativa) % total;
            int porta = Config.PORTAS_APP[i];
            try (Socket app = new Socket()) {
                app.connect(new InetSocketAddress(Config.HOST, porta), TIMEOUT_CONEXAO_MS);
                app.setSoTimeout(TIMEOUT_RESPOSTA_MS);
                PrintWriter out = new PrintWriter(app.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(app.getInputStream()));

                out.println(mensagem);
                String resposta = in.readLine();
                if (resposta != null) {
                    enviadas.incrementAndGet(i);
                    System.out.println("[Balanceador] " + mensagem + " -> App " + (i + 1)
                            + (tentativa > 0 ? " (desviado)" : "") + "  " + contagem());
                    return resposta;
                }
            } catch (IOException e) {
                System.err.println("[Balanceador] App " + (i + 1) + " indisponível: " + e.getMessage());
            }
        }
        return null;
    }

    /** Ex.: (App1=12 App2=12 App3=11) */
    private String contagem() {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < enviadas.length(); i++) {
            if (i > 0) sb.append(' ');
            sb.append("App").append(i + 1).append('=').append(enviadas.get(i));
        }
        return sb.append(')').toString();
    }
}
