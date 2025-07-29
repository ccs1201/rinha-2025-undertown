#!/bin/bash

# Define as variáveis de ambiente
export DATASOURCE_CLASS_NAME="org.postgresql.Driver"
export DATASOURCE_MAXIMUM_POOL_SIZE="10"
export DATASOURCE_MINIMUM_IDLE="10"
export DATASOURCE_PASSWORD="rinha"
export DATASOURCE_TIMEOUT="2000"
export DATASOURCE_URL="jdbc:postgresql://localhost:5432/rinha"
export DATASOURCE_USERNAME="rinha"
export PAYMENT_PROCESSOR_DEFAULT_URL="http://localhost:8001"
export PAYMENT_PROCESSOR_FALLBACK_URL="http://localhost:8002"
export PAYMENT_PROCESSOR_WORKERS="10"
export SERVER_IO_THREADS="2"
export SERVER_PORT="9999"
export SERVER_WORKER_THREADS="4"
export THREAD_POOL_SIZE="10"
export THREAD_QUEUE_SIZE="500"

# Limpa e empacota o projeto para garantir que o JAR está atualizado
# e que as dependências estão no diretório target/dependency/
mvn clean package dependency:copy-dependencies

# Define o classpath incluindo o JAR do seu projeto e todas as suas dependências
# O comando `mvn dependency:build-classpath` pode ser útil para construir isso dinamicamente,
# mas para um script, podemos inferir o caminho comum de dependências.
# Assumindo que o dependency:copy-dependencies colocará tudo em target/dependency
CP=$(echo target/dependency/*.jar | tr ' ' ':')
YOUR_APP_JAR="target/rinha-backend-2025-0.0.1-SNAPSHOT.jar"

java -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image \
     -cp "${YOUR_APP_JAR}:${CP}" \
     br.com.ccs.rinha.RinhaApp