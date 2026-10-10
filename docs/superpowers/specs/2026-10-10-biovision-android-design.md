# BioVision Android App — Especificação de Design

## 1. Visão Geral do Produto
O aplicativo móvel BioVision é um cliente nativo Android (desenvolvido em Kotlin com interface em XML tradicional) projetado para executar classificação de saúde de folhas on-device (Edge AI). Ele utiliza o modelo pré-treinado `modelo.tflite` com MobileNetV2 para fornecer triagem educativa em três categorias: saudável, oídio e queimada.

## 2. Princípios de Engenharia e Restrições
- **Minimalismo Estrito (Ponytail):** Solução concisa, sem bibliotecas pesadas de terceiros ou frameworks desnecessários.
- **Isolamento de Threads:** A inferência e o pré-processamento de imagens rodam em segundo plano via corrotinas (`Dispatchers.Default`), preservando a fluidez da UI a 60 FPS.
- **Zero Requisições Externas:** 100% offline. Nenhuma imagem ou dado de usuário sai do dispositivo.
- **Entrada e Contrato do Modelo:**
  - Resolução de entrada: $224 \times 224$ pixels, RGB.
  - Formato de entrada: `float32` no intervalo de 0 a 255 (a camada interna de `Rescaling` no modelo converte para $[-1.0, 1.0]$).
  - Saída: vetor `float32` de dimensão $[1, 3]$ representando probabilidades softmax nas classes `oidio`, `queimada` e `saudavel`.

## 3. Arquitetura de Componentes

### 3.1 `LeafClassifier.kt`
Responsável pelo ciclo de vida do motor de inferência:
- Carregamento de `modelo.tflite` e `labels.txt` a partir da pasta `app/src/main/assets/`.
- Cálculo de luminância média: $L = 0.299R + 0.587G + 0.114B$. Alerta caso $L < 60$ ("muito escuro") ou $L > 200$ ("muito claro").
- Redimensionamento e corte central da imagem para $224 \times 224$ mantendo proporção quadrada (1:1).
- Injeção de buffer no `Interpreter` do TensorFlow Lite e extração do vetor de probabilidades.
- Aplicação do limiar de segurança de 70%:
  - Confiança $\ge 0.70 \implies$ Retorna diagnóstico da classe vencedora.
  - Confiança $< 0.70 \implies$ Retorna "Inconclusivo" com recomendação de nova foto.

### 3.2 `MainActivity.kt`
Responsável pela interface e interação:
- Configurada com View Binding sobre `activity_main.xml`.
- Inicialização dos contratos modernos de atividade:
  - `ActivityResultContracts.TakePicturePreview()` para captura rápida de fotos via câmera.
  - `ActivityResultContracts.PickVisualMedia()` para seleção de imagens da galeria sem necessidade de permissões legadas de armazenamento.
- Envio assíncrono do Bitmap para o `LeafClassifier` e atualização visual dos componentes.

### 3.3 Layout e Identidade Visual (Material You / Estilo Google Orgânico)
- **Edge-to-Edge Nativo:** Fundo fluido contínuo que passa por trás da barra de status com ícones escuros translúcidos, eliminando a faixa dura de cor no topo.
- **Filosofia Borderless:** Zero linhas rígidas de contorno (`strokeWidth = 0dp`). A hierarquia é definida apenas por superfícies tonais limpas.
- **Paleta Verde & Branco:**
  - Fundo geral: `#F0F5F0` (Menta orvalho suave).
  - Superfície do conteúdo: `#FFFFFF` (Branco puro).
  - Botão Câmera (Primário): `#2D6A4F` (Verde folha madura sólido) com ícone e texto brancos.
  - Botão Galeria (Secundário/Tonal): `#D8F3DC` (Verde broto pastel) com ícone e texto verde escuro `#1B2E24`.
  - Texto e Títulos: `#1B2E24` (Verde floresta profundo, alto contraste legível sob luz solar).
- **Assimetria Botânica na Foto:**
  - Moldura da imagem em proporção 1:1 com cantos alternados orgânicos: canto superior esquerdo e inferior direito com curvatura ampla de `32dp`, cantos superior direito e inferior esquerdo com curvatura suave de `12dp`.
- **Botões em Pílula:** Formato arredondado suave (`24dp`) com ícones modernos sem traços externos.
- **Card de Orientação Caseira:** Superfície tonal com cantos arredondados contendo recomendação contextual para cada resultado.

## 4. Tratamento de Erros e Casos Extremos
- Imagem nula ou erro de decodificação: exibe mensagem amigável sem fechar o app.
- Luz precária: mantém a inferência, mas exibe aviso chamativo orientando nova captura com luz natural.
- Modelo não carregado: mensagem de erro tratada no `try/catch` de inicialização.

## 5. Próximos Passos
Elaboração do plano de implementação detalhado (Tarefas passo a passo com testes e validações).
