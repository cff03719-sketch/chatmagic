CHAT MAGIC (Fabric 1.21.1)

COMO USAR
1. Instale Java 21 e o Fabric Loader + Fabric API na sua instancia 1.21.1.
2. Compile: dentro desta pasta rode "gradle build" (ou copie src/, build.gradle,
   gradle.properties para o template oficial em https://fabricmc.net/develop/template/
   e rode ./gradlew build). O .jar fica em build/libs/.
3. Coloque o .jar na pasta mods.
4. Crie o arquivo config/chatmagic.txt com sua chave da API da Anthropic
   (ou defina a variavel de ambiente ANTHROPIC_API_KEY).
5. No jogo, escreva no chat com ! na frente. Exemplo: !faz chover diamantes
   Precisa de trapacas/permissao nao: o mod executa os comandos com nivel 4 em nome do jogador.

CUIDADO: em servidor multiplayer, qualquer jogador poderia usar. Use so em mundo seu.
