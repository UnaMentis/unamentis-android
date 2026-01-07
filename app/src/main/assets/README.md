# UnaMentis Assets

## Silero VAD Model

The Silero VAD TensorFlow Lite model (`silero_vad.tflite`) should be placed in this directory.

### Download Instructions

1. Download the Silero VAD ONNX model from: https://github.com/snakers4/silero-vad
2. Convert to TensorFlow Lite format using the official converter
3. Place the resulting `silero_vad.tflite` file in this directory

### Model Specifications

- **Input**: Float32 tensor of shape [1, 512] (512 samples at 16kHz = 32ms)
- **Output**: Float32 scalar (speech probability, 0.0 - 1.0)
- **Sample Rate**: 16000 Hz
- **Frame Size**: 512 samples (32ms)

## Other Assets

Additional assets (curriculum visual assets, etc.) will be stored here or in subdirectories.
