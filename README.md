# BioVision — Classificador de Saúde de Folhas

Classificador botânico on-device para Android que diagnostica a saúde de folhas em milissegundos sem envio de dados para servidores.

```bash
# Compilar o APK de depuração diretamente via terminal
./gradlew assembleDebug
```

---

## Funcionalidades

| Recurso | Detalhes |
|---|---|
| **Inferência 100% On-Device** | Modelo MobileNetV2 quantizado (~2,4 MB) executado localmente via TensorFlow Lite 2.17.0. Não consome franquia de dados móveis nem expõe imagens do usuário. |
| **Verificação de Luminância** | Avalia a iluminação da cena ($0.299R + 0.587G + 0.114B$) com amostragem rápida e alerta quando a foto está escura ($< 60$) ou com reflexo excessivo ($> 200$). |
| **Limiar de Confiança de 70%** | Retorna diagnóstico como **Inconclusivo** sempre que a probabilidade máxima da rede for inferior a 70%, prevenindo condutas equivocadas em folhas cortadas ou borradas. |
| **Orientações de Manejo** | Exibe recomendações práticas e personalizadas conforme o estado identificado: cuidados de rotina, controle de fungo (oídio) ou correção de irrigação/exposição (queimadura). |
| **Design Orgânico Google** | Interface fluida em tons botânicos (verde `#2D6A4F` e tonal `#D8F3DC`), botões em pílula sem contorno, moldura foliar assimétrica (`32dp` / `12dp`) e suporte a Edge-to-Edge nativo. |

---

## Estrutura do Repositório

```
BioVisionMP/
├── app/
│   ├── src/main/
│   │   ├── assets/
│   │   │   ├── modelo.tflite          # Binário quantizado compilado para execução móvel
│   │   │   └── labels.txt             # Rótulos das classes (oidio, queimada, saudavel)
│   │   ├── java/com/biovision/biovisionmp/
│   │   │   ├── MainActivity.kt        # Controle da interface, câmera, galeria e coroutines
│   │   │   └── LeafClassifier.kt      # Carregamento do TFLite, cálculo de luz e inferência
│   │   └── res/                       # Layouts borderless, estilos e paleta botânica
│   └── build.gradle.kts               # Configuração do módulo app e dependências Android
├── docs/
│   ├── DOCUMENTACAO_TECNICA.md        # Relatório de engenharia, métricas e decisões
│   └── superpowers/                   # Especificações de design e planos de implementação
├── model/
│   ├── BioVision_Micro_Projeto_Final.ipynb  # Notebook de treinamento com métricas e exportação
│   ├── 07_10_Modelo_Micro_Projeto.ipynb     # Notebook de exploração preliminar
│   ├── modelo.tflite                  # Cópia de referência do modelo treinado
│   └── labels.txt                     # Rótulos de referência
└── gradle/                            # Wrapper do Gradle 9.5 e catálogo de versões
```

---

## Requisitos e Especificações Técnicas

- **Linguagem:** Kotlin
- **SDK Mínimo:** Android 8.0 (API 26)
- **SDK Alvo / Compilação:** Android 15 (API 35/37)
- **Gradle:** 9.5.0 com Android Gradle Plugin 9.3.3
- **Motor de Inferência:** TensorFlow Lite Runtime 2.17.0
- **Entrada do Modelo:** $224 \times 224 \times 3$ (RGB)
- **Acurácia no Teste:** **99,66%** sobre partição independente do PlantVillage

---

## Como Executar

### Opção 1: Pelo Android Studio
1. Abra a pasta `C:\Users\kaycs\Dev\BioVisionMP` no Android Studio.
2. Aguarde a sincronização automática do Gradle.
3. Conecte um aparelho físico com depuração USB ou inicie um emulador (API 26+).
4. Clique no botão **Run** (`Shift + F10`).

### Opção 2: Pela Linha de Comando

```bash
# Limpar e gerar o APK de depuração
./gradlew assembleDebug

# Instalar no dispositivo conectado via adb
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Documentação Detalhada

Para consultar as justificativas teóricas, a comparação com abordagens clássicas de visão computacional e os detalhes do treinamento, veja:
- [`docs/DOCUMENTACAO_TECNICA.md`](docs/DOCUMENTACAO_TECNICA.md)
- [`model/BioVision_Micro_Projeto_Final.ipynb`](model/BioVision_Micro_Projeto_Final.ipynb)
