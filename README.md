# Apex Telemetry 25

Aplicativo Android de telemetria, engenharia de corrida e análise estratégica em tempo real para o **EA Sports F1 25**, desenvolvido em **Kotlin** e **Jetpack Compose** com identidade visual escura inspirada em centrais de *Pit Wall* e grafismos de transmissão da Fórmula 1.

---

## Visão Geral

O **Apex Telemetry 25** funciona como um segundo monitor de engenharia de corrida ao lado do volante ou controle. Ele recebe pacotes UDP binários diretamente do F1 25 pela rede Wi-Fi local (porta padrão `20777`) e também conta com um **Simulador Ao Vivo** integrado para testes imediatos de todos os painéis, gráficos, alertas e recomendações de setup sem necessidade de conexão com o jogo.

---

## Funcionalidades

### 1. Recepção UDP F1 25 & Simulador Integrado
- **Decodificador Binário F1 25 (`20777`)**: Leitura em *Little-Endian* do cabeçalho padrão de 29 bytes e dos principais pacotes da especificação 2025:
  - `Motion (ID 0)`: coordenadas de pista (`worldPositionX`, `worldPositionZ`) e forças G lateral/longitudinal.
  - `Session (ID 1)`: circuito, tipo de sessão, clima, temperaturas de pista/ar e bandeiras/Safety Car.
  - `Lap Data (ID 2)`: tempos de volta e setores, distância na pista, posições, intervalos, paradas nos boxes e punições.
  - `Participants (ID 4)`: nomes, siglas e equipes dos pilotos no grid.
  - `Car Telemetry (ID 6)`: velocidade, acelerador, freio, esterçamento, embreagem, marcha, RPM, LEDs de troca, DRS, temperaturas de freios/pneus/motor e pressões em PSI.
  - `Car Status (ID 7)`: combustível, autonomia estimada, mistura, balanço de freio, composto/idade dos pneus e energia/modo do ERS.
  - `Car Damage (ID 10)`: desgaste dos pneus, bolhas (*tyre blisters*), danos de asa dianteira/assoalho e desgaste de ICE, MGU-K, turbo e câmbio.
- **Descoberta Automática de IP Local**: Exibe o endereço IPv4 do aparelho e permite alterar a porta UDP a qualquer momento (persistida com *Jetpack DataStore*).
- **Modo Simulador Ao Vivo**: Simulação contínua a 20 Hz com 20 carros na pista e alternância entre **Interlagos**, **Spa-Francorchamps**, **Silverstone**, **Monza** e **Suzuka**.

---

### 2. Painel de Telemetria & Gráficos em Tempo Real
- **HUD Estilo Volante**: Barra superior de LEDs de RPM, indicador central de marcha, velocímetro em `km/h`, rotações, status do DRS (`ATIVO`, `DISP.`, `OFF`), barras instantâneas de acelerador, freio e esterçamento, além de setorização **S1 / S2 / S3** com cores de transmissão (roxo recorde, verde pessoal, amarelo).
- **Gráfico de Pilotagem em Canvas**: Plotagem contínua e sincronizada de **Velocidade (`km/h`)**, **Acelerador (`%`)** e **Freio (`%`)**.
- **Círculo de Força G & Delta**: Diagrama vetorial com rastro de aderência lateral/longitudinal e curva temporal de ganho/perda em relação à melhor volta.

---

### 3. Condições do Veículo
- **4 Cantos (Pneus & Freios)**: Cartões dedicados para Dianteiro Esquerdo (`DE`), Dianteiro Direito (`DD`), Traseiro Esquerdo (`TE`) e Traseiro Direito (`TD`) com:
  - Temperatura de superfície e núcleo interno (`°C`) com codificação térmica.
  - Pressão em `PSI`, desgaste (`%`), porcentagem de bolhas (*blisters*) e temperatura dos discos de freio.
- **ERS & Combustível**: Percentual de carga da bateria híbrida, modo de entrega, balanço de freio, combustível no tanque (`kg`), autonomia restante em voltas e mistura atual.
- **Unidade de Potência & Aerodinâmica**: Temperatura do motor, danos na asa dianteira (esquerda/direita) e assoalho, além de barras de desgaste de ICE, MGU-K, turbocompressor e caixa de câmbio.

---

### 4. Pista, Grid ao Vivo & Estratégia
- **Mapa Vetorial da Pista**: Traçado do circuito com marcador de largada/chegada e movimentação em tempo real de todos os carros coloridos por equipe.
- **Classificação Completa**: Tabela ao vivo com posição, faixa da equipe, sigla do piloto, melhor volta, paradas nos boxes, punições em segundos, composto e idade dos pneus, gap para o líder e intervalo para o carro à frente. Tocar em um piloto na tabela destaca sua posição no mapa.
- **Condições Climáticas & Bandeiras**: Temperatura de pista/ar, clima atual, previsão de curto prazo e status de bandeira (Verde, Amarela, VSC, Safety Car).

---

### 5. Sistema de Alertas em Tempo Real
- **Alertas Críticos de Telemetria**:
  - Superaquecimento de aviso e crítico do motor (`°C`).
  - Desgaste elevado e crítico de pneus (`%`).
  - Superaquecimento térmico de superfície e formação de bolhas.
  - Superaquecimento de discos de freio (`°C`).
  - Margem crítica de combustível restante (voltas).
- **Alertas Estratégicos de Corrida**:
  - **Janela Ideal de Pit Stop**: calcula o retorno estimado na classificação somando a perda nos boxes (`Pit Loss`) aos gaps do grid.
  - **Análise de Tráfego & Undercut**: identifica se o carro retornará em **ar limpo** ou preso atrás de um rival específico.
  - **Oportunidade de Pit Barato**: alerta imediato quando o *Virtual Safety Car* ou *Safety Car* está ativo com pneus desgastados.
- **Central de Alertas Personalizável**: Banner fixo no topo para alertas não reconhecidos, modal com histórico de alertas ativos, interruptor de **Simular Sobrecarga & VSC** (no modo simulador) e controles deslizantes para ajustar cada limite de disparo.

---

### 6. Módulo de Sugestões de Setup de Carro
- Analisa o **DNA da pista atual** (extensão, número de curvas, variação de elevação, percentual de retas, demanda aerodinâmica, agressividade de zebras e estresse de pneus) combinado com a **telemetria térmica e de velocidade** coletada em tempo real.
- Gera valores recomendados para o F1 25 nas áreas:
  - **Aerodinâmica**: Asa Dianteira e Asa Traseira.
  - **Transmissão**: Diferencial em Aceleração (*On-Throttle*) e Desaceleração (*Off-Throttle*).
  - **Geometria**: Cambagem e Convergência dianteira/traseira.
  - **Suspensão & Altura**: Rigidez de suspensão, barras estabilizadoras (*ARB*) e altura do solo.
  - **Freios & Pressão**: Pressão, balanço de freio e `PSI` dianteiro/traseiro ajustados à temperatura da pista.
- Exibe cartões de **Diagnóstico & Por Quê** explicando os dados observados que motivaram cada recomendação.

---

### 7. Histórico de Corridas na Nuvem & Comparação Lado a Lado
- **Autenticação com Conta Google**: Login via *Jetpack Credential Manager* (`GetSignInWithGoogleOption`) e *Firebase Authentication*, com isolamento de dados por usuário.
- **Sincronização no Cloud Firestore**: Gravação atômica em lote da corrida completa e de todas as voltas concluídas (`/users/{userId}/sessions/{sessionId}` e subcoleção `laps`), incluindo notas do piloto e curvas amostradas de velocidade.
- **Ferramenta de Comparação Lado a Lado**:
  - Seleção de **Corrida A** e **Corrida B** a partir do histórico salvo.
  - Tabela comparativa destacando **Melhor Volta**, **Ritmo Médio**, **Velocidade Máxima**, **Consistência (%)** e **Posição / Paradas nos Boxes**.
  - Gráfico comparativo de **Progressão de Voltas** e sobreposição da **Curva de Velocidade da Melhor Volta**.

---

## Como Conectar ao F1 25 (UDP)

1. Certifique-se de que o dispositivo Android e o PC/console executando o **EA Sports F1 25** estejam conectados à mesma rede Wi-Fi.
2. No **Apex Telemetry 25**, toque no indicador de status no canto superior direito para visualizar o **IP Local** do aparelho e conferir a porta UDP (padrão `20777`), alterando a fonte para **UDP 20777**.
3. No menu do jogo **F1 25**, acesse **Configurações > Telemetria**:
   - **Telemetria UDP**: `Ligado`
   - **Modo Broadcast**: `Desligado` (ou `Ligado` caso utilize múltiplos receptores)
   - **Endereço IP UDP**: informe o IP exibido no aplicativo
   - **Porta UDP**: `20777`
   - **Taxa de Envio UDP**: `20 Hz` ou `60 Hz`
   - **Formato UDP**: `2025`

---

## Arquitetura & Stack Tecnológica

- **Linguagem & UI**: Kotlin, Jetpack Compose, Material Design 3 (tema escuro *Pit Wall* com fontes locais *Titillium Web*, *JetBrains Mono* e *Inter*), `Canvas` nativo para gráficos de alta frequência e layout adaptativo (`NavigationBar` em telas compactas e `NavigationRail` em telas largas).
- **Arquitetura**: MVVM com `ViewModel`, `StateFlow`, `collectAsStateWithLifecycle`, *Navigation Compose* e camada dedicada de análise/telemetria (`F125PacketParser`, `UdpTelemetryReceiver`, `RaceSimulatorEngine`, `AlertEngine`, `SetupAdvisorEngine`).
- **Backend & Persistência**: Firebase Authentication (Google Sign-In via *Credential Manager*), Cloud Firestore (regras *zero-trust* isoladas por `request.auth.uid` e validadas em emulador local) e *Jetpack DataStore Preferences* para configurações locais de porta UDP e limites de alertas.
- **Testes**: Testes unitários JUnit para decodificação binária UDP do F1 25, motor de alertas e consultor de setup, testes Robolectric e suíte `node:test` com `@firebase/rules-unit-testing` para as regras de segurança do Firestore.
