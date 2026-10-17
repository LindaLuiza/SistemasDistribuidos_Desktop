package dados;

/**
 * Abstração da exclusão mútua entre os servidores de dados.
 *
 * O ServidorDados só conhece esta interface. Hoje a implementação planejada é
 * Ricart-Agrawala (relógio de Lamport); se for preciso migrar, por exemplo
 * para token ring, basta trocar a implementação, sem mexer no resto do servidor.
 *
 * Uso esperado:
 *   exclusao.entrar();
 *   try {
 *       // verificar arquivo, escrever, replicar, aguardar ACK
 *   } finally {
 *       exclusao.sair();
 *   }
 */
public interface ExclusaoMutua {

    /** Bloqueia até obter acesso exclusivo à seção crítica (em todos os servidores de dados). */
    void entrar() throws InterruptedException;

    /** Libera a seção crítica. */
    void sair();
}
