# Space Extractor

Aplicativo Android em Kotlin para extrair arquivos compactados com foco em **uso mínimo de armazenamento**. O projeto foi pensado para aparelhos como o Samsung Galaxy S23, com Android moderno, e mantém a interface simples, espacial e objetiva.

## O que o aplicativo faz

O Space Extractor permite selecionar um arquivo `.zip`, `.zip64` ou `.rar`, escolher uma pasta de destino, analisar o conteúdo e iniciar a extração em segundo plano. Durante o processo, exibe progresso de 0% a 100%, nome do arquivo atual e notificações compatíveis com a tela apagada.

A interface usa o nome **Space Extractor**, versão **0.1**, e possui um ícone espacial baseado na arte fornecida para o projeto.

## Recursos implementados

- Seleção de arquivo e pasta pelo Storage Access Framework oficial do Android.
- Análise de entradas ZIP, tamanhos compactados/descompactados e espaço livre aproximado.
- Extração em `Foreground Service`, continuando com a tela apagada.
- Validação de tamanho e CRC pelo fechamento validado das entradas ZIP.
- Progresso percentual real enviado do serviço para a Activity.
- Persistência de sessão em `extraction_session.json`.
- Proteção contra path traversal em nomes de arquivos ZIP.
- Interface fixa, sem rolagem, com fundo espacial estático e cartões neon.
- Nenhum uso de Shizuku ou `MANAGE_EXTERNAL_STORAGE`.

## Limitações intencionais

A interface SAF não é tratada como truncável: o Android não garante que um URI de documento aceite `truncate/ftruncate`. Por isso, o aplicativo preserva o arquivo original quando ele é selecionado pelo sistema. O método destrutivo `ZipEngine.extractDestructive()` existe somente para arquivos locais realmente graváveis e só trunca depois de gravação, sincronização, validação e registro do ponto seguro.

RAR é reconhecido no seletor, mas a extração RAR ainda não está implementada nesta versão. Isso evita alegar suporte seguro a RAR5, arquivos solid ou casos que exigem dependências anteriores.

O acesso a `Android/data` depende do que o seletor oficial do aparelho permitir. Se o Android bloquear uma pasta, o aplicativo não tenta contornar a restrição com permissões falsas.

## Estrutura principal

| Arquivo | Responsabilidade |
|---|---|
| `MainActivity.kt` | Interface fixa, seleção SAF, análise e progresso de 0–100%. |
| `SpaceBackgroundView.kt` | Fundo espacial estático desenhado localmente. |
| `ZipEngine.kt` | Análise ZIP, extração SAF e mecanismo destrutivo local seguro. |
| `ExtractionService.kt` | Extração em segundo plano e notificações de progresso. |
| `AndroidManifest.xml` | Activity, permissões mínimas e Foreground Service. |
| `app/src/main/res/drawable/icon_space_extractor.png` | Ícone oficial do aplicativo. |

## Compilar no Android Studio

Requisitos recomendados: Android Studio atual, JDK 21 e Android SDK Platform 35.

Para gerar debug:

```bash
./gradlew :app:assembleDebug
```

Para gerar release:

1. Copie `keystore.properties.example` para `keystore.properties`.
2. Coloque a sua keystore privada no caminho indicado.
3. Preencha as senhas localmente.
4. Execute:

```bash
./gradlew :app:assembleRelease
```

A keystore e `keystore.properties` estão no `.gitignore` e não devem ser publicados. Para atualizações futuras instaláveis sobre a versão 0.1, mantenha uma cópia segura da mesma chave de assinatura.

## Identidade do pacote

- Aplicativo: `Space Extractor`
- Application ID: `com.manus.spaceextractor`
- Versão atual: `0.1`
- `minSdk`: 26
- `targetSdk`: 35
- Arquitetura principal: Kotlin/JVM; compatível com ARM64-v8a via runtime Android
