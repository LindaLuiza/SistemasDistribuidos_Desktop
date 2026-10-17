package dados;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Arquivo local de um servidor de dados: um número por linha, sem duplicatas.
 *
 * O arquivo é a fonte da verdade. Na inicialização ele é lido (se existir) e
 * um índice em memória (número -> linha) é reconstruído, para que a verificação
 * de existência seja O(1) em vez de reler o arquivo a cada requisição.
 * O servidor nunca apaga o arquivo: para recomeçar do zero, use o script limpar.
 *
 * Os métodos são synchronized: é o lock interno do arquivo. Ele protege o
 * índice e o arquivo contra acessos simultâneos de threads diferentes do mesmo
 * processo (na Etapa 6: a thread que atende um worker e a que recebe REPLICAR).
 */
public class ArquivoDados {

    /** Resultado de uma consulta: se o número foi inserido agora e em qual linha está. */
    public static final class Resultado {
        public final boolean inserido;
        public final int linha;

        Resultado(boolean inserido, int linha) {
            this.inserido = inserido;
            this.linha = linha;
        }
    }

    private final File arquivo;
    private final Map<Integer, Integer> indice = new HashMap<>();
    private int totalLinhas = 0;

    public ArquivoDados(String caminho) throws IOException {
        this.arquivo = new File(caminho);
        carregar();
    }

    /** Lê o arquivo existente (se houver) e reconstrói o índice. */
    private void carregar() throws IOException {
        if (!arquivo.exists()) {
            File pasta = arquivo.getParentFile();
            if (pasta != null) pasta.mkdirs();
            arquivo.createNewFile();
            return;
        }
        try (BufferedReader in = new BufferedReader(new FileReader(arquivo))) {
            String linha;
            while ((linha = in.readLine()) != null) {
                linha = linha.trim();
                if (linha.isEmpty()) continue;
                totalLinhas++;
                indice.putIfAbsent(Integer.parseInt(linha), totalLinhas);
            }
        }
    }

    /**
     * Passo ii do enunciado: se o número já existe, devolve a linha dele sem
     * escrever; senão, grava em uma nova linha (a 1ª inserção vai na linha 1).
     */
    public synchronized Resultado consultarOuInserir(int numero) throws IOException {
        Integer linhaExistente = indice.get(numero);
        if (linhaExistente != null) {
            return new Resultado(false, linhaExistente);
        }
        int novaLinha = totalLinhas + 1;
        gravar(numero);
        indice.put(numero, novaLinha);
        totalLinhas = novaLinha;
        return new Resultado(true, novaLinha);
    }

    /** Número de linhas gravadas até agora. */
    public synchronized int getTotalLinhas() {
        return totalLinhas;
    }

    /** Acrescenta uma linha ao final do arquivo, com flush imediato. */
    private void gravar(int numero) throws IOException {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(arquivo, true))) {
            out.write(Integer.toString(numero));
            out.newLine();
        }
    }
}
