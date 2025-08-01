#!/bin/bash

# Parar e remover containers existentes
docker-compose -f docker-compose-payment-processor.yml down --remove-orphans
docker-compose -f docker-compose.yml down --remove-orphans
docker container prune -f

# Build da aplicação
./mvnw clean package -DskipTests

# Build da imagem Docker
docker buildx build -f Dockerfile -t csouzadocker/rinha-backend-2025-puro:0.0.1 .

## Subir Payment Processors
#docker-compose -f docker-compose-payment-processor.yml up -d
#
## Aguardar Payment Processors
##sleep 3
#
## Subir aplicação
#docker-compose -f docker-compose.yml up -d

#echo "Aplicação rodando na porta 9999"