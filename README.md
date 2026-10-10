# BioVision — Classificador de Saúde de Folhas

Classificador botânico on-device para Android que identifica a saúde de folhas em milissegundos sem enviar fotos para servidores.

```bash
# Compilar o aplicativo diretamente pela linha de comando
./gradlew assembleDebug
```

---

## Funcionalidades

| Recurso | Detalhes técnicos |
|---|---|
| **Inferência On-Device** | Modelo MobileNetV2 quantizado (~2,4 MB) executado localmente via TensorFlow Lite 2.17.0. Funciona offline sem consumir dados móveis. |
| **Verificação de Luminância** | Calcula a luminosidade média ($0.299R + 0.587G + 0.114B$) antes da inferência e emite alerta caso a foto esteja escura ($< 60$) ou com reflexo excessivo ($> 200$). |
| **Limiar de Confiança (70%)** | Retorna diagnóstico como **Inconclusivo** sempre que a probabilidade for inferior a 70%, prevenindo erros em fotos desfocadas ou cortadas. |
| **Orientações de Manejo** | Exibe orientações práticas de cuidado conforme o estado identificado: rotina habitual, isolamento para controle de fungo (oídio) ou ajuste de rega/sol (queima). |
| **Design Orgânico Material 3** | Interface borderless em tons de verde e branco, botões em formato de pílula, moldura foliar com cantos assimétricos (`32dp` e `12dp`) e suporte a Edge-to-Edge nativo. |

---

## Alinhamento com as Técnicas do Curso

A escolha da solução baseia-se diretamente nas diretrizes da capacitação:

- **Por que não Visão Computacional Clássica (Aulas 23-24/09):** Limiares estáticos (Otsu/Canny) falham com as nervuras irregulares, curvaturas e sombras naturais de folhas reais.
- **Por que não Features manuais com HOG + Random Forest (Aula 29/09):** Descritores HOG capturam apenas gradientes de arestas rígidas e descartam as texturas finas e variações de cor que diferenciam oídio de queimadura.
- **Por que MobileNetV2 com Transfer Learning (Aula 30/09):** Redes convolucionais profundas aprendem hierarquias visuais automáticas, garantindo tolerância a rotações, escala e iluminação natural.
- **Pipeline Móvel e Orçamento de Borda (Aula 07/10 - Aula 14):**
  - Pipeline de 5 etapas: `Câmera → Preparar → Inferir → Interpretar → Mostrar`.
  - Inferência executada fora da UI Thread via Kotlin Coroutines (`Dispatchers.Default`) para evitar congelamento de tela (*ANR*).
  - Quantização dinâmica (FP32 $\to$ INT8) para manter a latência abaixo de 150 ms e o arquivo em 2,4 MB.

---

## Estrutura do Repositório

```
BioVisionMP/
├── app/
│   ├── src/main/
│   │   ├── assets/
│   │   │   ├── modelo.tflite          # Modelo TFLite quantizado embutido no app
│   │   │   └── labels.txt             # Rótulos ordinais (oidio, queimada, saudavel)
│   │   ├── java/com/biovision/biovisionmp/
│   │   │   ├── MainActivity.kt        # Câmera, galeria, coroutines e atualização de tela
│   │   │   └── LeafClassifier.kt      # Carregamento do TFLite, cálculo de luz e predição
│   │   └── res/                       # Layouts borderless, estilos e paleta botânica
│   └── build.gradle.kts               # Configuração do app e dependências
├── docs/
│   ├── DOCUMENTACAO_TECNICA.md        # Relatório de engenharia, métricas e decisões
│   └── superpowers/                   # Planos e especificações originais
├── model/
│   ├── BioVision_Micro_Projeto_Final.ipynb  # Notebook com treinamento, tabela e ética
│   ├── 07_10_Modelo_Micro_Projeto.ipynb     # Notebook modelo da aula
│   ├── modelo.tflite                  # Binário do modelo treinado
│   └── labels.txt                     # Rótulos das classes
└── gradle/                            # Gradle 9.5 e catálogo de versões
```

---

## Avaliação do Modelo

- **Base de Dados:** PlantVillage (1.456 imagens balanceadas entre folhas saudáveis, com oídio e queimadas).
- **Partição:** 80% para treinamento (1.165 imagens) e 20% para teste independente (291 imagens).
- **Acurácia no Teste:** **99,66%** com perda de **0,0245**.
- **Validação contra Gabarito:** 12 casos avaliados com 100% de precisão na amostra do notebook.

---

## Considerações Éticas

Conforme a Ficha da Oficina (página 15 do material):
1. **Finalidade:** Ferramenta educativa de suporte para cultivo doméstico, sem pretensão de laudo agronômico oficial.
2. **Privacidade:** Registra apenas folhas e partes vegetais em primeiro plano, sem pessoas, rostos ou dados pessoais.
3. **Minimização:** Coordenadas GPS e metadados EXIF da câmera são descartados no carregamento da imagem.
4. **Proteção:** Processamento 100% local no dispositivo móvel, sem envio de fotos para servidores externos.
5. **Limitações e Viés:** Pode apresentar erros em folhas com pigmentação naturalmente manchada (variegação) ou sob luz deficiente. O aplicativo trata esses casos por meio do detector de luz e do limiar de segurança de 70%.

---

## Como Executar

### Via Android Studio
1. Abra a pasta `C:\Users\kaycs\Dev\BioVisionMP` no Android Studio.
2. Aguarde a sincronização do Gradle.
3. Conecte um aparelho Android (versão 8.0 / API 26 ou superior) ou inicie um emulador.
4. Execute o projeto (`Shift + F10`).

### Via Linha de Comando

```bash
# Compilar o APK
./gradlew assembleDebug

# Instalar no dispositivo conectado via USB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Documentos de Apoio

- Relatório técnico completo: [`docs/DOCUMENTACAO_TECNICA.md`](docs/DOCUMENTACAO_TECNICA.md)
- Notebook formatado para o Colab: [`model/BioVision_Micro_Projeto_Final.ipynb`](model/BioVision_Micro_Projeto_Final.ipynb)
