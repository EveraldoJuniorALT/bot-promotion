# 🤖 Garimpo de Ofertas - Bot (AliExpress Affiliate Automation)

![Java](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-brightgreen.svg)
![Appium](https://img.shields.io/badge/Appium-Mobile%20Automation-blue)
![Telegram API](https://img.shields.io/badge/Telegram-Bot%20API-informational)
![MySQL](https://img.shields.io/badge/MySQL-Database-lightgrey)

Um sistema de automação avançado e de alta performance desenvolvido em Java e Spring Boot para rastrear, processar e publicar ofertas do AliExpress (Programa de Afiliados) diretamente em canais do Telegram. 

O projeto se destaca por sua arquitetura multi-thread, processamento assíncrono inteligente e orquestração automática de emuladores Android para extração de dados invisíveis via API.

---

## ✨ Principais Funcionalidades

* **🔍 Busca Inteligente de Ofertas:** Integração nativa com o SDK do AliExpress para buscar "Hot Products", detalhes de SKU e informações de frete.
* **📱 Extração de Moedas via Appium (Web Scraping Mobile):** Utiliza automação UIAutomator2 conectada ao MuMu Player para abrir o app do AliExpress e raspar a porcentagem de desconto real de moedas que a API não fornece.
* **🚀 Pool de Emuladores Dinâmico:** Gerenciamento de múltiplas instâncias de emuladores operando simultaneamente, eliminando gargalos e permitindo escalabilidade horizontal (basta alterar uma variável para rodar com 2, 5 ou 10 emuladores).
* **⚡ Arquitetura "Fast Lane" e "Slow Lane":** Processamento assíncrono inteligente que impede que produtos novos (processamento em milissegundos) fiquem presos atrás de produtos antigos que exigem processamento mobile no Appium (10 a 30 segundos).
* **🤖 Orquestração de Ambiente Auto-Gerenciável:** O sistema liga automaticamente os servidores Appium, inicializa os emuladores MuMu Player, descobre as portas ADB dinamicamente e aguarda o boot do Android antes de iniciar o bot.
* **📢 Postagem Integrada ao Telegram:** Formatação rica de mensagens com imagens, cálculos de preço final, cupons e links de afiliado convertidos automaticamente.
* **📊 Banco de Dados & Histórico de Preços:** Salva entidades de produtos e variações no MySQL para calcular o preço médio e garantir que apenas ofertas reais (abaixo da média) sejam publicadas.

---

## 🏗️ Arquitetura do Sistema

O projeto adota os princípios **SOLID**, **KISS** e **Clean Code**, separando as responsabilidades em serviços dedicados:

1.  **Schedulers:** Acionam rotinas automáticas de busca de cupons, atualização de tokens e busca de produtos (`ProductSearchScheduler`).
2.  **API Client:** Camada de comunicação com a AliExpress (`AliexpressApiClient`, `SkuProductInfo`, etc).
3.  **Appium Pool:** `EmulatorPoolManager` gerencia uma fila bloqueante (`BlockingQueue`) de `AndroidDriver`, entregando emuladores prontos para as threads de processamento e recolhendo-os após o uso.
4.  **ProductService (Core):** Filtra marcas/modelos, divide a carga entre a via rápida e lenta, e delega o processamento concorrente para os `Executors`.
5.  **Telegram Layer:** Formata a mensagem final e envia para o canal primário (totalmente automatizado) ou secundário (triagem manual).

---

## 🛠️ Tecnologias Utilizadas

* **Backend:** Java 21, Spring Boot 3.5.5, Spring Data JPA, Hibernate, Lombok.
* **Concorrência:** Java `CompletableFuture`, `ThreadPoolTaskExecutor`.
* **Automação:** Appium (Node.js), Selenium WebDriver, MuMu Player 12, ADB.
* **Banco de Dados:** MySQL 8.
* **Integrações:** Telegram Bots API, BCB API, AliExpress Open Platform SDK.

---

## ⚙️ Pré-requisitos

Para rodar este projeto localmente, você precisará de:

1.  **Java JDK 21+**
2.  **MySQL Server** (rodando na porta padrão 3306).
3.  **Node.js e Appium CLI** (`npm install -g appium`).
4.  **Driver UIAutomator2** instalado no Appium (`appium driver install uiautomator2`).
5.  **MuMu Player 12** instalado no diretório padrão (`C:\Program Files\Netease\MuMuPlayer\`).
    * *Importante:* Crie as instâncias no MuMu Multi-Drive, ative o Modo Desenvolvedor/Depuração USB, defina a mesma resolução para todas as instâncias e certifique-se de que o aplicativo do AliExpress está instalado e logado.

---

## 🚀 Como Executar

**1. Clone o repositório**
```bash
git clone [https://github.com/everaldojunioralt/bot-promotion.git](https://github.com/everaldojunioralt/bot-promotion.git)
cd bot-promotion

2. Configure o Banco de Dados
Crie um banco de dados no MySQL chamado aliexpress_db. O Hibernate criará as tabelas automaticamente.

3. Configure o arquivo application.properties
Preencha as variáveis sensíveis no arquivo src/main/resources/application.properties:

Properties
# Credenciais AliExpress
aliexpress.app.key=SEU_APP_KEY
aliexpress.app.secret=SEU_APP_SECRET
aliexpress.app.tracking-id=SEU_TRACKING_ID

# Credenciais Telegram
telegram.bot.name=NOME_DO_BOT
telegram.bot.token=SEU_TOKEN_DO_TELEGRAM
telegram.bot.chat-id=-ID_CANAL_SECUNDARIO
telegram.bot.chat-id-priority=-ID_CANAL_PRIMARIO

# Credenciais Banco de Dados
spring.datasource.username=root
spring.datasource.password=sua_senha
Nota: Não é necessário configurar portas do Appium ou Emuladores no properties. A classe EnvironmentManager cuidará da descoberta dinâmica via ADB e injetará as propriedades no Spring Boot durante o startup.

4. Execute o projeto
Certifique-se de fechar instâncias fantasmas do Node.js ou MuMu. Inicie a aplicação via IDE (IntelliJ/Eclipse) ou via Maven:

Bash
./mvnw spring-boot:run
Acompanhe os logs. O sistema fará a limpeza de processos, ligará os servidores Appium, inicializará os emuladores, aguardará o sistema operacional Android carregar e, em seguida, começará a buscar e postar os produtos!

📈 Status do Projeto
⏳ Status: 95% Concluído.
O núcleo (Core), integração com APIs, banco de dados, pool de automação mobile e regras de negócio assíncronas estão totalmente implementadas, estabilizadas e com alta performance.
