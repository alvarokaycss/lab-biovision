# Documentação Técnica — BioVision: Arquitetura, Decisões de Engenharia e Avaliação do Modelo

**Capacitação em Visão Computacional** · IFPE · INPaR · Financiamento FACEPE  
**Aluno:** Álvaro Kayc da Silva Santos  
**Artefatos:** `BioVision_Micro_Projeto_Final.ipynb` (Colab) e `modelo.tflite` (Android)

---

## 1. Por que Esta Abordagem e Não Outra?

Durante a concepção da solução foram analisados três caminhos técnicos principais para classificação visual:

| Abordagem | Viabilidade no Problema | Justificativa da Escolha / Descarte |
| :--- | :---: | :--- |
| **Visão Computacional Clássica**<br>*(Otsu, Canny, Contornos)* | Inadequada | Funciona bem para objetos rígidos e iluminação de laboratório controlada. Folhas reais apresentam nervuras naturais, sombras, curvaturas tridimensionais e manchas biológicas irregulares que quebram limiares estáticos de intensidade. |
| **Machine Learning Clássico**<br>*(HOG + SVM / Random Forest)* | Frágil | Descritores HOG capturam gradientes de bordas mas descartam textura fina e nuances de cor. Pequenas variações angulares ou deformações na folha degradam o vetor de características. |
| **Deep Learning com Transfer Learning**<br>*(MobileNetV2)* | **Ideal (Escolhida)** | Redes neurais convolucionais profundas aprendem representações hierárquicas automatizadas (arestas na base, texturas no meio e padrões patológicos no topo), mantendo alta invariância a rotações, escala e iluminação natural. |

---

## 2. Por que a Arquitetura MobileNetV2?

Alternativas clássicas como ResNet50 ou VGG16 violam as restrições de orçamento computacional de borda (*Edge AI*):
- **VGG16:** Ocupa mais de 500 MB e exige bilhões de FLOPs por inferência. Em um smartphone intermediário, isso drena bateria rapidamente e pode congelar a interface.
- **MobileNetV2:** Projetada especificamente para eficiência em dispositivos móveis. Seu pilar é a técnica de **Convoluções Separáveis em Profundidade (*Depthwise Separable Convolutions*)**:
  - Em vez de aplicar filtros tridimensionais completos em todos os canais ao mesmo tempo, divide o cálculo em duas fases: convolução espacial por canal (*depthwise*) e convolução pontual $1 \times 1$ para recombinação de canais (*pointwise*).
  - Reduz em aproximadamente 8 a 9 vezes o custo computacional e o consumo de memória, com acurácia competitiva.

---

## 3. Transfer Learning na Prática

Treinar uma CNN profunda do zero demandaria dezenas de milhares de fotos anotadas e dezenas de horas de GPU. O Transfer Learning reutiliza representações visuais ricas treinadas sobre a base ImageNet (1,4 milhão de imagens e 1.000 categorias):

```python
base = tf.keras.applications.MobileNetV2(
    input_shape=(224, 224, 3),
    include_top=False,
    weights="imagenet",
    pooling="avg"
)
base.trainable = False
```

- `weights="imagenet"`: Pré-calibração com padrões visuais universais do mundo real.
- `include_top=False`: Remoção da cabeça de classificação original de 1.000 classes genéricas.
- `pooling="avg"` (*Global Average Pooling*): Condensa os mapas de ativação 2D em um vetor denso unidimensional de 1.280 características por imagem.
- `base.trainable = False`: **Congelamento dos pesos.** Durante o treinamento, apenas a camada densa final é ajustada, preservando o extrator e acelerando a convergência.

---

## 4. Estrutura do Modelo e Normalização Embutida

```python
modelo = tf.keras.Sequential([
    tf.keras.layers.Rescaling(scale=1.0 / 127.5, offset=-1.0, input_shape=(224, 224, 3)),
    base,
    tf.keras.layers.Dense(units=3, activation="softmax")
])
```

1. **`Rescaling(scale=1.0 / 127.5, offset=-1.0)`:**
   - Câmeras e bibliotecas Android fornecem pixels inteiros em $[0, 255]$.
   - A MobileNetV2 opera com entradas no intervalo $[-1.0, 1.0]$.
   - Incorporar essa transformação diretamente no grafo do modelo simplifica a camada Android: o aplicativo injeta a matriz crua de pixels e o grafo cuida da normalização matemática.
2. **`base` (MobileNetV2 congelada):** Processa a entrada e gera o embedding de 1.280 dimensões.
3. **`Dense(units=3, activation="softmax")`:**
   - 3 neurônios representando as classes: `saudavel`, `oidio` e `queimada`.
   - A ativação **Softmax** converte saídas lineares em distribuição de probabilidade normalizada ($\sum P = 1.0$).

---

## 5. Compilação e Treinamento

- **Otimizador `adam`:** Ajuste adaptativo de taxa de aprendizado por parâmetro.
- **Função de perda `sparse_categorical_crossentropy`:** Mede a divergência entre a distribuição predita e a classe correta com rótulos inteiros ($0, 1, 2$).
- **5 Épocas:** Suficientes para saltar de 84,29% de acurácia na 1ª época para 99,91% no treino e 99,66% na validação.

---

## 6. Avaliação e Limiar de Segurança (70%)

1. **Validação Cega:** 1.165 imagens de treino e 291 imagens de validação independentes. Acurácia de **99,66%** com loss de **0,0245**.
2. **Limiar de Confiança:**
   $$\text{Se } \max(P) < 0,70 \implies \text{Resultado: INCONCLUSIVO}$$
   Evita que o usuário aplique tratamentos errados caso a imagem esteja desfocada, com folhas cortadas ou objetos estranhos na cena.

---

## 7. Otimização TFLite para Android

O modelo Keras foi serializado em formato FlatBuffer:

```python
conversor = tf.lite.TFLiteConverter.from_keras_model(modelo)
conversor.optimizations = [tf.lite.Optimize.DEFAULT]
modelo_tflite_bytes = conversor.convert()
```

- **Tamanho final:** ~2,41 MB (fácil de distribuir e embutir no APK).
- **Tempo de inferência:** < 150 ms em dispositivos intermediários.
- **Privacidade total:** Todo o processamento ocorre no próprio aparelho, sem necessidade de internet.
