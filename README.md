# Cabeceira 📚

MVP Android (Kotlin + Jetpack Compose) para organizar sua lista de livros e **calcular quanto tempo você vai levar para lê-los**.

## O que já faz

- **Busca por título, autor ou ISBN** em APIs públicas e gratuitas
  - [Google Books API](https://developers.google.com/books) (principal: bom acervo em PT-BR, traz número de páginas)
  - [BrasilAPI](https://brasilapi.com.br/docs#tag/ISBN) (ISBN de edições brasileiras, sem chave)
  - [Open Library](https://openlibrary.org/developers/api) (fallback)
- **Leitura de código de barras** (EAN-13 / ISBN) com o scanner do Google Play services — sem pedir permissão de câmera
- **Cadastro manual** quando a API não encontra o livro
- **Estimativa de tempo** já no momento de adicionar ("≈ 9 h · 18 dias lendo 30 min/dia")
- **Aprende seu ritmo**: ao registrar sessões ("parei na página X depois de Y minutos"), o app calcula suas páginas/hora
  - usa o ritmo *daquele livro* depois de 30 min registrados nele
  - senão, seu ritmo médio depois de 1 h registrada no total
  - senão, o ritmo padrão das configurações (30 pág/h)
- **Estante organizada** por status: Lendo · Quero ler · Lidos · Abandonados
- **Fila de leitura ordenável** com data prevista de início/fim de cada livro e quando você zera a fila
- **"Mais deste autor"** na tela do livro, para adicionar outras obras com um toque
- Tudo salvo localmente (Room/SQLite), sem login

## Estrutura

```
app/src/main/java/com/cabeceira/app/
├── data/
│   ├── local/        Room: Book, ReadingSession, DAOs, banco
│   ├── remote/       BookApi (Google Books + Open Library, via OkHttp)
│   ├── BookRepository.kt
│   └── SettingsStore.kt
├── domain/
│   └── ReadingEstimator.kt   ← o cálculo de tempo (testado em src/test)
└── ui/               Telas Compose: estante, busca/scanner, detalhes, configurações
```

## Como rodar

Abra a pasta no Android Studio e dê Run, ou pela linha de comando:

```bash
./gradlew installDebug
```

Testes da lógica de estimativa:

```bash
./gradlew testDebugUnitTest
```

O scanner de código de barras precisa de um aparelho (ou emulador) com Google Play services.

### Chave da Google Books (opcional)

Funciona sem chave, mas a cota é compartilhada por IP. Se começar a aparecer
"Limite de buscas atingido", crie uma chave gratuita no Google Cloud Console e adicione ao `local.properties`:

```
GOOGLE_BOOKS_API_KEY=sua_chave
```

## Próximos passos sugeridos

- Cronômetro de leitura dentro do app (em vez de digitar os minutos)
- Metas anuais e gráficos de ritmo ao longo do tempo
- Backup/exportação (JSON/CSV) e importação do Goodreads/Skoob
- Widget na tela inicial com o livro atual e o tempo restante
