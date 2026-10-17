# Sistema Distribuído de Verificação de Primos

Projeto Desktop da disciplina de **Sistemas Distribuídos (2026/2)**.

Dois clientes geram números de 1 a 1000 sem parar. Um balanceador distribui as requisições entre três servidores de aplicação. Cada servidor de aplicação verifica se o número é primo e consulta um de dois servidores de dados, que guardam os números já vistos num arquivo replicado e consistente.

Tudo é escrito em Java puro, sem bibliotecas externas. Toda a comunicação usa **sockets TCP em `localhost`**, e portas diferentes fazem o papel de máquinas diferentes.

---

## Sumário

- [Arquitetura](#arquitetura)
- [Fluxo de uma requisição](#fluxo-de-uma-requisição)
- [Protocolo](#protocolo)
- [Portas](#portas)
- [Como executar](#como-executar)
- [Scripts](#scripts)
- [Decisões de design](#decisões-de-design)
- [Documentos](#documentos)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Situação das etapas](#situação-das-etapas)

---

## Arquitetura

```
 ┌───────────┐   ┌───────────┐
 │ Cliente 1 │   │ Cliente 2 │          log_cliente<id>.txt
 └─────┬─────┘   └─────┬─────┘
       │  CHECK;n      │
       └──────┬────────┘
              ▼
      ┌───────────────┐
      │  Balanceador  │  :5000   Round Robin, proxy
      └───────┬───────┘
     ┌────────┼────────┐
     ▼        ▼        ▼
 ┌───────┐┌───────┐┌───────┐
 │ App 1 ││ App 2 ││ App 3 │  :6001-6003   1 thread worker por requisição
 └───┬───┘└───┬───┘└───┬───┘
     └────────┼────────┘     sorteio aleatório do servidor de dados
        ┌─────┴─────┐
        ▼           ▼
   ┌─────────┐ ┌─────────┐
   │ Dados 1 │◄┤ Dados 2 │  :7001-7002 (workers)
   │         ├►│         │  :7101-7102 (exclusão mútua + replicação)
   └────┬────┘ └────┬────┘
   dados1.txt   dados2.txt   (mesmo conteúdo, sem duplicatas)
```

Cada elemento é um **executável independente**, com sua própria classe `main`:

| Elemento | Classe | Qtd. | Papel |
|---|---|---|---|
| Cliente | `cliente.Cliente <id>` | 2 | Sorteia um número, grava no log, envia, dorme de 50 a 200 ms e repete. |
| Balanceador | `balanceador.Balanceador` | 1 | Repassa cada requisição ao servidor de aplicação da vez (Round Robin). |
| Servidor de Aplicação | `aplicacao.ServidorAplicacao <id>` | 3 | Cria um worker por requisição: primalidade, consulta aos dados, impressão do resultado. |
| Servidor de Dados | `dados.ServidorDados <id>` | 2 | Mantém o arquivo de números e garante que os dois arquivos fiquem iguais. |

---

## Fluxo de uma requisição

1. **Cliente:** sorteia `n` entre 1 e 1000 e grava `n` em `logs/log_cliente<id>.txt` **antes** de enviar. Depois envia `CHECK;n` ao balanceador e espera `RECEBIDO;n`.
2. **Balanceador:** escolhe o próximo servidor de aplicação (Round Robin), repassa `CHECK;n` e devolve ao cliente o `RECEBIDO;n` que o servidor responder.
3. **Servidor de Aplicação:** responde `RECEBIDO;n` na hora e cria uma `Thread` worker, que:
   1. verifica a primalidade de `n`, por divisão até a raiz quadrada;
   2. sorteia um dos dois servidores de dados e envia `CHECK;n`;
   3. espera a resposta e imprime **uma** das frases:
      - `O número X é (não é) primo, não está presente no arquivo, e foi inserido na linha Y`
      - `O número X é (não é) primo, e já se encontra no arquivo na linha Z`
   4. termina.
4. **Servidor de Dados:** dorme de 100 a 300 ms e entra na seção crítica. Lá dentro, verifica se `n` já está no arquivo:
   - se está, responde `EXISTE;n;linha` sem escrever nada;
   - se não está, grava `n` numa nova linha, replica para o outro servidor de dados, espera o `ACK` e responde `INSERIDO;n;linha`.

   Por fim, sai da seção crítica.

> **Por que a verificação fica dentro da seção crítica?** Se ficasse fora, os dois servidores de dados poderiam concluir ao mesmo tempo que `n` não existe, e os dois o inseririam. O resultado seria uma duplicata.

---

## Protocolo

Texto simples, **uma mensagem por linha**, campos separados por `;`. As constantes ficam em [src/comum/Protocolo.java](src/comum/Protocolo.java).

| Direção | Mensagem | Significado |
|---|---|---|
| Cliente → Balanceador → App | `CHECK;<n>` | Pede o processamento de `n`. |
| App → Balanceador → Cliente | `RECEBIDO;<n>` | Confirma o recebimento, antes de processar. |
| Worker → Dados | `CHECK;<n>` | Consulta ou insere `n`. |
| Dados → Worker | `INSERIDO;<n>;<linha>` | `n` foi inserido agora, na linha indicada. |
| Dados → Worker | `EXISTE;<n>;<linha>` | `n` já estava no arquivo, na linha indicada. |
| Dados ↔ Dados | `REQUEST;<timestamp>;<id>` | Pedido de entrada na seção crítica (Ricart-Agrawala). |
| Dados ↔ Dados | `OK;<id>` | Permissão concedida. |
| Dados → Dados | `REPLICAR;<n>;<linha>` | Replica a escrita no par. |
| Dados → Dados | `ACK` | Confirma a replicação. |

Cada conexão leva **uma requisição só**: abre, envia, lê a resposta e fecha.

---

## Portas

Definidas em [src/comum/Config.java](src/comum/Config.java).

| Elemento | Porta(s) |
|---|---|
| Balanceador | `5000` |
| Servidores de Aplicação 1, 2, 3 | `6001`, `6002`, `6003` |
| Servidores de Dados 1, 2 (atendem os workers) | `7001`, `7002` |
| Sincronização entre os servidores de dados | `7101`, `7102` |

---

## Como executar

### Pré-requisitos

- **JDK 11 ou superior**, com `java` e `javac` no `PATH` (testado com o JDK 24).
- **Windows com PowerShell**, para usar os scripts. Sem eles, os comandos manuais abaixo funcionam em qualquer sistema.

Se o PowerShell bloquear os scripts, libere-os para a sessão atual:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
```

### Opção 1: tudo de uma vez (recomendado)

```powershell
.\scripts\SubirTudo.ps1           # compila e abre cada componente numa janela, na ordem certa
.\scripts\PararTudo.ps1           # para tudo sem perder requisições
```

O `SubirTudo` sobe as camadas na ordem **Dados → Aplicação → Balanceador → Clientes**. Antes de passar para a próxima, espera a porta da camada anterior abrir. Se alguma porta já estiver ocupada, ele avisa e não sobe nada.

Opções:

```powershell
.\scripts\SubirTudo.ps1 -Limpar        # apaga dados e logs antes de subir (execução do zero)
.\scripts\SubirTudo.ps1 -SemClientes   # só os servidores, para testar com Requisicoes/TesteConcorrente
```

### Opção 2: um terminal por componente

Abra um terminal para cada componente e suba **nesta ordem**:

```powershell
.\scripts\Dados1.ps1
.\scripts\Dados2.ps1
.\scripts\Aplicacao1.ps1
.\scripts\Aplicacao2.ps1
.\scripts\Aplicacao3.ps1
.\scripts\Balanceador.ps1
.\scripts\Cliente1.ps1
.\scripts\Cliente2.ps1
```

Cada script compila o projeto e sobe o componente. Para parar, use `Ctrl+C`. No cliente, o `Ctrl+C` termina a requisição em andamento antes de sair.

### Opção 3: manualmente, sem scripts

```bash
# Compilar (a partir da raiz do projeto)
javac -encoding UTF-8 -d out $(find src -name "*.java")

# Executar, cada um num terminal, nesta ordem
java -cp out dados.ServidorDados 1
java -cp out dados.ServidorDados 2
java -cp out aplicacao.ServidorAplicacao 1
java -cp out aplicacao.ServidorAplicacao 2
java -cp out aplicacao.ServidorAplicacao 3
java -cp out balanceador.Balanceador
java -cp out cliente.Cliente 1
java -cp out cliente.Cliente 2
```

> Execute sempre a partir da **raiz do projeto**: as pastas `dados/`, `logs/` e `out/` são resolvidas a partir do diretório atual.

### Arquivos gerados

| Arquivo | Quem gera | Conteúdo |
|---|---|---|
| `logs/log_cliente1.txt`, `logs/log_cliente2.txt` | Clientes | Um número por linha, um para cada sorteio, **incluindo repetidos**. |
| `dados/dados1.txt`, `dados/dados2.txt` | Servidores de dados | Um número por linha, **sem duplicatas**. A 1ª inserção vai na linha 1. |

Os arquivos de dados **persistem entre execuções**. Ao subir, o servidor relê o arquivo existente e reconstrói o índice em memória (número → linha). Para recomeçar do zero, pare tudo e rode:

```powershell
.\scripts\Limpar.ps1
```

O `Limpar` apaga dados **e** logs juntos de propósito. A validação compara uns com os outros, então os dois precisam ser da mesma execução.

---

## Scripts

Todos ficam em [scripts/](scripts/) e podem ser chamados de qualquer diretório.

| Script | O que faz |
|---|---|
| `SubirTudo.ps1` | Compila e sobe o sistema inteiro em janelas separadas (`-Limpar`, `-SemClientes`). |
| `PararTudo.ps1` | Para os clientes, espera os workers terminarem e só então fecha os servidores. |
| `Compilar.ps1` | Compila todo o `src/` para `out/`. |
| `Dados1.ps1`, `Dados2.ps1` | Compila e sobe um servidor de dados. |
| `Aplicacao1.ps1` … `Aplicacao3.ps1` | Compila e sobe um servidor de aplicação. |
| `Balanceador.ps1` | Compila e sobe o balanceador. |
| `Cliente1.ps1`, `Cliente2.ps1` | Compila e sobe um cliente. |
| `Limpar.ps1` | Apaga `dados/dados*.txt` e `logs/log_cliente*.txt`. |
| `Requisicoes.ps1` | Envia requisições **em sequência** a um alvo (`-Alvo Dados\|App\|Balanceador -Servidor N`). |
| `TesteConcorrente.ps1` | Dispara requisições **simultâneas** a um alvo e confere o resultado (sem duplicatas, todas confirmadas). |

Exemplos de teste:

```powershell
.\scripts\Requisicoes.ps1 -Numeros 37,50,37                    # 37 é inserido e depois aparece como já existente
.\scripts\Requisicoes.ps1 -Alvo Balanceador -Quantidade 30     # sistema completo, como um cliente faria
.\scripts\TesteConcorrente.ps1 -Quantidade 100 -Maximo 30      # concorrência direto no Dados 1
.\scripts\TesteConcorrente.ps1 -Alvo Balanceador -Quantidade 60
```

---

## Decisões de design

### Balanceamento: Round Robin

A carga é **homogênea**. Verificar a primalidade de números até 1000 é trivial, e o tempo de cada requisição é dominado pela espera no servidor de dados. Por isso, distribuir igualmente entre os três servidores de aplicação basta.

- **Implementação** ([src/balanceador/RoundRobin.java](src/balanceador/RoundRobin.java)): `AtomicInteger.getAndUpdate(i -> (i + 1) % 3)`. A operação é atômica, porque um `contador++` comum teria condição de corrida entre as threads do balanceador. Como o valor volta a zero a cada volta, o contador nunca estoura.
- **Por que não Least Connections:** as conexões entre o balanceador e o App duram poucos milissegundos (só até o `RECEBIDO`). A contagem de conexões abertas não refletiria a carga real, que está nos workers.
- **Desvio de falhas:** se o servidor da vez não aceita a conexão (timeout de 1 s) ou não responde (timeout de 5 s), o balanceador tenta o próximo da sequência. Isso é seguro porque `CHECK` é **idempotente**: processar o mesmo número de novo só gera um `EXISTE`.
- O log do balanceador mostra a contagem de requisições por App, o que permite comprovar a distribuição uniforme.

### Balanceador como proxy

O cliente conhece só a porta `5000`. O balanceador repassa `CHECK;n` ao App e devolve o `RECEBIDO;n` ao cliente. Comparado a um redirecionador, o proxy esconde os servidores internos, usa uma conexão só por requisição e deixa o desvio de falhas sob controle do balanceador. O gargalo típico de um proxy quase não aparece aqui, porque cada conexão dura só até o `RECEBIDO`.

### Consistência: Ricart-Agrawala + replicação síncrona

Os dois servidores de dados coordenam o acesso à seção crítica com o algoritmo de exclusão mútua distribuída de **Ricart-Agrawala**. Os pedidos levam o **relógio lógico de Lamport**, e o desempate é feito pelo timestamp e depois pelo id do servidor. Dentro da seção crítica, o servidor:

1. verifica se o número já existe;
2. se não existe, escreve no arquivo local;
3. envia `REPLICAR;n;linha` ao par e **espera o `ACK`**;
4. sai da seção crítica.

Nenhuma nova inserção acontece antes de a replicação terminar. Com isso, ao final os dois arquivos têm exatamente os mesmos valores, na mesma ordem e sem duplicatas. Requisições que chegam durante a sincronização ficam **bloqueadas na fila, nunca descartadas**.

A exclusão mútua fica isolada atrás da interface [ExclusaoMutua](src/dados/ExclusaoMutua.java) (`entrar()` / `sair()`). O servidor de dados só chama esses dois métodos. Se for preciso trocar de algoritmo (o plano B é token ring), basta escrever outra implementação.

Os detalhes do mecanismo (fundamentos, argumentos de correção, testes e limitações) estão no [documento de consistência](#consistenciapdf--consistenciatex).

### Desempenho e robustez

- **Sleep do servidor de dados fora da seção crítica.** Só a parte que precisa de exclusividade (verificar, escrever e replicar) é serializada. Com o sleep dentro, a vazão cairia para cerca de 5 req/s, contra cerca de 16 req/s gerados pelos clientes, e a fila cresceria sem limite.
- **Lock justo (FIFO)** na seção crítica: as requisições entram na ordem em que chegaram, e nenhuma espera indefinidamente.
- **Lock interno no arquivo** (métodos `synchronized` em [ArquivoDados](src/dados/ArquivoDados.java)), que protege o arquivo e o índice contra a thread que atende um worker e a que recebe um `REPLICAR` ao mesmo tempo.
- **Backlog de 1000 em todo `ServerSocket`** (`Config.BACKLOG`). Este bug foi encontrado em teste: no Windows, quando a fila padrão (50 conexões) enche numa rajada, o sistema operacional **recusa** as conexões excedentes (`Connection refused`). Com 300 conexões simultâneas o problema se reproduziu em 4 de 5 rodadas. Depois da correção, foram 5 de 5 rodadas sem falha e 1500 de 1500 frases impressas.
- **Worker = `new Thread` por requisição**, e não um pool, seguindo o enunciado ao pé da letra: a thread nasce com a requisição e termina depois de imprimir. A frase sai num único `println`, então frases de workers diferentes não se misturam.

### Nenhuma requisição perdida

| Ponto de falha | Tratamento |
|---|---|
| Cliente morre entre gravar no log e enviar | O `PararTudo` cria o arquivo-sinal `out/parar`, e os clientes só saem **entre** requisições. O `Ctrl+C` faz o mesmo via shutdown hook. |
| Balanceador ou App fora do ar | O cliente reenvia **o mesmo número**, sem regravar no log e sem sortear outro, com espera de 50 ms dobrando até 1 s, indefinidamente. Timeout de 10 s para o `RECEBIDO`. |
| App da vez não responde | O balanceador tenta o próximo App da sequência. |
| Servidor de dados sorteado fora do ar | O worker tenta o outro. Se nenhum responde, tenta de novo com espera crescente (50 ms dobrando até 1 s), indefinidamente, porque depois do `RECEBIDO` o cliente conta com o processamento. |
| Parada do sistema | O `PararTudo` para os clientes, espera 1 s sem conexões ativas nos servidores (os workers terminam o que já confirmaram) e só então fecha os servidores. |

---

## Documentos

A pasta [documents/](documents/) reúne o enunciado e a documentação escrita do projeto.

| Arquivo | O que é |
|---|---|
| [Projeto1SD-20262.pdf](documents/Projeto1SD-20262.pdf) | Enunciado oficial do trabalho. É a referência para os requisitos: elementos, tempos de espera, frases exatas e critérios de validação. |
| [consistencia.pdf](documents/consistencia.pdf) | Documento técnico sobre a arquitetura e o mecanismo de consistência, já compilado e pronto para leitura. |
| [consistencia.tex](documents/consistencia.tex) | Fonte LaTeX do documento acima. |
| [referentials.txt](documents/referentials.txt) | Arquivo reservado para anotar referências. Ainda está vazio. |

### `consistencia.pdf` / `consistencia.tex`

O documento se chama *Mecanismo de Consistência entre os Servidores de Dados: exclusão mútua distribuída (Ricart-Agrawala) e replicação síncrona*. O mecanismo de consistência vale 3 dos 10 pontos do trabalho e é o foco do texto. O restante do sistema aparece apenas como contexto.

| Seção | Conteúdo |
|---|---|
| 1. Introdução | Os três requisitos do enunciado sobre os arquivos de dados (disparar a consistência a cada escrita, não atender requisições durante a consistência, arquivos iguais e sem duplicatas) e a exigência de não perder requisições. |
| 2. Visão geral do sistema | Componentes, portas, protocolo de mensagens e decisões de projeto das outras camadas (cliente, balanceador, aplicação, dados). |
| 3. Fundamentos teóricos | O problema da exclusão mútua distribuída, relógios lógicos de Lamport, o algoritmo de Ricart-Agrawala, comparação com as alternativas (servidor central, token ring) e modelos de replicação e consistência. |
| 4. Mecanismo de consistência proposto | Fluxo de uma requisição e decisões de projeto: duas camadas de exclusão mútua, resposta adiada como thread em espera, toda requisição passando pela seção crítica, replicação síncrona com conferência de linha e o comportamento quando o par está fora do ar (consistência acima de disponibilidade, à luz do teorema CAP). Também mostra a estrutura da implementação. |
| 5. Argumentos de correção | Por que o mecanismo garante exclusão mútua, ausência de impasse e de inanição, e arquivos idênticos. |
| 6. Plano de testes | Como verificar o mecanismo na prática. |
| 7. Limitações conhecidas | O que o mecanismo não cobre e por quê. |
| 8. Perguntas prováveis na apresentação | Perguntas que a banca pode fazer, com as respostas, para preparar a defesa. |
| Referências | Lamport (1978), Ricart e Agrawala (1981), Coulouris *et al.*, Tanenbaum e Van Steen, Brewer (2000) e Gilbert e Lynch (2002). |

> O documento descreve o projeto da **Etapa 6**, que ainda está em implementação. Sempre que o código mudar, ele precisa ser atualizado junto. Antes de entregar, também falta preencher o nome da dupla no campo `\author`.

Para recompilar o PDF depois de editar o `.tex`, use qualquer distribuição LaTeX (MiKTeX, TeX Live) ou o Overleaf. Compile **duas vezes** para o sumário e as referências cruzadas saírem corretos:

```bash
cd documents
pdflatex consistencia.tex
pdflatex consistencia.tex
```

---

## Estrutura do projeto

```
src/
  comum/        Config.java (portas, tempos, pastas), Protocolo.java (mensagens)
  cliente/      Cliente.java
  balanceador/  Balanceador.java, RoundRobin.java
  aplicacao/    ServidorAplicacao.java, Worker.java, Primalidade.java
  dados/        ServidorDados.java, ArquivoDados.java, ExclusaoMutua.java,
                ExclusaoMutuaLocal.java (provisória, até a Etapa 6)
  teste/        TesteConcorrente.java (só para testes, não faz parte do sistema)
scripts/        scripts PowerShell (ver seção Scripts)
documents/      enunciado e documento do mecanismo de consistência (ver seção Documentos)
dados/          dados1.txt, dados2.txt (gerados em execução)
logs/           log_cliente1.txt, log_cliente2.txt (gerados em execução)
out/            classes compiladas, PIDs e arquivo-sinal de parada (gerados)
```

---

## Situação das etapas

| # | Etapa | Situação |
|---|---|---|
| 1 | Esqueleto, portas, protocolo e interface `ExclusaoMutua` | ✅ Feito |
| 2 | Servidor de Dados isolado (arquivo, sleep, existência e inserção) | ✅ Feito |
| 3 | Servidor de Aplicação (worker, primalidade, sorteio, mensagem exata) | ✅ Feito |
| 4 | Balanceador Round Robin (proxy, contador thread-safe) | ✅ Feito |
| 5 | Cliente (loop, log antes de enviar, sleep 50-200 ms) | ✅ Feito |
| 6 | Consistência: Ricart-Agrawala + `REPLICAR`/`ACK` entre os servidores de dados | ⏳ Em andamento |
| 7 | Script de validação: arquivos de dados idênticos, sem duplicatas, contendo todo número dos logs | ⏳ Pendente |
| 8 | Scripts para subir e parar tudo na ordem certa | ✅ Feito |

> **Atenção:** enquanto a Etapa 6 não estiver pronta, os servidores de dados usam a implementação provisória [ExclusaoMutuaLocal](src/dados/ExclusaoMutuaLocal.java). Ela garante exclusão só **dentro** de cada processo e não replica, então `dados1.txt` e `dados2.txt` ainda podem divergir.


