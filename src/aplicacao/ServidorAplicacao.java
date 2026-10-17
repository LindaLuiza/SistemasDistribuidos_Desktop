package aplicacao;

import comum.Config;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Servidor de Aplicação. Uso: java aplicacao.ServidorAplicacao <1|2|3>
 *
 * Para cada requisição recebida, abre uma NOVA thread worker (new Thread, não
 * um pool), como pede o enunciado: a thread nasce com a requisição e termina
 * depois de imprimir o resultado. O servidor não guarda estado.
 */
public class ServidorAplicacao {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Uso: java aplicacao.ServidorAplicacao <1|2|3>");
            return;
        }
        int id = Integer.parseInt(args[0]);
        int porta = Config.PORTAS_APP[id - 1];
        System.out.println("[App " + id + "] iniciando na porta " + porta);

        try (ServerSocket servidor = new ServerSocket(porta, Config.BACKLOG)) {
            while (true) {
                Socket conexao = servidor.accept();
                new Thread(new Worker(id, conexao)).start();
            }
        }
    }
}
