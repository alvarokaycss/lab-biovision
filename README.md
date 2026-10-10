# BioVision - Classificador de Saúde de Folhas

Aplicativo Android em Kotlin com modelo leve MobileNetV2 (Edge AI via TensorFlow Lite) para triagem visual de folhas em três classes: saudável, oídio e queimada.

## Estrutura do Repositório
- model/: Notebooks de treinamento, métricas e artefatos (modelo.tflite, labels.txt).
- pp/ ou projeto Android: Interface do usuário para captura e inferência on-device.
