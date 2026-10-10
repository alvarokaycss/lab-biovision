# Documentação Técnica — BioVision: Arquitetura, Decisões de Engenharia e Avaliação do Modelo

**Capacitação em Visão Computacional** · IFPE · INPaR · Financiamento FACEPE  
**Aluno:** Álvaro Kayc da Silva Santos  
**Artefatos:** `BioVision_Micro_Projeto_Final.ipynb` (Colab) e `modelo.tflite` (Android)

---

## 1. Contexto e Escolha da Técnica

O treinamento do curso apresentou um roteiro claro para a escolha de técnicas em visão computacional:

| Se o problema é... | Técnica indicada | Aula | Aderência ao BioVision |
| :--- | :--- | :---: | :--- |
| Separar objeto e fundo | Limiar adaptativo e morfologia | 23 e 24/09 | Inadequada para folhas reais: variações de curvatura, nervuras e sombras naturais tornam limiares estáticos ineficientes. |
| Contar ou medir objetos | Contornos, área e circularidade | 24/09 | Útil para formas geométricas fixas (como o contador de moedas), mas não resolve distinção de patologias celulares. |
| Movimento corporal ou de mãos | Keypoints (MediaPipe) e ângulos | 25/09 | Focada em rastreamento articular humano; não se aplica a folhas estáticas. |
| Decidir entre classes simples | Features manuais (HOG) + Random Forest | 29/09 | Frágil: descritores HOG capturam gradientes de arestas rígidas, descartando textura fina e nuances cromáticas essenciais para identificar oídio ou queima. |
| **Objetos e padrões do dia a dia** | **Modelo pré-treinado (MobileNet)** | **30/09** | **Escolhida:** Redes convolucionais profundas extraem hierarquias visuais ricas e toleram rotações, sombras leves e variações de fundo. |
| Ações no tempo | Séries temporais de keypoints | 01/10 | Requer análise de sequência temporal contínua; a triagem de folhas requer apenas uma fotografia pontual. |

---

## 2. Orçamento Computacional e Arquitetura MobileNetV2

Redes densas como VGG16 (mais de 500 MB) ou ResNet50 demandam bilhões de operações de ponto flutuante por inferência, inviabilizando execução fluida em smartphones intermediários.

A MobileNetV2 soluciona essa restrição com **Convoluções Separáveis em Profundidade (*Depthwise Separable Convolutions*)**:
1. **Convolução Depthwise:** Aplica um filtro espacial $3 \times 3$ por canal de entrada de forma isolada.
2. **Convolução Pointwise:** Aplica filtros $1 \times 1$ para projetar e recombinar os canais gerados.

Essa divisão reduz o custo computacional e o consumo de memória em 8 a 9 vezes com impacto mínimo na acurácia.

---

## 3. Transfer Learning e Estrutura do Grafo

Treinar uma rede neural convolucional do zero exigiria dezenas de milhares de fotos anotadas. O Transfer Learning reutiliza padrões visuais universais extraídos da base ImageNet (1,4 milhão de imagens e 1.000 categorias):

```python
base = tf.keras.applications.MobileNetV2(
    input_shape=(224, 224, 3),
    include_top=False,
    weights="imagenet",
    pooling="avg"
)
base.trainable = False

modelo = tf.keras.Sequential([
    tf.keras.layers.Rescaling(scale=1.0 / 127.5, offset=-1.0, input_shape=(224, 224, 3)),
    base,
    tf.keras.layers.Dense(units=3, activation="softmax")
])
```

- **`Rescaling(scale=1.0 / 127.5, offset=-1.0)`:** Câmeras fornecem valores inteiros em $[0, 255]$. A camada embutida no grafo normaliza a matriz para $[-1.0, 1.0]$, eliminando conversões adicionais no código Kotlin do Android.
- **`pooling="avg"`:** O Global Average Pooling reduz os mapas de ativação 2D a um vetor compacto de 1.280 características.
- **`base.trainable = False`:** Pesos convolucionais congelados durante o treino. Apenas a camada densa de saída com 3 neurônios e ativação Softmax é ajustada.

---

## 4. O Pipeline no Smartphone Android (Aula 14)

A execução no aplicativo segue o pipeline de 5 etapas apresentado na Aula 14:

```
[1. Câmera / Galeria] ──> [2. Preparar] ──> [3. Inferir] ──> [4. Interpretar] ──> [5. Mostrar]
```

1. **Câmera / Galeria:** Captura pontual via contratos modernos do Android (`TakePicturePreview` e `PickVisualMedia`).
2. **Preparar:** Redimensionamento bicúbico para $224 \times 224$ pixels e análise de iluminação por amostragem rápida ($0.299R + 0.587G + 0.114B$).
3. **Inferir (Isolamento de Thread):** A inferência NUNCA roda na UI Thread. O app despacha o cálculo em background usando Kotlin Coroutines (`lifecycleScope.launch(Dispatchers.Default)`), evitando travamentos de interface (*ANR*).
4. **Interpretar:** Verificação da probabilidade máxima contra o limiar de 70% e seleção da orientação de cuidado.
5. **Mostrar:** Atualização fluida da tela com o diagnóstico, porcentagem, aviso de luz e card contextual de manejo.

---

## 5. Avaliação do Modelo

A validação foi conduzida em dois níveis complementares:

### Nível 1: Validação Quantitativa Cega (Conjunto de Teste)
- **Base:** PlantVillage balanceado (1.456 amostras no total: 1.165 para treino e 291 para teste independente).
- **Acurácia global:** **99,66%**
- **Loss:** **0,0245**

### Nível 2: Tabela Estruturada contra Gabarito (12 Casos de Teste)

| Caso | Gabarito | Previsto | Confiança | Status |
| :---: | :--- | :--- | :---: | :---: |
| 01 | saudável | saudável | 99,84% | Correto |
| 02 | oídio | oídio | 98,72% | Correto |
| 03 | queimada | queimada | 97,41% | Correto |
| 04 | saudável | saudável | 99,65% | Correto |
| 05 | oídio | oídio | 96,18% | Correto |
| 06 | queimada | queimada | 98,90% | Correto |
| 07 | saudável | saudável | 99,12% | Correto |
| 08 | oídio | oídio | 95,83% | Correto |
| 09 | queimada | queimada | 96,30% | Correto |
| 10 | saudável | saudável | 98,45% | Correto |
| 11 | oídio | oídio | 94,90% | Correto |
| 12 | queimada | queimada | 97,15% | Correto |

- **Taxa de acerto contra o gabarito:** 12/12 (100%) na amostra de avaliação.
- **Limiar de segurança:** Casos com confiança $< 70\%$ recebem o status **Inconclusivo**, solicitando foto com melhor enquadramento e luz.

---

## 6. Ficha Ética (Baseada na Página 15 do Obsidian)

1. **Finalidade:** Servir como ferramenta educativa e de triagem visual inicial para cultivadores caseiros. A interface informa de forma transparente que se trata de suporte informativo, e não de laudo agronômico oficial.
2. **Consentimento e Privacidade:** O aplicativo fotografa exclusivamente folhas em primeiro plano. Nenhuma pessoa é filmada, e rostos ou dados pessoais não entram no pipeline.
3. **Minimização de Dados:** Apenas a foto estática é lida na memória. Coordenadas de GPS e metadados EXIF da câmera são descartados imediatamente no pré-processamento.
4. **Proteção e Armazenamento:** A inferência é 100% on-device (Edge AI). Nenhuma imagem é transmitida para a internet ou salva em servidores externos.
5. **Avaliação por Grupo e Falhas Conhecidas:** O modelo apresenta maior taxa de erro em plantas com manchas claras naturais nas folhas (variegação), podendo confundir o padrão natural com fungos. Também falha sob luz muito fraca ($< 60$) ou reflexos diretos ($> 200$), mitigados pelo detector de luminância.
6. **Responsabilidade e Mitigação de Viés:**
   - **Risco:** Fotos da base PlantVillage foram capturadas sobre fundo de papel branco em bancada de laboratório.
   - **Mitigação:** Treinamento com data augmentation, inclusão de validação externa e aplicação do limiar de 70% para evitar condutas erradas no manejo da planta.

---

## 7. Otimização TFLite para Android

O modelo Keras foi exportado para FlatBuffer via TFLite Converter com quantização dinâmica de pesos (FP32 para INT8):
- **Tamanho do arquivo binário:** **2,41 MB**
- **Tempo de inferência:** Inferior a **150 ms** em processador intermediário.
- **Runtime:** `org.tensorflow:tensorflow-lite:2.17.0` (suporte à versão 12 da operação `FULLY_CONNECTED`).
