package balanceador;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Política de balanceamento Round Robin: devolve 0, 1, 2, 0, 1, 2, ...
 *
 * Thread-safe: várias threads do balanceador chamam proximo() ao mesmo tempo.
 * Com um int comum, "contador++" seria ler + somar + gravar, e duas threads
 * poderiam ler o mesmo valor e mandar duas requisições ao mesmo servidor.
 * O getAndUpdate do AtomicInteger faz a operação inteira de forma atômica, e o
 * valor fica sempre em 0..n-1, então nunca estoura.
 */
public class RoundRobin {

    private final int quantidade;
    private final AtomicInteger atual = new AtomicInteger(0);

    public RoundRobin(int quantidade) {
        this.quantidade = quantidade;
    }

    /** Índice do próximo servidor da vez. */
    public int proximo() {
        return atual.getAndUpdate(i -> (i + 1) % quantidade);
    }
}
