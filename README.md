# Apex Telemetry

Aplicativo Android de telemetria multi-jogo, engenharia de corrida e análise estratégica em tempo real para simuladores e jogos de corrida com saída externa (**EA Sports F1 23/24/25**, **Gran Turismo 7**, **Forza Motorsport & Horizon 5**, **Assetto Corsa & ACC**, **Project CARS 2 & Automobilista 2**), desenvolvido em **Kotlin** e **Jetpack Compose** com visual inspirado em centrais de *Pit Wall*.

---

## Visão Geral

O **Apex Telemetry** transforma o celular ou tablet em um painel de corrida **hands-free (zero interação durante a pilotagem)** e segundo monitor de engenharia. Ele detecta automaticamente pacotes UDP binários enviados na rede Wi-Fi local, adapta painéis, alertas e recomendações de setup à categoria do veículo (**Fórmula**, **GT / Endurance** ou **Rua / Turismo**) e oferece um **Simulador Ao Vivo** integrado para cada título suportado.

---

## Jogos & Protocolos UDP Suportados

O aplicativo conta com um **Seletor de Jogo com Detecção Automática (`AUTO`)**, escutando múltiplas portas simultaneamente via NIO `Selector` e identificando a assinatura binária do jogo conectado:

| Jogo | Categoria | Porta UDP | Protocolo & Particularidades |
| :--- | :--- | :--- | :--- |
| **EA Sports F1 25 / 24 / 23** | Fórmula | `20777` | Cabeçalho Little-Endian de 29 bytes (`packetFormat` 2023/2024/2025), grid de até 22 carros, ERS, DRS, desgaste e temperaturas |
| **Gran Turismo 7** | GT / Endurance | `33740` (RX) / `33739` (TX) | Pacote A de 296 bytes criptografado em **Salsa20** com *heartbeat* `'A'` periódico enviado ao IP do PlayStation |
| **Forza Motorsport (FM7 / FM23)** | GT / Endurance | `5300` | Fluxo *Data Out* (311B FM7 / 331B FM23 com desgaste de pneus), conversão °F → °C e m/s → km/h |
| **Forza Horizon 5** | Rua / Turismo | `5300` | Fluxo *Data Out* com deslocamento de +12 bytes da variante Horizon (323B/324B), turbo *boost* e freio de mão |
| **Assetto Corsa** | GT / Endurance | `9996` | Handshake UDP ativo (`operationID` 0, 1, 3), resposta UTF-16LE (408B) e `RTCarInfo` (328B) |
| **Assetto Corsa Competizione** | GT / Endurance | `9000` | Registro ativo *ACC Broadcasting* (`msgType=1`, protocolo 4) e atualizações em tempo real |
| **Project CARS 2 / Automobilista 2** | GT / Endurance | `5606` | Protocolo **SMS UDP v2** (`PacketBase` 12B + telemetria física completa, temperatura de freios e PSI) |

---

## Funcionalidades Principais

### 1. Dashboards Padrão Hands-Free por Jogo (Zero Scroll)
Ao iniciar uma sessão, a área superior da tela exibe sempre em primeiro plano um **HUD padrão dedicado e específico para o jogo ativo**, adaptando-se automaticamente entre **modo retrato** e **modo paisagem (2 colunas lado a lado)** para leitura periférica imediata sem rolagem ou toque na tela, além de contar com botão opcional de **Modo Cockpit em Tela Cheia**:
- **F1 23 / 24 / 25**: LEDs de troca de marcha, marcha central e velocidade, indicadores **DRS** e barra de **ERS %**, delta em tempo real, gap/box, composto/idade de pneu e setorização ao vivo **S1 / S2 / S3**.
- **Gran Turismo 7**: Marcha atual + **marcha sugerida (`SUG`)**, *boost* do turbo (`bar`), barra de combustível (`L` + voltas), temperatura de óleo/água e mapa térmico dos 4 pneus.
- **Assetto Corsa Competizione (ACC)**: Barra de RPM GT3, marcha, velocidade, intervenção ao vivo de **TC** e **ABS**, temperatura de freios e **4 pressões em `PSI` em destaque** para a janela ideal de 26.5–27.5 PSI.
- **Assetto Corsa (AC)**: Indicadores de TC/ABS, *slip* máximo, força G lateral/longitudinal, curso de suspensão D/T e progresso da volta.
- **Forza Motorsport (FM7 / FM23)**: Combustível (`%` + barra + voltas), *slip* de tração, melhor volta e temperaturas + desgaste nos 4 cantos.
- **Forza Horizon 5**: Turbo *boost*, indicador de **Freio de Mão / Drift**, embreagem, curso de suspensão e temperaturas Data Out nos 4 cantos.
- **Project CARS 2 & Automobilista 2**: Combustível com barra de nível, temperaturas de óleo/água, pressão de óleo, balanço de freio (`BB%`), PSI e desgaste.
- **Modo Tela Sempre Acesa (`KEEP_SCREEN_ON`)**: Mantém o visor ativo durante toda a corrida ou *stint* sem apagar a tela.

---

### 2. Recepção UDP Multi-Porta & Simulador Integrado
- **Detecção Automática (`AUTO`)**: Escuta simultaneamente `20777`, `5300`, `5606` e `33740` (além da porta personalizada configurada pelo usuário), alternando o perfil ativo assim que um pacote válido é reconhecido.
- **IP do Console/PC (Handshake Ativo)**: Permite informar o IP do PlayStation (GT7) ou PC (AC/ACC) para envio automático de *heartbeats* `'A'` e pacotes de registro/assinatura.
- **Modo Simulador Ao Vivo**: Cada jogo conta com perfil físico realista adaptado à sua categoria (**Fórmula**, **GT3/Endurance** ou **Turismo/Rua**), incluindo alternância de pistas e botão para simular sobrecarga térmica / VSC.

---

### 3. Condições do Veículo Adaptadas à Categoria
- **4 Cantos (Pneus & Freios)**: Cartões para `DE`, `DD`, `TE` e `TD` exibindo os sensores disponíveis no protocolo ativo — temperatura de superfície/núcleo (`°C`), pressão (`PSI`), desgaste (`%`), bolhas (*blisters*), temperatura de freios, curso de suspensão (`mm`) ou deslizamento (`Slip %`).
- **Módulo Híbrido vs Motor & Eletrônica**:
  - **Fórmula**: Bateria ERS (`%`), modo de entrega, combustível (`kg`), mistura e desgaste de ICE, MGU-K, turbo, câmbio e danos de asa/assoalho.
  - **GT / Turismo**: Turbo *Boost* (`bar`), temperatura de óleo e água, pressão de óleo, além dos níveis ativos de **TC** e **ABS**.

---

### 4. Pista, Grid ao Vivo & Estratégia
- **Mapa Vetorial da Pista**: Traçado do circuito em `Canvas` com movimentação dos carros em tempo real.
- **Classificação do Grid**: Nos jogos com grid completo (F1 23/24/25), exibe posição, equipe, melhor volta, boxes, punições, composto de pneus, *gap* e intervalo. Nos protocolos que enviam apenas o carro do jogador (GT7, Forza, AC, SMS v2), adapta a visualização para a posição e ritmo do jogador.
- **Janela Ideal de Pit Stop**: Projeta retorno na pista (`ar limpo` vs tráfego) e ganho estimado de *undercut* quando o jogo fornece grid e boxes.

---

### 5. Sistema de Alertas em Tempo Real
- **Ticker Auto-Dispensável (6 segundos)**: Alertas aparecem em faixa compacta no topo para não exigir toque do piloto durante a corrida e também ficam acessíveis na Central de Alertas.
- **Limites Adaptados à Categoria**:
  - **Fórmula**: Temperatura crítica de motor/freios de carbono, desgaste de pneus, bolhas, combustível baixo e pit barato em VSC/Safety Car.
  - **GT / Endurance**: Janela de pressão **GT3 (25.8–28.1 PSI)**, temperaturas de freio em aço/cerâmica, óleo/água e destracionamento (`Slip Ratio`).
  - **Rua / Turismo**: Deslizamento excessivo nas saídas de curva, temperatura de óleo/água e superaquecimento de pneus esportivos.

---

### 6. Consultor de Setup de Carro por Categoria
- Combina o **DNA da pista** (curvas, retas, elevação, agressividade de zebras) com a telemetria observada:
  - **Fórmula**: Asas dianteira/traseira, diferencial On/Off-Throttle, cambagem, convergência, suspensão, ARB, altura e pressões.
  - **GT / Endurance**: **Splitter Dianteiro**, Asa GT, níveis recomendados de **TC e ABS**, altura do solo em `mm` e pressão alvo quente **26.5–27.5 PSI**.
  - **Rua / Turismo**: *Lip* dianteiro / aerofólio, molas, altura esportiva e calibragem recomendada de pneus.

---

### 7. Histórico de Corridas na Nuvem & Comparação Lado a Lado
- **Login Google & Cloud Firestore**: Autenticação via *Jetpack Credential Manager* (`GetSignInWithGoogleOption`) e sincronização atômica em `/users/{userId}/sessions/{sessionId}` (incluindo `gameTitle`, `carCategory` e subcoleção `laps`).
- **Filtro por Jogo & Comparação**: Permite filtrar sessões salvas por jogo e comparar duas corridas lado a lado (**Jogo**, **Melhor Volta**, **Ritmo Médio**, **Velocidade Máxima**, **Consistência (%)**, **Posição/Boxes** e gráfico sobreposto de velocidade).

---

## Como Configurar a Conexão UDP

1. Conecte o dispositivo Android e o PC ou console à **mesma rede Wi-Fi**.
2. Toque no indicador de conexão no topo do **Apex Telemetry** para conferir o **IP Local** do aparelho e escolher **Detecção Automática (`AUTO`)** ou o jogo específico.
3. Caso utilize **Gran Turismo 7**, **Assetto Corsa** ou **ACC**, informe também o **IP do Console/PC** na janela de configuração UDP para que o app envie os pacotes de *heartbeat*/registro automaticamente.
4. No menu do jogo escolhido, ative a telemetria UDP para o **IP Local** exibido no app (portas `20777` para F1, `33740` para GT7, `5300` para Forza, `9996` para AC, `9000` para ACC ou `5606` para PC2/AMS2).

---

## Arquitetura & Stack Tecnológica

- **Linguagem & UI**: Kotlin, Jetpack Compose, Material Design 3 (tema escuro *Pit Wall* com fontes *Titillium Web*, *JetBrains Mono* e *Inter*), `Canvas` nativo e layout responsivo (`NavigationBar` / `NavigationRail`).
- **Camada UDP & Análise**: `UdpTelemetryReceiver` (NIO `Selector` multi-porta + `PacketAutoDetector`), `F125PacketParser`, `Gt7Salsa20Decoder`, `ForzaPacketParser`, `AssettoCorsaParsers`, `SmsPcars2Parser`, `GameCatalog`, `RaceSimulatorEngine`, `AlertEngine` e `SetupAdvisorEngine`.
- **Backend & Persistência**: Firebase Authentication (Google Sign-In via *Credential Manager*), Cloud Firestore (regras *zero-trust* por `uid` validadas no emulador) e *Jetpack DataStore Preferences*.
- **Testes**: Testes JUnit (`F125PacketParserAndAnalysisTest`, `MultiGameParsersTest`), testes Robolectric (`ExampleRobolectricTest`) e suíte `node:test` para as regras do Firestore (`firestore.test.js`).
