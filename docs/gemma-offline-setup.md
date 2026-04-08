# Gemma Offline Setup

This document describes the new offline model files added for Gemma3 1B and how they fit into the Android app.

## Files

- `app/src/main/java/com/ml/Anshuman776/docqa/domain/llm/GemmaModelDownloader.kt`
- `app/src/main/java/com/ml/Anshuman776/docqa/domain/llm/GemmaOfflineEngine.kt`

## Purpose

The app is designed to run a local LLM on device instead of sending prompts to a cloud API.

- `GemmaModelDownloader` downloads the Gemma3 1B LiteRT task file from HuggingFace.
- `GemmaOfflineEngine` loads that task file with MediaPipe LLM Inference and generates responses locally.

## Model used

- Model: Gemma3 1B IT
- File name: `gemma3-1b-it-int4.task`
- Source: HuggingFace `litert-community/Gemma3-1B-IT`

## Download flow

1. The app checks that the device has enough free storage.
2. The app downloads the model into the app-specific downloads folder.
3. The model is renamed into its final path after a successful download.
4. The app loads the model locally through MediaPipe.

## Runtime flow

1. The user opens the local models screen.
2. The user downloads Gemma3 1B IT if needed.
3. The user loads the model.
4. Chat requests are sent to the local engine and answered on device.

## HuggingFace token

A HuggingFace token is only needed if the model is gated or requires license acceptance.

- Save the token in the existing credentials screen.
- The token is added as an Authorization header during download.

## Storage requirements

The downloader checks for at least 1.5 GB of available storage before starting the download.

## Notes

- The model file must exist locally before `GemmaOfflineEngine.loadModel(...)` is called.
- The offline engine keeps the model loaded until `release()` is called.
- The current app structure still uses the existing local-model screen and can be wired to these files directly.
