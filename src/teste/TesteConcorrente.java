package teste;

import comum.Config;
import comum.Protocolo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Teste concorrente (não faz parte do sistema). Dispara várias requisições
 * CHECK ao mesmo tempo, com números repetidos de propósito, e confere as respostas.
 *
 *   alvo "dados": fala direto com um Servidor de Dados e confere que nenhum
 *                 número foi inserido duas vezes e que as linhas são coerentes.
 *   alvo "app" ou "balanceador": fala com um Servidor de Aplicação (direto ou
 *                 via balanceador) e confere que toda
 *                 requisição recebeu RECEBIDO com o número certo (o resultado
 *                 aparece impresso no terminal do servidor de aplicação).
 *
 * Uso: java teste.TesteConcorrente <dados|app|balanceador> <servidor> <quantidade> <numeroMaximo>
 */
public class TesteConcorrente {

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Uso: java teste.TesteConcorrente <dados|app|balanceador> <servidor> <quantidade> <numeroMaximo>");
            return;
        }
        String alvo = args[0].toLowerCase();
        // App e Balanceador respondem igual (RECEBIDO;n); só o Servidor de Dados responde INSERIDO/EXISTE
        boolean alvoApp = !alvo.equals("dados");
        int servidor = Integer.parseInt(args[1]);
        int porta;
        String nome;
        switch (alvo) {
            case "balanceador": porta = Config.PORTA_BALANCEADOR; nome = "Balanceador"; break;
            case "app":         porta = Config.PORTAS_APP[servidor - 1]; nome = "App " + servidor; break;
            default:            porta = Config.PORTAS_DADOS[servidor - 1]; nome = "Dados " + servidor;
        }
        int quantidade = Integer.parseInt(args[2]);
        int maximo = Integer.parseInt(args[3]);

        System.out.println("Enviando " + quantidade + " requisições simultâneas (números 1-"
                + maximo + ") para " + nome + " (porta " + porta + ")...");

        ExecutorService pool = Executors.newFixedThreadPool(quantidade);
        int[] enviados = new int[quantidade];
        List<Future<String>> respostas = new ArrayList<>();
        long inicio = System.currentTimeMillis();
        for (int i = 0; i < quantidade; i++) {
            int numero = ThreadLocalRandom.current().nextInt(1, maximo + 1);
            enviados[i] = numero;
            respostas.add(pool.submit(() -> enviar(porta, numero)));
        }

        List<String> erros = new ArrayList<>();
        List<String> recebidas = new ArrayList<>();
        String[] textos = new String[quantidade];
        try {
            for (int i = 0; i < quantidade; i++) {
                try {
                    textos[i] = respostas.get(i).get();
                } catch (ExecutionException e) {
                    // Ex.: conexão recusada. Conta como falha em vez de derrubar o teste.
                    erros.add("requisição do número " + enviados[i] + " falhou: " + e.getCause());
                    System.out.println("  CHECK;" + enviados[i] + " -> FALHOU (" + e.getCause() + ")");
                    continue;
                }
                System.out.println("  CHECK;" + enviados[i] + " -> " + textos[i]);
                if (textos[i] == null) {
                    erros.add("requisição do número " + enviados[i] + " ficou sem resposta");
                } else {
                    recebidas.add(textos[i]);
                }
            }
        } finally {
            // Sempre encerra o pool: senão as threads dele mantêm o processo vivo para sempre
            pool.shutdownNow();
        }
        long tempo = System.currentTimeMillis() - inicio;

        System.out.println();
        if (alvoApp) {
            verificarApp(enviados, textos, erros);
            System.out.println("Respostas: " + recebidas.size() + " de " + quantidade
                    + ", tempo total: " + tempo + " ms");
        } else {
            verificarDados(recebidas, erros, tempo);
        }
        if (erros.isEmpty()) {
            System.out.println(alvoApp
                    ? "OK: todas as requisições foram confirmadas com RECEBIDO."
                    : "OK: nenhum número inserido duas vezes, linhas coerentes.");
        } else {
            System.out.println("FALHOU:");
            erros.forEach(e -> System.out.println("  - " + e));
        }
    }

    /** Cada requisição deve ter recebido RECEBIDO;<o mesmo número enviado>. */
    private static void verificarApp(int[] enviados, String[] textos, List<String> erros) {
        for (int i = 0; i < enviados.length; i++) {
            String resposta = textos[i];
            if (resposta == null) continue;
            String esperado = Protocolo.montar(Protocolo.RECEBIDO, enviados[i]);
            if (!resposta.equals(esperado)) {
                erros.add("esperava " + esperado + ", veio " + resposta);
            }
        }
    }

    /** Nenhum número INSERIDO duas vezes; cada número numa só linha e cada linha com um só número. */
    private static void verificarDados(List<String> respostas, List<String> erros, long tempo) {
        Map<Integer, Integer> insercoes = new HashMap<>();
        Map<Integer, Integer> linhaDoNumero = new HashMap<>();
        Map<Integer, Integer> numeroDaLinha = new HashMap<>();
        int inseridos = 0, existentes = 0;

        for (String resposta : respostas) {
            String[] c = Protocolo.separar(resposta);
            int numero = Integer.parseInt(c[1]);
            int linha = Integer.parseInt(c[2]);

            if (c[0].equals(Protocolo.INSERIDO)) {
                inseridos++;
                insercoes.merge(numero, 1, Integer::sum);
            } else {
                existentes++;
            }
            Integer linhaAnterior = linhaDoNumero.putIfAbsent(numero, linha);
            if (linhaAnterior != null && linhaAnterior != linha) {
                erros.add("número " + numero + " apareceu nas linhas " + linhaAnterior + " e " + linha);
            }
            Integer numeroAnterior = numeroDaLinha.putIfAbsent(linha, numero);
            if (numeroAnterior != null && numeroAnterior != numero) {
                erros.add("linha " + linha + " tem os números " + numeroAnterior + " e " + numero);
            }
        }
        insercoes.forEach((numero, vezes) -> {
            if (vezes > 1) erros.add("número " + numero + " foi INSERIDO " + vezes + " vezes");
        });

        System.out.println("Respostas: " + respostas.size() + " (" + inseridos + " INSERIDO, "
                + existentes + " EXISTE), números distintos: " + linhaDoNumero.size());
        System.out.println("Tempo total: " + tempo + " ms (sequencial levaria ~"
                + (respostas.size() * 200) + " ms em média)");
    }

    private static String enviar(int porta, int numero) throws Exception {
        try (Socket s = new Socket(Config.HOST, porta);
             PrintWriter out = new PrintWriter(s.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
            out.println(Protocolo.montar(Protocolo.CHECK, numero));
            return in.readLine();
        }
    }
}
