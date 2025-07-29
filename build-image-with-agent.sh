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

mvn clean package dependency:copy-dependencies

CP=$(echo target/dependency/*.jar | tr ' ' ':')
YOUR_APP_JAR="target/rinha-backend-2025-0.0.1-SNAPSHOT.jar"

java -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image \
     -cp "${YOUR_APP_JAR}:${CP}" \
     br.com.ccs.rinha.RinhaApp

k6 run rinha-test/rinha.js
k6 run rinha-test/rinha.js
k6 run rinha-test/rinha.js

mvn clean package -Pnative

docker-compose -f docker-compose-payment-processor.yml down --remove-orphans
docker-compose -f docker-compose.yml down --remove-orphans
docker container prune -f

docker buildx build -f Dockerfile -t csouzadocker/rinha-backend-2025-puro:0.0.1 .
