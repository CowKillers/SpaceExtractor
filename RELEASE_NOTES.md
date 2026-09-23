# Space Extractor 0.2

## Novidades

- Extração automática para a pasta pública `Download/SpaceExtractor`.
- Remoção da seleção manual de diretório e do navegador interno de arquivos.
- Botão de cancelamento com resposta imediata na interface.
- Cancelamento cooperativo do serviço sem continuar atualizando o progresso após o pedido.
- Verificação compacta do arquivo antes da extração, com mensagens de pronto ou arquivo com problemas.
- Progresso global por bytes, barra neon RGB animada e interface espacial preservada.
- Compatibilidade com Android 10+ usando `MediaStore.Downloads` e `RELATIVE_PATH`.
- Fallback para `Download/SpaceExtractor` em Android 9 ou anterior.

## Instalação

Instale `SpaceExtractor-0.2-debug.apk`. Esta é uma build debug assinada para testes e uso direto no dispositivo.

## Destino dos arquivos

Os arquivos extraídos ficam em:

```text
Download/SpaceExtractor
```

A pasta é pública e pode ser acessada por Files, EX File Manager, RS File Manager e outros gerenciadores compatíveis.
