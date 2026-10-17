package aplicacao;

/**
 * Teste de primalidade por divisão até a raiz quadrada de n.
 *
 * Se n tem um divisor d > raiz(n), então n/d < raiz(n) também é divisor, então
 * basta testar até a raiz. Depois do 2, só os ímpares precisam ser testados.
 * Para n <= 1000 são no máximo ~16 divisões.
 */
public final class Primalidade {

    private Primalidade() {}

    public static boolean ehPrimo(int n) {
        if (n < 2) return false;          // 0 e 1 não são primos
        if (n == 2) return true;
        if (n % 2 == 0) return false;
        for (int i = 3; i * i <= n; i += 2) {
            if (n % i == 0) return false;
        }
        return true;
    }
}
