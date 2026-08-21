# ZavynCore

Sistema central da rede **Zavyn**, para rodar **exclusivamente no Velocity** (proxy).
Nao e um plugin Bukkit/Paper — os servidores Lobby, FullPvP, BedWars etc. **nao precisam**
instalar nada, tudo e controlado no proxy.

- Proxy alvo: **Velocity `4.1.0-SNAPSHOT`** (API atual do PaperMC/Velocity em desenvolvimento)
- Java: **21+** (o pedido original citava "Java 25+"; no momento desta entrega o release publico
  mais recente do OpenJDK e o 21 LTS, que e o que este ambiente possui instalado. O projeto usa
  apenas recursos de linguagem compativeis com Java 21 (records, switch expressions, text blocks,
  var) e compila normalmente em Java 25 quando esse JDK estiver disponivel — basta ajustar
  `maven.compiler.release` no `pom.xml`.)

---

## 1. Arquitetura

```
Jogador
   |
   v
Velocity + ZavynCore  ---->  MySQL/MariaDB (bans, mutes, warns, contas, sessoes, logs)
   |
   +--> Lobby (Paper)
   +--> FullPvP (Paper)
   +--> BedWars (Paper, futuro)
```

Todas as punicoes (ban, mute, warn, ipban) e todo o sistema de autenticacao vivem no proxy.
Um jogador banido no FullPvP nao consegue entrar em nenhum outro servidor da rede, porque o
Velocity nega a conexao antes mesmo dele chegar em qualquer Paper. O mesmo vale para mute:
o chat e interceptado no proxy (`PlayerChatEvent`), entao o mute vale em qualquer servidor.

Comandos globais (`/ban`, `/mute`, `/kick`, `/warn`, ...) sao registrados no `CommandManager`
do Velocity e funcionam de qualquer servidor. Comandos especificos de um servidor (ex:
`/resetmine` no FullPvP) **nao sao tocados** pelo ZavynCore — eles continuam sendo comandos
normais do Paper, registrados la.

---

## 2. Modulos do projeto

```
net.zavyn.zavyncore
 |- ZavynCore.java                 Classe principal do plugin (@Plugin), monta tudo
 |- config/                        Leitura de config.yml e messages.yml (SnakeYAML)
 |- database/                      HikariCP + criacao automatica do schema (Database.java)
 |    `- dao/                      Acesso a dados (PlayerDao, PunishmentDao, WarnDao, IpBanDao, LogDao)
 |- model/                         Classes de dominio (Punishment, Warning, PlayerAccount, ...)
 |- service/                       Regras de negocio (bans, mutes, warns, login, limite de contas, alts)
 |- integration/                   FloodgateIntegration.java (deteccao de Bedrock, soft-depend)
 |- listener/                      ConnectionListener (login/ban/limite/confirmacao) e ChatListener (mute)
 `- command/                       Um comando por classe (SimpleCommand)
```

---

## 3. Limitacoes reais da API e como foram tratadas

O pedido original pede para eu ser honesto quando algo nao pode ser implementado exatamente
como descrito. Pontos relevantes:

1. **Diferenciar premium vs offline com 100% de certeza.**
   O Velocity nao expoe um "isPremium()" direto no objeto `Player`. A forma correta e capturar
   `GameProfileRequestEvent#isOnlineMode()` (disparado antes do login) e guardar esse valor
   (feito em `ConnectionListener.onGameProfileRequest`). Isso reflete exatamente se o cliente
   autenticou com sucesso no servico de sessao da Mojang/Microsoft — e a mesma fonte de verdade
   que o proprio Velocity usa. Nao ha "invencao" de deteccao aqui.

2. **Detectar jogadores Bedrock/Floodgate.**
   Usamos exclusivamente `FloodgateApi.getInstance().isFloodgateId(uuid)` (API oficial). O
   Floodgate e uma dependencia `provided` (soft-depend): se ele nao estiver instalado, o
   ZavynCore **continua funcionando normalmente**, apenas loga um aviso e trata todo mundo como
   premium/offline conforme o modo do proxy (ver `FloodgateIntegration.java`).

3. **Atomicidade do limite de contas por IP (item 27 do pedido).**
   Isso e o ponto mais delicado tecnicamente. A solucao usa duas camadas:
   - Um `ReentrantLock` em memoria por IP, que serializa checagens concorrentes dentro do mesmo
     processo Velocity (cobre o caso mais comum: duas conexoes quase simultaneas no mesmo proxy).
   - Uma transacao MySQL que faz `SELECT ... WHERE ip = ? FOR UPDATE` sobre uma coluna indexada
     (`player_ips.ip`) e, dentro da **mesma transacao**, insere o novo vinculo `uuid<->ip` antes
     do commit. Sob o isolamento padrao do InnoDB (REPEATABLE READ), essa consulta adquire um
     **gap lock** no indice, bloqueando `INSERT`s concorrentes com o mesmo IP ate o commit. Isso
     garante a atomicidade mesmo com **multiplas instancias de Velocity** apontando pro mesmo
     banco (preparando o terreno pro item 22 - sincronizacao multi-proxy).
   - Ver `AccountLimitService.java` e `PlayerDao.countOfflineAccountsForIp(...)`.

4. **VPN / CGNAT / IP compartilhado no `/banip`.**
   Nao existe forma tecnica de, usando apenas APIs do Velocity/Floodgate, saber com certeza se
   um IP e uma VPN, um CGNAT de operadora ou uma casa com varias pessoas. O plugin **nao tenta
   adivinhar isso** (seria "inventar deteccao", que o pedido pede pra evitar). Em vez disso:
   - `/banip` e um comando administrativo explicito, feito para ser usado com julgamento humano.
   - `ban-also-ip` e `false` por padrao — banir IP junto com a conta e opt-in.
   - `/alts` e `/ipaccounts` existem justamente para o staff **investigar antes de agir**, ao
     inves do sistema banir automaticamente qualquer coisa parecida com evasao.
   Se no futuro voces quiserem deteccao de VPN/proxy, isso exige um servico externo de
   reputacao de IP (pago ou nao) — nao ha API gratuita e confiavel embutida no Velocity/Floodgate
   pra isso, entao ficou fora do escopo por ser, na pratica, uma integracao com terceiros.

5. **Bloquear jogador offline nao logado de andar/interagir no Lobby.**
   O Velocity, por design, so controla **conexao** e **chat/plugin messages** — ele nao
   controla movimento, quebra de bloco, inventario etc. dentro de um servidor Paper (isso e
   territorio do proprio servidor). O ZavynCore trava corretamente o **chat** (via
   `ChatListener`) e o **login em si** (o jogador so e considerado "autenticado" apos
   `/login`/`/register`). Para travar fisicamente o jogador no Lobby (nao andar, nao abrir
   inventario) antes do login, seria necessario um mini-plugin no lado Paper que consulte esse
   estado — isso esta fora do que o Velocity consegue fazer sozinho, e por isso nao foi
   inventada uma solucao falsa. O codigo ja deixa esse ponto de extensao comentado em
   `ConnectionListener`.

---

## 4. Compilando

Este ambiente de geracao de codigo **nao tem acesso a rede**, entao o projeto **nao pode ser
compilado aqui** para validacao final — ele foi escrito e revisado manualmente (imports,
assinaturas de metodo, tipos, chaves/parenteses) mas recomendo fortemente rodar uma build local
antes de subir em producao.

```bash
# Requisitos: JDK 21+ (ou 25+, ajustando o pom.xml) e Maven 3.9+
cd zavyncore
mvn clean package
```

O jar final fica em `target/ZavynCore-1.0.0.jar`.

Se estiver usando **JDK 23+**, o Maven pode precisar do annotation processor do Velocity
declarado explicitamente (ja esta no `pom.xml`, na secao `maven-compiler-plugin`).

---

## 5. Instalando no Velocity

1. Copie `ZavynCore-1.0.0.jar` para a pasta `plugins/` do seu Velocity.
2. Reinicie o proxy. Na primeira execucao, o ZavynCore vai:
   - Criar `plugins/zavyncore/config.yml` e `plugins/zavyncore/messages.yml`.
   - Tentar conectar no MySQL usando os dados padrao de `config.yml` (que provavelmente vao
     falhar — edite antes de usar em producao).
3. Edite `plugins/zavyncore/config.yml` com os dados reais do seu banco (secao 6).
4. Rode `/zavyncore reload` ou reinicie o proxy novamente.
5. Confirme com `/zavyncore database` que a conexao esta OK.

**Importante:** se o seu Velocity estiver em `online-mode: false` mas os servidores Paper
usarem forwarding do tipo `modern`/`velocity`, garanta que o `forwarding-secret` esteja
corretamente configurado entre proxy e Paper — isso e configuracao padrao do proprio Velocity,
nao do ZavynCore, mas e essencial pra o ZavynCore confiar no UUID/IP que recebe.

---

## 6. Configurando o MySQL/MariaDB

```sql
CREATE DATABASE zavyncore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'zavyncore'@'localhost' IDENTIFIED BY 'uma_senha_forte_aqui';
GRANT ALL PRIVILEGES ON zavyncore.* TO 'zavyncore'@'localhost';
FLUSH PRIVILEGES;
```

Depois, em `config.yml`:

```yaml
database:
  type: mysql
  host: localhost
  port: 3306
  database: zavyncore
  username: zavyncore
  password: "uma_senha_forte_aqui"
  pool:
    maximum-pool-size: 10
```

As tabelas sao criadas automaticamente pelo plugin (`Database.migrate()`). O arquivo
`sql/schema.sql` neste projeto e apenas uma referencia caso queira inspecionar ou provisionar
manualmente.

---

## 7. Configurando o LuckPerms

O ZavynCore **nao implementa permissoes proprias** — ele usa exclusivamente o sistema de
permissoes do Velocity (`CommandSource#hasPermission`), que o LuckPerms ja substitui
automaticamente quando instalado no proxy. Basta:

1. Instalar o LuckPerms para Velocity normalmente (`plugins/LuckPerms-Velocity-x.x.x.jar`).
2. Dar as permissoes da secao 9 abaixo aos grupos de staff, ex:

```
/lpv group admin permission set zavyncore.* true
/lpv group mod permission set zavyncore.ban true
/lpv group mod permission set zavyncore.mute true
/lpv group mod permission set zavyncore.kick true
/lpv group mod permission set zavyncore.warn true
```

Nao ha nenhuma configuracao adicional necessaria no `config.yml` do ZavynCore para o
LuckPerms funcionar — a integracao e transparente via API nativa do Velocity.

---

## 8. Configurando o Geyser/Floodgate

1. Instale o Geyser-Velocity e o Floodgate-Velocity normalmente na pasta `plugins/` do proxy
   (e configure o Geyser standalone ou embutido, conforme sua topologia).
2. O ZavynCore detecta o Floodgate automaticamente no boot e imprime no console:
   - `Floodgate detectado - recursos de deteccao Bedrock ativados.` ou
   - `Floodgate NAO detectado - jogadores Bedrock serao tratados de acordo com o modo online...`
3. Nao ha configuracao adicional no `config.yml` do ZavynCore — apenas os campos
   `authentication.floodgate-auto-login` e `account-limit.floodgate-limit` controlam o
   comportamento para esses jogadores.

---

## 9. Permissoes

| Permissao                         | Descricao                                      |
|-----------------------------------|-------------------------------------------------|
| `zavyncore.ban`                   | `/ban`                                          |
| `zavyncore.tempban`               | `/tempban`                                      |
| `zavyncore.unban`                 | `/unban`                                        |
| `zavyncore.mute`                  | `/mute`, `/checkmute`                           |
| `zavyncore.tempmute`              | `/tempmute`                                     |
| `zavyncore.unmute`                | `/unmute`                                       |
| `zavyncore.kick`                  | `/kick`                                         |
| `zavyncore.warn`                  | `/warn`, `/warnings`, `/clearwarnings`          |
| `zavyncore.ipban`                 | `/banip`                                        |
| `zavyncore.unipban`               | `/unbanip`                                      |
| `zavyncore.history`               | `/checkban`, `/history`                         |
| `zavyncore.accountlimit.bypass`   | Ignora o limite de contas por IP; ve `/accounts`|
| `zavyncore.password.set`          | `/setpassword`                                  |
| `zavyncore.password.reset`        | `/resetpassword`                                |
| `zavyncore.admin`                 | `/zavyncore ...`, `/alts`, `/ipaccounts`        |
| `zavyncore.*`                     | Todas as permissoes acima                       |

---

## 10. Comandos

**Globais (funcionam em qualquer servidor atras do Velocity):**

```
/ban <jogador> [duracao] [motivo]
/tempban <jogador> <duracao> [motivo]
/unban <jogador>
/banip <ip> [motivo]
/unbanip <ip>
/checkban <jogador>
/history <jogador>

/mute <jogador> [duracao] [motivo]
/tempmute <jogador> <duracao> [motivo]
/unmute <jogador>
/checkmute <jogador>

/kick <jogador> [motivo]

/warn <jogador> [motivo]
/warnings <jogador>
/clearwarnings <jogador>

/register <senha> <senha>
/login <senha>
/changepassword <senha_antiga> <senha_nova>
/unregister <senha>
/logout

/setpassword <jogador> <nova_senha>
/resetpassword <jogador>

/accounts <jogador>
/ipaccounts <ip>
/alts <jogador>

/zavyncore reload
/zavyncore info
/zavyncore debug
/zavyncore database
/zavyncore version
```

`/server` e um comando **nativo do Velocity** e continua funcionando normalmente sem qualquer
interferencia do ZavynCore.

Formatos de duracao aceitos: `s`, `m`, `h`, `d`, `w`, `mo` (ex: `30m`, `2h`, `7d`, `30d`, `1mo`),
ou `perm`/`permanent`/`-1` para permanente.

---

## 11. Exemplo de `config.yml`

Veja `src/main/resources/config.yml` (copiado automaticamente para
`plugins/zavyncore/config.yml` na primeira execucao) — todas as opcoes do pedido original estao
la: `database`, `account-limit`, `authentication`, `new-ip`, `security`, `punishment`,
`warnings.auto-actions` e `messages.prefix`.

## 12. Exemplo de `messages.yml`

Veja `src/main/resources/messages.yml` — todas as mensagens usam **MiniMessage** e suportam os
placeholders `{player}`, `{reason}`, `{duration}`, `{punishment_id}`, `{staff}`, `{ip}`,
`{total}`, `{error}`, `{new_password}`, entre outros, dependendo da mensagem.

---

## 13. Seguranca

- Senhas sao hasheadas com **Argon2id** (`de.mkammerer:argon2-jvm`), nunca armazenadas ou
  logadas em texto puro. O `char[]` da senha e sempre zerado (`wipeArray`) apos o uso.
- Todas as queries usam `PreparedStatement` (sem concatenacao de SQL).
- O IP de conexao vem de `Player#getRemoteAddress()`, que reflete o forwarding validado pelo
  proprio Velocity — o plugin nao confia em nenhum header/IP enviado pelo cliente.
- `/setpassword` e `/resetpassword` nunca leem nem exibem a senha antiga — apenas substituem.
- Logs administrativos (`admin_logs`) nunca recebem senha em nenhum campo.
