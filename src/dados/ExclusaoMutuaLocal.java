package dados;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementação provisória de ExclusaoMutua (Etapa 2): vale só dentro deste
 * processo. Será substituída por Ricart-Agrawala na Etapa 6, sem alterar o
 * ServidorDados.
 *
 * O lock é justo (fair = true): as threads entram na ordem em que chegaram,
 * então nenhuma requisição fica esperando indefinidamente.
 */
public class ExclusaoMutuaLocal implements ExclusaoMutua {

    private final ReentrantLock lock = new ReentrantLock(true);

    @Override
    public void entrar() throws InterruptedException {
        lock.lockInterruptibly();
    }

    @Override
    public void sair() {
        lock.unlock();
    }
}
