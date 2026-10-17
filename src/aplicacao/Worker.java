package aplicacao;

import comum.Config;
import comum.Protocolo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Thread worker: atende UMA requisição e termina.
 *
 * Fluxo (na ordem exigida pelo enunciado):
 *   1. lê CHECK;n do balanceador e confirma com RECEBIDO;n;
 *   2. verifica a primalidade PRIMEIRO;
 *   3. sorteia um servidor de dados, envia o número e espera a resposta;
 *   4. imprime o resultado; só então a thread termina.
 */
public class Worker implements Runnable {

    private final int idApp;
    private final Socket conexao;

    public Worker(int idApp, Socket conexao) {
        this.idApp = idApp;
        this.conexao = conexao;
    }

    @Override
    public void run() {
        Integer numero = receber();
        if (numero == null) return;

        boolean primo = Primalidade.ehPrimo(numero);

        String[] resposta;
        try {
            resposta = consultarDadosAteResponder(numero);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        System.out.println(formatar(numero, primo, resposta));
    }

    /**
     * O cliente já recebeu RECEBIDO, então o worker não pode desistir: se
     * nenhum servidor de dados responder, espera e tenta de novo (50 ms,
     * 100 ms, 200 ms, ... até 1 s entre tentativas) até conseguir.
     */
    private String[] consultarDadosAteResponder(int numero) throws InterruptedException {
        int esperaMs = 50;
        while (true) {
            String[] resposta = consultarDados(numero);
            if (resposta != null) return resposta;
            System.err.println("[App " + idApp + "] nenhum servidor de dados respondeu para o número "
                    + numero + "; tentando de novo em " + esperaMs + " ms");
            Thread.sleep(esperaMs);
            esperaMs = Math.min(esperaMs * 2, 1000);
        }
    }

    /**
     * Lê a requisição e confirma o recebimento na hora, antes de processar:
     * o cliente só espera essa confirmação (milissegundos), não o resultado.
     */
    private Integer receber() {
        try (Socket s = conexao;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {

            String mensagem = in.readLine();
            if (mensagem == null) return null;

            String[] campos = Protocolo.separar(mensagem);
            if (campos.length != 2 || !campos[0].equals(Protocolo.CHECK)) {
                System.err.println("[App " + idApp + "] mensagem inválida: " + mensagem);
                return null;
            }
            int numero = Integer.parseInt(campos[1]);
            out.println(Protocolo.montar(Protocolo.RECEBIDO, numero));
            return numero;
        } catch (NumberFormatException e) {
            System.err.println("[App " + idApp + "] número inválido: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("[App " + idApp + "] erro ao receber requisição: " + e.getMessage());
        }
        return null;
    }

    /**
     * Sorteia um dos servidores de dados (sem política de balanceamento) e
     * envia o número. Se o sorteado estiver fora do ar, tenta o outro, para
     * não perder a requisição. Sem timeout de leitura: a resposta pode
     * demorar enquanto o servidor de dados espera a seção crítica.
     *
     * @return campos da resposta (INSERIDO|EXISTE, numero, linha), ou null se nenhum respondeu
     */
    private String[] consultarDados(int numero) {
        int total = Config.PORTAS_DADOS.length;
        int sorteado = ThreadLocalRandom.current().nextInt(total);
        for (int tentativa = 0; tentativa < total; tentativa++) {
            int porta = Config.PORTAS_DADOS[(sorteado + tentativa) % total];
            try (Socket s = new Socket(Config.HOST, porta);
                 PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {

                out.println(Protocolo.montar(Protocolo.CHECK, numero));
                String resposta = in.readLine();
                if (resposta != null) return Protocolo.separar(resposta);
            } catch (IOException e) {
                System.err.println("[App " + idApp + "] servidor de dados na porta " + porta
                        + " indisponível: " + e.getMessage());
            }
        }
        return null;
    }

    /** Monta a frase exatamente como pede o enunciado. */
    private static String formatar(int numero, boolean primo, String[] resposta) {
        String ehPrimo = primo ? "é primo" : "não é primo";
        String linha = resposta[2];
        if (resposta[0].equals(Protocolo.INSERIDO)) {
            return "O número " + numero + " " + ehPrimo
                    + ", não está presente no arquivo, e foi inserido na linha " + linha;
        }
        return "O número " + numero + " " + ehPrimo
                + ", e já se encontra no arquivo na linha " + linha;
    }
}
