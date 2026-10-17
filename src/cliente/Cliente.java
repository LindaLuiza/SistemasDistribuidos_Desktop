package cliente;

import comum.Config;
import comum.Protocolo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Cliente. Uso: java cliente.Cliente <1|2>
 *
 * Loop infinito: sorteia 1-1000, grava no log ANTES de enviar, envia ao
 * balanceador até receber RECEBIDO e dorme 50-200 ms.
 *
 * Nenhum número do log pode deixar de chegar aos servidores de dados, por isso:
 *  - se o envio falhar, o MESMO número é reenviado até ser confirmado (seguro,
 *    porque CHECK é idempotente: reprocessar só gera "já se encontra");
 *  - a parada é sempre entre uma requisição e outra, nunca entre gravar no log
 *    e enviar (arquivo-sinal do PararTudo.ps1 ou Ctrl+C).
 */
public class Cliente {

    private static final int TIMEOUT_CONEXAO_MS = 1000;

    /** Ligado pelo Ctrl+C (shutdown hook). */
    private static volatile boolean pararPedido = false;

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            System.err.println("Uso: java cliente.Cliente <1|2>");
            return;
        }
        int id = Integer.parseInt(args[0]);
        Files.createDirectories(Path.of(Config.PASTA_LOGS));
        String nomeLog = Config.PASTA_LOGS + "/log_cliente" + id + ".txt";
        System.out.println("[Cliente " + id + "] iniciando; balanceador em "
                + Config.HOST + ":" + Config.PORTA_BALANCEADOR + "; log em " + nomeLog);

        // Ctrl+C: pede a parada e espera a requisição em andamento terminar
        CountDownLatch terminou = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            pararPedido = true;
            try {
                terminou.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException ignorada) {
                Thread.currentThread().interrupt();
            }
        }));

        long enviados = 0;
        // Aberto uma vez só, em modo append (o log continua entre execuções)
        try (BufferedWriter log = new BufferedWriter(new FileWriter(nomeLog, true))) {
            while (!deveParar()) {
                int numero = ThreadLocalRandom.current().nextInt(Config.NUM_MIN, Config.NUM_MAX + 1);

                // Grava ANTES de enviar; flush para garantir que já está no disco
                log.write(Integer.toString(numero));
                log.newLine();
                log.flush();

                enviarAteConfirmar(id, numero);
                enviados++;
                System.out.println("[Cliente " + id + "] #" + enviados + " enviou " + numero + " -> RECEBIDO");

                Thread.sleep(ThreadLocalRandom.current().nextInt(
                        Config.CLIENTE_SLEEP_MIN_MS, Config.CLIENTE_SLEEP_MAX_MS + 1));
            }
        } finally {
            terminou.countDown();
        }
        System.out.println("[Cliente " + id + "] parado após " + enviados + " envios");
    }

    private static boolean deveParar() {
        return pararPedido || Files.exists(Path.of(Config.ARQUIVO_PARAR));
    }

    /**
     * Envia CHECK;n ao balanceador e espera RECEBIDO;n. Se falhar (balanceador
     * fora do ar, conexão recusada, timeout), espera e reenvia o mesmo número:
     * 50 ms, 100 ms, 200 ms, ... até 1 s entre tentativas, sem desistir.
     */
    private static void enviarAteConfirmar(int id, int numero) throws InterruptedException {
        String esperado = Protocolo.montar(Protocolo.RECEBIDO, numero);
        int esperaMs = 50;
        while (true) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(Config.HOST, Config.PORTA_BALANCEADOR), TIMEOUT_CONEXAO_MS);
                s.setSoTimeout(Config.CLIENTE_TIMEOUT_MS);
                PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));

                out.println(Protocolo.montar(Protocolo.CHECK, numero));
                String resposta = in.readLine();
                if (esperado.equals(resposta)) return;
                System.err.println("[Cliente " + id + "] resposta inesperada para " + numero + ": " + resposta);
            } catch (IOException e) {
                System.err.println("[Cliente " + id + "] falha ao enviar " + numero + ": " + e.getMessage()
                        + "; reenviando em " + esperaMs + " ms");
            }
            Thread.sleep(esperaMs);
            esperaMs = Math.min(esperaMs * 2, 1000);
        }
    }
}
